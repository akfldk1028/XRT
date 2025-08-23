"""
Base Task Manager for Claude CLI Agents
"""
import asyncio
import logging
import traceback
from typing import AsyncIterable, Union, Any, Dict, Optional, Protocol, TYPE_CHECKING
import sys
from pathlib import Path

try:
    from typing import TypeAlias
except ImportError:
    from typing_extensions import TypeAlias

# Add parent directories to path
sys.path.append(str(Path(__file__).parent.parent.parent))

from shared import utils as utils
from shared.abc_task_manager import InMemoryTaskManager
from shared.a2a_conversation_logger import log_a2a_request, log_a2a_response, log_a2a_error
from shared.custom_types import (
    Artifact,
    InternalError,
    InvalidParamsError,
    JSONRPCResponse,
    Message,
    PushNotificationConfig,
    SendTaskRequest,
    SendTaskResponse,
    SendTaskStreamingRequest,
    SendTaskStreamingResponse,
    Task,
    TaskArtifactUpdateEvent,
    TaskIdParams,
    TaskSendParams,
    TaskState,
    TaskStatus,
    TaskStatusUpdateEvent,
    TextPart,
    A2AMessageSendRequest,
    A2AMessageSendResponse,
    A2AMessage,
    A2ATextPart,
    A2APart,
    A2AMessageResponse,
    A2ATaskResponse,
    MessageSendParams,
    ContentTypeNotSupportedError,
)
from shared.push_notification_auth import PushNotificationSenderAuth
from shared.a2a_conversation_logger import (
    get_conversation_logger,
    log_a2a_request,
    log_a2a_response,
    log_a2a_error
)

logger = logging.getLogger(__name__)
a2a_logger = get_conversation_logger()

# Type aliases for better maintainability
AgentResponse: TypeAlias = Dict[str, Any]

def get_projects_directory() -> Path:
    """Get the projects directory path"""
    current_file = Path(__file__)
    projects_dir = current_file.parent.parent.parent / "projects"
    projects_dir.mkdir(exist_ok=True)
    return projects_dir
TaskId: TypeAlias = str
SessionId: TypeAlias = str
Query: TypeAlias = str

class CLIAgent(Protocol):
    """Protocol defining the interface for CLI agents"""
    SUPPORTED_CONTENT_TYPES: list[str]
    
    async def invoke_async(self, query: Query, session_id: SessionId) -> AgentResponse:
        """Invoke the agent asynchronously"""
        ...
    
    async def stream(self, query: Query, session_id: SessionId) -> AsyncIterable[AgentResponse]:
        """Stream responses from the agent"""
        ...


class CLIAgentTaskManager(InMemoryTaskManager):
    """Task Manager for Claude CLI based agents"""
    
    def __init__(self, agent: CLIAgent, notification_sender_auth: PushNotificationSenderAuth) -> None:
        super().__init__()
        self.agent = agent
        self.notification_sender_auth = notification_sender_auth

    async def on_a2a_message_send(self, request: A2AMessageSendRequest) -> JSONRPCResponse:
        """Handle A2A message/send requests"""
        try:
            # Log the incoming request
            log_a2a_request("client", self.agent.__class__.__name__, request)
            
            # Extract message params
            params: MessageSendParams = request.params
            message = params.message
            
            # Convert A2A message parts to query string
            query_parts = []
            for part in message.parts:
                if hasattr(part, 'text'):
                    query_parts.append(part.text)
                elif hasattr(part, 'data'):
                    query_parts.append(str(part.data))
            
            query = ' '.join(query_parts)
            session_id = message.contextId or "default_session"
            
            # Invoke the Claude CLI agent
            start_time = asyncio.get_event_loop().time()
            result = await self.agent.invoke_async(query, session_id)
            duration_ms = (asyncio.get_event_loop().time() - start_time) * 1000
            
            # Create response based on result
            if result.get("is_task_complete", False):
                # Return as completed task
                task_response = A2ATaskResponse(
                    id=f"task_{message.messageId}",
                    contextId=message.contextId or f"ctx_{session_id}",
                    status=TaskStatus(state=TaskState.COMPLETED),
                    artifacts=[
                        Artifact(
                            artifactId=f"artifact_{message.messageId}",
                            parts=[
                                A2ATextPart(
                                    kind="text",
                                    text=result.get("content", "")
                                )
                            ]
                        )
                    ],
                    kind="task"
                )
                response = A2AMessageSendResponse(
                    id=request.id,
                    result=task_response
                )
            else:
                # Return as message
                message_response = A2AMessageResponse(
                    role="agent",
                    parts=[
                        A2ATextPart(
                            kind="text",
                            text=result.get("content", "")
                        )
                    ],
                    messageId=f"resp_{message.messageId}",
                    contextId=message.contextId,
                    kind="message"
                )
                response = A2AMessageSendResponse(
                    id=request.id,
                    result=message_response
                )
            
            # Log the response
            log_a2a_response("client", self.agent.__class__.__name__, response, duration_ms)
            
            return response
            
        except Exception as e:
            logger.error(f"Error in A2A message handling: {e}")
            log_a2a_error("on_a2a_message_send", e, self.agent.__class__.__name__)
            
            error_response = A2AMessageSendResponse(
                id=request.id,
                error=InternalError(data={"error": str(e)})
            )
            return error_response

    async def _run_streaming_agent(self, request: SendTaskStreamingRequest) -> None:
        task_send_params: TaskSendParams = request.params
        query = self._get_user_query(task_send_params)

        try:
            async for item in self.agent.stream(query, task_send_params.sessionId):
                is_task_complete = item["is_task_complete"]
                require_user_input = item["require_user_input"]
                artifact = None
                message = None
                parts = [A2ATextPart(kind="text", text=item["content"])]
                end_stream = False

                if not is_task_complete and not require_user_input:
                    task_state = TaskState.WORKING
                    message = A2AMessage(role="agent", parts=parts)
                elif require_user_input:
                    task_state = TaskState.INPUT_REQUIRED
                    message = A2AMessage(role="agent", parts=parts)
                    end_stream = True
                else:
                    task_state = TaskState.COMPLETED
                    artifact = Artifact(parts=parts, index=0, append=False)
                    end_stream = True

                task_status = TaskStatus(state=task_state, message=message)
                latest_task = await self.update_store(
                    task_send_params.id,
                    task_status,
                    None if artifact is None else [artifact],
                )
                await self.send_task_notification(latest_task)

                if artifact:
                    task_artifact_update_event = TaskArtifactUpdateEvent(
                        id=task_send_params.id, artifact=artifact
                    )
                    await self.enqueue_events_for_sse(
                        task_send_params.id, task_artifact_update_event
                    )

                task_update_event = TaskStatusUpdateEvent(
                    id=task_send_params.id, status=task_status, final=end_stream
                )
                await self.enqueue_events_for_sse(
                    task_send_params.id, task_update_event
                )

        except Exception as e:
            logger.error(f"An error occurred while streaming the response: {e}")
            await self.enqueue_events_for_sse(
                task_send_params.id,
                InternalError(
                    message=f"An error occurred while streaming the response: {e}"
                ),
            )

    def _validate_request(
        self, request: Union[SendTaskRequest, SendTaskStreamingRequest]
    ) -> Optional[JSONRPCResponse]:
        task_send_params: TaskSendParams = request.params
        if not utils.are_modalities_compatible(
            task_send_params.acceptedOutputModes, self.agent.SUPPORTED_CONTENT_TYPES
        ):
            logger.warning(
                "Unsupported output mode. Received %s, Support %s",
                task_send_params.acceptedOutputModes,
                self.agent.SUPPORTED_CONTENT_TYPES,
            )
            return utils.new_incompatible_types_error(request.id)

        if (
            task_send_params.pushNotification
            and not task_send_params.pushNotification.url
        ):
            logger.warning("Push notification URL is missing")
            return JSONRPCResponse(
                id=request.id,
                error=InvalidParamsError(message="Push notification URL is missing"),
            )

        return None

    async def on_send_task(self, request: SendTaskRequest) -> SendTaskResponse:
        """Handles the 'send task' request."""
        validation_error = self._validate_request(request)
        if validation_error:
            return SendTaskResponse(id=request.id, error=validation_error.error)

        if request.params.pushNotification:
            if not await self.set_push_notification_info(
                request.params.id, request.params.pushNotification
            ):
                return SendTaskResponse(
                    id=request.id,
                    error=InvalidParamsError(
                        message="Push notification URL is invalid"
                    ),
                )

        await self.upsert_task(request.params)
        task = await self.update_store(
            request.params.id, TaskStatus(state=TaskState.WORKING), None
        )
        await self.send_task_notification(task)

        task_send_params: TaskSendParams = request.params
        query = self._get_user_query(task_send_params)
        try:
            agent_response = await self.agent.invoke_async(query, task_send_params.sessionId)
        except Exception as e:
            logger.error(f"Error invoking agent: {e}")
            raise ValueError(f"Error invoking agent: {e}")
        return await self._process_agent_response(request, agent_response)

    async def on_a2a_message_send(self, request: A2AMessageSendRequest) -> A2AMessageSendResponse:
        """Handles the official A2A 'message/send' request using Python A2A style."""
        import uuid
        
        print(f"[A2A] message/send received: message_id={request.params.message.messageId}")
        
        # Extract message text from A2A format
        message_text = ""
        for part in request.params.message.parts:
            if part.kind == "text":
                message_text += part.text
        
        print(f"[A2A] Extracted text: {message_text[:100]}...")
        
        # Generate task and session IDs
        task_id = request.params.message.taskId or str(uuid.uuid4())
        session_id = request.params.message.contextId or str(uuid.uuid4())
        
        try:
            print(f"[A2A] Calling agent.invoke_async with: {message_text[:50]}...")
            
            # Get agent response directly (simplified approach)
            agent_response = await self.agent.invoke_async(message_text, session_id)
            
            print(f"[A2A] Agent response received: {agent_response.get('is_task_complete', False)}")
            
            # Create simple A2A response based on Python A2A patterns
            if agent_response.get("is_task_complete", False):
                # Create completed task response
                content = agent_response.get("content", "Task completed")
                
                # Create artifact with the response
                artifact = Artifact(
                    artifactId=str(uuid.uuid4()),
                    parts=[A2ATextPart(kind="text", text=content)]
                )
                
                # Create task response
                task_response = A2ATaskResponse(
                    id=task_id,
                    contextId=session_id,
                    status=TaskStatus(state=TaskState.COMPLETED),
                    artifacts=[artifact],
                    kind="task"
                )
                
                print(f"[A2A] Returning completed task: {task_id}")
                return A2AMessageSendResponse(id=request.id, result=task_response)
            else:
                # Task needs more input or is in progress
                content = agent_response.get("content", "Task in progress")
                
                task_response = A2ATaskResponse(
                    id=task_id,
                    contextId=session_id,
                    status=TaskStatus(
                        state=TaskState.INPUT_REQUIRED,
                        message=Message(
                            role="agent",
                            parts=[TextPart(type="text", text=content)]
                        )
                    ),
                    kind="task"
                )
                
                print(f"[A2A] Returning input-required task: {task_id}")
                return A2AMessageSendResponse(id=request.id, result=task_response)
                
        except Exception as e:
            logger.error(f"Error in A2A message/send: {e}")
            print(f"[A2A] Error: {str(e)}")
            
            # Return error response
            error_task = A2ATaskResponse(
                id=task_id,
                contextId=session_id,
                status=TaskStatus(
                    state=TaskState.FAILED,
                    message=Message(
                        role="agent", 
                        parts=[TextPart(type="text", text=f"Error: {str(e)}")]
                    )
                ),
                kind="task"
            )
            
            return A2AMessageSendResponse(id=request.id, result=error_task)
            error_msg = str(e).encode('ascii', 'replace').decode('ascii')
            return A2AMessageSendResponse(
                id=request.id,
                error=InternalError(message=f"Error processing A2A message: {error_msg}")
            )

    async def on_send_task_subscribe(
        self, request: SendTaskStreamingRequest
    ) -> Union[AsyncIterable[SendTaskStreamingResponse], JSONRPCResponse]:
        try:
            error = self._validate_request(request)
            if error:
                return error

            await self.upsert_task(request.params)

            if request.params.pushNotification:
                if not await self.set_push_notification_info(
                    request.params.id, request.params.pushNotification
                ):
                    return JSONRPCResponse(
                        id=request.id,
                        error=InvalidParamsError(
                            message="Push notification URL is invalid"
                        ),
                    )

            task_send_params: TaskSendParams = request.params
            sse_event_queue = await self.setup_sse_consumer(task_send_params.id, False)

            asyncio.create_task(self._run_streaming_agent(request))

            return self.dequeue_events_for_sse(
                request.id, task_send_params.id, sse_event_queue
            )
        except Exception as e:
            logger.error(f"Error in SSE stream: {e}")
            print(traceback.format_exc())
            return JSONRPCResponse(
                id=request.id,
                error=InternalError(
                    message="An error occurred while streaming the response"
                ),
            )

    async def _process_agent_response(
        self, request: SendTaskRequest, agent_response: AgentResponse
    ) -> SendTaskResponse:
        """Processes the agent's response and updates the task store."""
        task_send_params: TaskSendParams = request.params
        task_id = task_send_params.id
        history_length = task_send_params.historyLength
        task_status = None

        parts = [A2ATextPart(kind="text", text=agent_response["content"])]
        artifact = None
        if agent_response["require_user_input"]:
            task_status = TaskStatus(
                state=TaskState.INPUT_REQUIRED,
                message=A2AMessage(role="agent", parts=parts),
            )
        else:
            task_status = TaskStatus(state=TaskState.COMPLETED)
            artifact = Artifact(parts=parts)
        task = await self.update_store(
            task_id, task_status, None if artifact is None else [artifact]
        )
        task_result = self.append_task_history(task, history_length)
        await self.send_task_notification(task)
        return SendTaskResponse(id=request.id, result=task_result)

    def _get_user_query(self, task_send_params: TaskSendParams) -> Query:
        part = task_send_params.message.parts[0]
        if not isinstance(part, TextPart):
            raise ValueError("Only text parts are supported")
        return part.text

    async def send_task_notification(self, task: Task) -> None:
        if not await self.has_push_notification_info(task.id):
            logger.info(f"No push notification info found for task {task.id}")
            return
        push_info = await self.get_push_notification_info(task.id)

        logger.info(f"Notifying for task {task.id} => {task.status.state}")
        await self.notification_sender_auth.send_push_notification(
            push_info.url, data=task.model_dump(exclude_none=True)
        )

    async def on_resubscribe_to_task(
        self, request: Any
    ) -> Union[AsyncIterable[SendTaskStreamingResponse], JSONRPCResponse]:
        task_id_params: TaskIdParams = request.params
        try:
            sse_event_queue = await self.setup_sse_consumer(task_id_params.id, True)
            return self.dequeue_events_for_sse(
                request.id, task_id_params.id, sse_event_queue
            )
        except Exception as e:
            logger.error(f"Error while reconnecting to SSE stream: {e}")
            return JSONRPCResponse(
                id=request.id,
                error=InternalError(
                    message=f"An error occurred while reconnecting to stream: {e}"
                ),
            )

    async def set_push_notification_info(
        self, task_id: TaskId, push_notification_config: PushNotificationConfig
    ) -> bool:
        # Verify the ownership of notification URL by issuing a challenge request.
        is_verified = await self.notification_sender_auth.verify_push_notification_url(
            push_notification_config.url
        )
        if not is_verified:
            return False

        await super().set_push_notification_info(task_id, push_notification_config)
        return True
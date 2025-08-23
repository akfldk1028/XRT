import json
import logging
import datetime
import os
from typing import Any, AsyncIterable, Union
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from sse_starlette.sse import EventSourceResponse

from .abc_task_manager import TaskManager
from .custom_types import (
    A2ARequest,
    AgentCard,
    CancelTaskRequest,
    GetTaskPushNotificationRequest,
    GetTaskRequest,
    InternalError,
    InvalidRequestError,
    JSONParseError,
    JSONRPCResponse,
    SendTaskRequest,
    SendTaskStreamingRequest,
    SetTaskPushNotificationRequest,
    TaskResubscriptionRequest,
    A2AMessageSendRequest,
    A2AMessageSendResponse,
    A2AMessage,
    A2APart,
)

logger = logging.getLogger(__name__)


def log_a2a_communication(agent_port: int, message_data: dict, response_data: dict = None) -> None:
    """Log all A2A communications to a central log file"""
    try:
        # Determine agent type by port
        agent_map = {
            8030: "perception",
            8031: "vision", 
            8032: "ux_tts",
            8033: "logger",
            8010: "frontend",
            8021: "backend", 
            8012: "unity"
        }
        
        agent_name = agent_map.get(agent_port, f"unknown_port_{agent_port}")
        
        # Create logs directory if it doesn't exist
        logs_dir = Path(__file__).parent.parent / "logs" / "a2a_communications"
        logs_dir.mkdir(parents=True, exist_ok=True)
        
        # Create timestamped log file
        timestamp = datetime.datetime.now()
        log_filename = f"a2a_all_agents_{timestamp.strftime('%Y%m%d')}.log"
        log_file = logs_dir / log_filename
        
        # Extract message content
        message_text = ""
        if message_data and 'params' in message_data:
            message_content = message_data['params'].get('message', {})
            parts = message_content.get('parts', [])
            if parts and len(parts) > 0:
                message_text = parts[0].get('text', '')
        
        # Create log entry
        log_entry = {
            "timestamp": timestamp.isoformat(),
            "datetime": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
            "agent_name": agent_name,
            "agent_port": agent_port,
            "message_type": "A2A_Communication",
            "request_id": message_data.get('id', 'unknown'),
            "method": message_data.get('method', 'unknown'),
            "message_preview": message_text[:200] + "..." if len(message_text) > 200 else message_text,
            "message_length": len(message_text),
            "has_response": response_data is not None,
            "raw_message": message_data,
            "response": response_data
        }
        
        # Write to log file
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(json.dumps(log_entry, indent=2, ensure_ascii=False) + "\n")
            f.write("-" * 100 + "\n")
            
        print(f"[A2A Logging] Communication logged for {agent_name}:{agent_port}")
        
    except Exception as e:
        print(f"[A2A Logging] Failed to log communication: {str(e)}")


class A2AServer:
    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 5000,
        endpoint: str = "/",
        agent_card: AgentCard = None,
        task_manager: TaskManager = None,
    ):
        self.host = host
        self.port = port
        self.endpoint = endpoint
        self.task_manager = task_manager
        self.agent_card = agent_card

        # Erstelle eine FastAPI-App für automatische Dokumentation (/docs, /redoc, etc.)
        self.app = FastAPI(
            title="A2A Server", description="A2A Protocol JSON-RPC API", version="1.0.0"
        )
        # JSON-RPC-Endpunkt (POST) - automatische Response Modell Generierung deaktiviert
        self.app.add_api_route(
            self.endpoint, self._process_request, methods=["POST"], response_model=None
        )
        # AgentCard-Endpunkt unter .well-known
        self.app.add_api_route(
            "/.well-known/agent.json",
            self._get_agent_card,
            methods=["GET"],
            response_model=None,
        )
        # A2A Inspector compatibility - also serve at agent-card.json
        self.app.add_api_route(
            "/.well-known/agent-card.json",
            self._get_agent_card,
            methods=["GET"],
            response_model=None,
        )

    def start(self):
        if self.agent_card is None:
            raise ValueError("agent_card is not defined")
        if self.task_manager is None:
            raise ValueError("task_manager is not defined")
        import uvicorn

        uvicorn.run(self.app, host=self.host, port=self.port)

    async def _get_agent_card(self, request: Request) -> JSONResponse:
        # Liefert die AgentCard als JSON zurück.
        return JSONResponse(self.agent_card.model_dump(exclude_none=True))

    async def _process_request(
        self, request: Request
    ) -> Union[JSONResponse, EventSourceResponse]:
        try:
            body = await request.json()
            logger.info(f"Request method: {body.get('method')}")
            logger.info(f"Request body keys: {list(body.keys())}")
            logger.info(f"Params keys: {list(body.get('params', {}).keys())}")
            
            # Handle Google A2A message/send requests directly with manual parsing
            method = body.get('method')
            logger.info(f"Checking method: '{method}' (type: {type(method)})")
            if method == 'message/send':
                logger.info("Processing Google A2A message/send request")
                
                # 📝 LOG A2A COMMUNICATION - 모든 Agent 간 대화를 자동 기록
                log_a2a_communication(self.port, body)
                
                try:
                    # Manually construct A2AMessageSendRequest
                    from shared.custom_types import A2AMessageSendRequest, MessageSendParams, A2AMessage, A2ATextPart
                    
                    # Extract message parts
                    message_data = body.get('params', {}).get('message', {})
                    parts = []
                    for part_data in message_data.get('parts', []):
                        if part_data.get('kind') == 'text':
                            parts.append(A2ATextPart(
                                kind='text',
                                text=part_data.get('text', ''),
                                mimeType=part_data.get('mimeType', 'text/plain')
                            ))
                    
                    # Create A2A message
                    a2a_message = A2AMessage(
                        role=message_data.get('role', 'user'),
                        parts=parts,
                        messageId=message_data.get('messageId', 'auto'),
                        kind='message'
                    )
                    
                    # Create parameters
                    params = MessageSendParams(message=a2a_message)
                    
                    # Create request
                    json_rpc_request = A2AMessageSendRequest(
                        id=body.get('id', 'auto'),
                        params=params
                    )
                    
                    logger.info(f"Successfully manually parsed A2A message/send request")
                except Exception as e:
                    logger.error(f"Failed to manually parse A2A message/send: {e}")
                    import traceback
                    traceback.print_exc()
                    # Return error immediately
                    from shared.custom_types import InvalidRequestError, JSONRPCResponse
                    return JSONResponse(
                        JSONRPCResponse(
                            id=body.get('id'),
                            error=InvalidRequestError(data=str(e))
                        ).model_dump(exclude_none=True),
                        status_code=400
                    )
            else:
                # Only use TypeAdapter for non-A2A requests
                try:
                    json_rpc_request = A2ARequest.validate_python(body)
                except Exception as e:
                    logger.error(f"Failed to parse non-A2A request: {e}")
                    return JSONResponse(
                        JSONRPCResponse(
                            id=body.get('id'),
                            error=InvalidRequestError(data=str(e))
                        ).model_dump(exclude_none=True),
                        status_code=400
                    )
                
            logger.info(f"Parsed as: {type(json_rpc_request).__name__}")

            if isinstance(json_rpc_request, A2AMessageSendRequest):
                result = await self.task_manager.on_a2a_message_send(
                    json_rpc_request
                )
            elif isinstance(json_rpc_request, A2AMessageStreamRequest):
                # Handle A2A message streaming (future implementation)
                result = await self.task_manager.on_a2a_message_send(
                    json_rpc_request
                )
            elif isinstance(json_rpc_request, GetTaskRequest):
                result = await self.task_manager.on_get_task(json_rpc_request)
            elif isinstance(json_rpc_request, SendTaskRequest):
                result = await self.task_manager.on_send_task(json_rpc_request)
            elif isinstance(json_rpc_request, SendTaskStreamingRequest):
                result = await self.task_manager.on_send_task_subscribe(
                    json_rpc_request
                )
            elif isinstance(json_rpc_request, CancelTaskRequest):
                result = await self.task_manager.on_cancel_task(json_rpc_request)
            elif isinstance(json_rpc_request, SetTaskPushNotificationRequest):
                result = await self.task_manager.on_set_task_push_notification(
                    json_rpc_request
                )
            elif isinstance(json_rpc_request, GetTaskPushNotificationRequest):
                result = await self.task_manager.on_get_task_push_notification(
                    json_rpc_request
                )
            elif isinstance(json_rpc_request, TaskResubscriptionRequest):
                result = await self.task_manager.on_resubscribe_to_task(
                    json_rpc_request
                )
            else:
                logger.warning(f"Unexpected request type: {type(json_rpc_request)}")
                raise ValueError(f"Unexpected request type: {type(json_rpc_request)}")

            return self._create_response(result)

        except Exception as e:
            return self._handle_exception(e)

    def _handle_exception(self, e: Exception) -> JSONResponse:
        if isinstance(e, json.decoder.JSONDecodeError):
            json_rpc_error = JSONParseError()
        elif isinstance(e, ValidationError):
            json_rpc_error = InvalidRequestError(data=json.loads(e.json()))
        else:
            logger.error(f"Unhandled exception: {e}")
            json_rpc_error = InternalError()

        response = JSONRPCResponse(id=None, error=json_rpc_error)
        return JSONResponse(response.model_dump(exclude_none=True), status_code=400)

    def _create_response(self, result: Any) -> Union[JSONResponse, EventSourceResponse]:
        if isinstance(result, AsyncIterable):

            async def event_generator(
                result: AsyncIterable,
            ) -> AsyncIterable[dict[str, str]]:
                async for item in result:
                    yield {"data": item.model_dump_json(exclude_none=True)}

            return EventSourceResponse(event_generator(result))
        elif isinstance(result, JSONRPCResponse):
            return JSONResponse(result.model_dump(exclude_none=True))
        else:
            logger.error(f"Unexpected result type: {type(result)}")
            raise ValueError(f"Unexpected result type: {type(result)}")

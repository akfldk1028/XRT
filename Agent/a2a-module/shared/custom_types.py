from datetime import datetime
from enum import Enum
from typing import Annotated, Any, List, Literal, Optional, Union
from uuid import uuid4

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    TypeAdapter,
    field_serializer,
    model_validator,
)
from typing_extensions import Self


class TaskState(str, Enum):
    SUBMITTED = "submitted"
    WORKING = "working"
    INPUT_REQUIRED = "input-required"
    AUTH_REQUIRED = "auth-required"
    COMPLETED = "completed"
    CANCELED = "canceled"
    REJECTED = "rejected"
    FAILED = "failed"
    UNKNOWN = "unknown"


# A2A Standard Error Codes (JSON-RPC 2.0 + A2A specific)
class A2AErrorCodes:
    # Standard JSON-RPC errors
    PARSE_ERROR = -32700
    INVALID_REQUEST = -32600
    METHOD_NOT_FOUND = -32601
    INVALID_PARAMS = -32602
    INTERNAL_ERROR = -32603
    
    # A2A specific errors (-32000 to -32099)
    TASK_NOT_FOUND = -32001
    TASK_NOT_CANCELABLE = -32002
    PUSH_NOTIFICATION_NOT_SUPPORTED = -32003
    UNSUPPORTED_OPERATION = -32004
    CONTENT_TYPE_NOT_SUPPORTED = -32005
    INVALID_AGENT_RESPONSE = -32006


# Base Part types (legacy support)
class TextPart(BaseModel):
    type: Literal["text"] = "text"
    text: str
    metadata: dict[str, Any] | None = None


class FileContent(BaseModel):
    name: str | None = None
    mimeType: str | None = None
    bytes: str | None = None
    uri: str | None = None

    @model_validator(mode="after")
    def check_content(self) -> Self:
        if not (self.bytes or self.uri):
            raise ValueError("Either 'bytes' or 'uri' must be present in the file data")
        if self.bytes and self.uri:
            raise ValueError(
                "Only one of 'bytes' or 'uri' can be present in the file data"
            )
        return self


class FilePart(BaseModel):
    type: Literal["file"] = "file"
    file: FileContent
    metadata: dict[str, Any] | None = None


class DataPart(BaseModel):
    type: Literal["data"] = "data"
    data: dict[str, Any]
    metadata: dict[str, Any] | None = None


Part = Annotated[Union[TextPart, FilePart, DataPart], Field(discriminator="type")]


# A2A Protocol Message Parts (Google ADK compliant)
class A2ATextPart(BaseModel):
    """A2A Protocol text part"""
    kind: Literal["text"] = "text"
    text: str
    mimeType: Optional[str] = "text/plain"
    metadata: Optional[dict[str, Any]] = None


class A2AFilePart(BaseModel):
    """A2A Protocol file part"""
    kind: Literal["file"] = "file"
    file: FileContent
    inline: Optional[bool] = None
    metadata: Optional[dict[str, Any]] = None


class A2ADataPart(BaseModel):
    """A2A Protocol data part"""
    kind: Literal["data"] = "data"
    data: dict[str, Any]
    mimeType: Optional[str] = "application/json"
    inline: Optional[bool] = None
    metadata: Optional[dict[str, Any]] = None


A2APart = Annotated[Union[A2ATextPart, A2AFilePart, A2ADataPart], Field(discriminator="kind")]


# A2A Protocol Message (Google ADK compliant)
class A2AMessage(BaseModel):
    """A2A Protocol message format"""
    role: Literal["user", "agent"]
    parts: List[A2APart]
    messageId: str = Field(default_factory=lambda: f"msg_{uuid4().hex[:12]}")
    parentMessageId: Optional[str] = None
    rootMessageId: Optional[str] = None
    referenceTaskIds: Optional[List[str]] = None
    taskId: Optional[str] = None
    contextId: Optional[str] = None
    kind: Literal["message"] = "message"
    metadata: Optional[dict[str, Any]] = None


# Legacy Message for backward compatibility
class Message(BaseModel):
    role: Literal["user", "agent"]
    parts: List[Part]
    metadata: dict[str, Any] | None = None


class TaskStatus(BaseModel):
    state: TaskState
    message: Optional[Union[Message, A2AMessage]] = None
    timestamp: datetime = Field(default_factory=datetime.now)

    @field_serializer("timestamp")
    def serialize_dt(self, dt: datetime, _info):
        return dt.isoformat()


class Artifact(BaseModel):
    artifactId: str = Field(default_factory=lambda: f"artifact_{uuid4().hex[:12]}")
    name: str | None = None
    description: str | None = None
    parts: List[A2APart]
    metadata: dict[str, Any] | None = None
    append: bool | None = None
    lastChunk: bool | None = None


class Task(BaseModel):
    id: str = Field(default_factory=lambda: f"task_{uuid4().hex[:12]}")
    contextId: str = Field(default_factory=lambda: f"ctx_{uuid4().hex[:12]}")
    status: TaskStatus
    artifacts: List[Artifact] | None = None
    history: List[A2AMessage] | None = None
    kind: Literal["task"] = "task"
    metadata: dict[str, Any] | None = None


class TaskStatusUpdateEvent(BaseModel):
    taskId: str
    contextId: str
    status: TaskStatus
    kind: Literal["status-update"] = "status-update"
    final: bool = False
    metadata: dict[str, Any] | None = None


class TaskArtifactUpdateEvent(BaseModel):
    taskId: str
    contextId: str
    artifact: Artifact
    kind: Literal["artifact-update"] = "artifact-update"
    append: bool = False
    lastChunk: bool = False
    final: bool = False
    metadata: dict[str, Any] | None = None


# Authentication and Push Notifications
class AuthenticationInfo(BaseModel):
    model_config = ConfigDict(extra="allow")
    schemes: List[str]
    credentials: str | None = None


class PushNotificationConfig(BaseModel):
    url: str
    token: str | None = None
    authentication: AuthenticationInfo | None = None


# Request Parameters
class TaskIdParams(BaseModel):
    id: str
    metadata: dict[str, Any] | None = None


class TaskQueryParams(TaskIdParams):
    historyLength: int | None = None


class TaskSendParams(BaseModel):
    id: str
    sessionId: str = Field(default_factory=lambda: uuid4().hex)
    message: Message
    acceptedOutputModes: Optional[List[str]] = None
    pushNotification: PushNotificationConfig | None = None
    historyLength: int | None = None
    metadata: dict[str, Any] | None = None


class TaskPushNotificationConfig(BaseModel):
    id: str
    pushNotificationConfig: PushNotificationConfig


# Message Send Configuration (A2A Protocol)
class MessageSendConfiguration(BaseModel):
    """Configuration for message sending"""
    pushNotificationConfig: Optional[PushNotificationConfig] = None
    acceptedOutputModes: Optional[List[str]] = None
    metadata: Optional[dict[str, Any]] = None


class MessageSendParams(BaseModel):
    """Parameters for message/send method"""
    message: A2AMessage
    configuration: Optional[MessageSendConfiguration] = None
    metadata: Optional[dict[str, Any]] = None


## JSON-RPC Base Classes
class JSONRPCMessage(BaseModel):
    jsonrpc: Literal["2.0"] = "2.0"
    id: int | str | None = Field(default_factory=lambda: uuid4().hex)


class JSONRPCRequest(JSONRPCMessage):
    method: str
    params: dict[str, Any] | None = None


class JSONRPCError(BaseModel):
    code: int
    message: str
    data: Any | None = None


class JSONRPCResponse(JSONRPCMessage):
    result: Any | None = None
    error: JSONRPCError | None = None

    @model_validator(mode="after")
    def validate_result_or_error(self) -> Self:
        """Ensure result and error are mutually exclusive"""
        if self.result is not None and self.error is not None:
            raise ValueError("result and error are mutually exclusive")
        if self.result is None and self.error is None:
            raise ValueError("Either result or error must be present")
        return self


## JSON-RPC Request Types

# Legacy task endpoints
class SendTaskRequest(JSONRPCRequest):
    method: Literal["tasks/send"] = "tasks/send"
    params: TaskSendParams


class SendTaskResponse(JSONRPCResponse):
    result: Task | None = None


class SendTaskStreamingRequest(JSONRPCRequest):
    method: Literal["tasks/sendSubscribe"] = "tasks/sendSubscribe"
    params: TaskSendParams


class GetTaskRequest(JSONRPCRequest):
    method: Literal["tasks/get"] = "tasks/get"
    params: TaskQueryParams


class GetTaskResponse(JSONRPCResponse):
    result: Task | None = None


class CancelTaskRequest(JSONRPCRequest):
    method: Literal["tasks/cancel"] = "tasks/cancel"
    params: TaskIdParams


class CancelTaskResponse(JSONRPCResponse):
    result: Task | None = None


class SetTaskPushNotificationRequest(JSONRPCRequest):
    method: Literal["tasks/pushNotification/set"] = "tasks/pushNotification/set"
    params: TaskPushNotificationConfig


class SetTaskPushNotificationResponse(JSONRPCResponse):
    result: TaskPushNotificationConfig | None = None


class GetTaskPushNotificationRequest(JSONRPCRequest):
    method: Literal["tasks/pushNotification/get"] = "tasks/pushNotification/get"
    params: TaskIdParams


class GetTaskPushNotificationResponse(JSONRPCResponse):
    result: TaskPushNotificationConfig | None = None


class TaskResubscriptionRequest(JSONRPCRequest):
    method: Literal["tasks/resubscribe"] = "tasks/resubscribe"
    params: TaskQueryParams


# A2A Protocol message endpoints
class A2AMessageSendRequest(JSONRPCRequest):
    """JSON-RPC request for message/send"""
    method: Literal["message/send"] = "message/send"
    params: MessageSendParams


class A2AMessageStreamRequest(JSONRPCRequest):
    """JSON-RPC request for message/stream"""
    method: Literal["message/stream"] = "message/stream"
    params: MessageSendParams


# A2A Response types
class A2AMessageResponse(BaseModel):
    """Response for message/send when returning a message directly"""
    role: Literal["agent"] = "agent"
    parts: List[A2APart]
    messageId: str
    contextId: Optional[str] = None
    kind: Literal["message"] = "message"
    metadata: Optional[dict[str, Any]] = None


class A2ATaskResponse(BaseModel):
    """Response for message/send when creating a task"""
    id: str
    contextId: str
    status: TaskStatus
    artifacts: Optional[List[Artifact]] = None
    history: Optional[List[A2AMessage]] = None
    kind: Literal["task"] = "task"
    metadata: Optional[dict[str, Any]] = None


class A2AMessageSendResponse(JSONRPCResponse):
    """Response for message/send - can be either Message or Task"""
    result: Union[A2AMessageResponse, A2ATaskResponse, None] = None


# SSE Streaming Response types
class SendStreamingMessageResponse(BaseModel):
    """SSE response for message/stream"""
    jsonrpc: Literal["2.0"] = "2.0"
    id: Union[str, int]
    result: Union[A2AMessageResponse, TaskStatusUpdateEvent, TaskArtifactUpdateEvent]
    final: Optional[bool] = False


class SendTaskStreamingResponse(JSONRPCResponse):
    result: Union[TaskStatusUpdateEvent, TaskArtifactUpdateEvent, None] = None


# Request type adapter for routing - A2A Protocol focus (Google A2A first)
A2ARequest = TypeAdapter(
    Annotated[
        Union[
            A2AMessageSendRequest,
            A2AMessageStreamRequest,
            GetTaskRequest,
            CancelTaskRequest,
            SetTaskPushNotificationRequest,
            GetTaskPushNotificationRequest,
            TaskResubscriptionRequest,
            SendTaskRequest,
            SendTaskStreamingRequest,
        ],
        Field(discriminator="method"),
    ]
)


## Error type implementations
class JSONParseError(JSONRPCError):
    def __init__(self, data: Any = None):
        super().__init__(
            code=A2AErrorCodes.PARSE_ERROR,
            message="Invalid JSON payload",
            data=data
        )


class InvalidRequestError(JSONRPCError):
    def __init__(self, data: Any = None):
        super().__init__(
            code=A2AErrorCodes.INVALID_REQUEST,
            message="Invalid JSON-RPC Request",
            data=data
        )


class MethodNotFoundError(JSONRPCError):
    def __init__(self, method: str = None):
        super().__init__(
            code=A2AErrorCodes.METHOD_NOT_FOUND,
            message=f"Method not found: {method}" if method else "Method not found",
            data={"method": method} if method else None
        )


class InvalidParamsError(JSONRPCError):
    def __init__(self, data: Any = None):
        super().__init__(
            code=A2AErrorCodes.INVALID_PARAMS,
            message="Invalid method parameters",
            data=data
        )


class InternalError(JSONRPCError):
    def __init__(self, data: Any = None):
        super().__init__(
            code=A2AErrorCodes.INTERNAL_ERROR,
            message="Internal server error",
            data=data
        )


class TaskNotFoundError(JSONRPCError):
    def __init__(self, task_id: str = None):
        super().__init__(
            code=A2AErrorCodes.TASK_NOT_FOUND,
            message=f"Task not found: {task_id}" if task_id else "Task not found",
            data={"task_id": task_id} if task_id else None
        )


class TaskNotCancelableError(JSONRPCError):
    def __init__(self, task_id: str = None, state: str = None):
        super().__init__(
            code=A2AErrorCodes.TASK_NOT_CANCELABLE,
            message="Task cannot be canceled",
            data={"task_id": task_id, "state": state} if task_id else None
        )


class ContentTypeNotSupportedError(JSONRPCError):
    def __init__(self, content_type: str = None):
        super().__init__(
            code=A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED,
            message="Incompatible content types",
            data={"content_type": content_type} if content_type else None
        )


class UnsupportedOperationError(JSONRPCError):
    def __init__(self, operation: str = None):
        super().__init__(
            code=A2AErrorCodes.UNSUPPORTED_OPERATION,
            message="This operation is not supported",
            data={"operation": operation} if operation else None
        )


## Agent Card types
class AgentSkill(BaseModel):
    id: str
    name: str
    description: str
    tags: List[str] = []
    examples: List[str] = []
    inputModes: List[str] = ["text/plain", "application/json"]
    outputModes: List[str] = ["text/plain", "application/json"]


class AgentCapabilities(BaseModel):
    streaming: bool = False
    pushNotifications: bool = False
    stateTransitionHistory: bool = False


class AgentProvider(BaseModel):
    organization: str = "A2A Agent"
    url: Optional[str] = None


class SecurityScheme(BaseModel):
    type: str
    scheme: Optional[str] = None
    bearerFormat: Optional[str] = None
    openIdConnectUrl: Optional[str] = None


class AgentInterface(BaseModel):
    url: str
    transport: Literal["JSONRPC", "GRPC", "HTTP+JSON"] = "JSONRPC"


class AgentCard(BaseModel):
    protocolVersion: str = "0.2.9"
    name: str
    description: str
    url: str
    preferredTransport: Literal["JSONRPC", "GRPC", "HTTP+JSON"] = "JSONRPC"
    additionalInterfaces: Optional[List[AgentInterface]] = None
    provider: Optional[AgentProvider] = None
    iconUrl: Optional[str] = None
    version: str = "1.0.0"
    documentationUrl: Optional[str] = None
    capabilities: AgentCapabilities = Field(default_factory=AgentCapabilities)
    securitySchemes: Optional[dict[str, SecurityScheme]] = None
    security: Optional[List[dict[str, List[str]]]] = None
    defaultInputModes: List[str] = ["text/plain", "application/json"]
    defaultOutputModes: List[str] = ["text/plain", "application/json"]
    skills: List[AgentSkill] = []
    supportsAuthenticatedExtendedCard: bool = False
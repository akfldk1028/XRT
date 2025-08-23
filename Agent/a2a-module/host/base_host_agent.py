"""
Abstract Base Host Agent - Provides common functionality for all host agents
"""
import uuid
from abc import ABC, abstractmethod
from typing import List, Dict, Optional, Any
import requests
from dataclasses import dataclass
from enum import Enum

class TaskState(str, Enum):
    """Task states matching A2A protocol"""
    SUBMITTED = "submitted"
    WORKING = "working"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELED = "canceled"
    INPUT_REQUIRED = "input-required"
    UNKNOWN = "unknown"


@dataclass
class AgentCard:
    """Agent metadata following A2A protocol"""
    name: str
    description: str
    url: str
    version: str
    capabilities: Dict[str, Any]


class RemoteAgentConnection:
    """Connection to a single remote agent"""
    
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.agent_card: Optional[AgentCard] = None
        
    def retrieve_card(self) -> AgentCard:
        """Fetch agent card from /.well-known/agent.json"""
        url = f"{self.base_url}/.well-known/agent.json"
        resp = requests.get(url, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        
        self.agent_card = AgentCard(
            name=data["name"],
            description=data.get("description", ""),
            url=self.base_url,
            version=data["version"],
            capabilities=data.get("capabilities", {})
        )
        return self.agent_card
    
    def send_message(self, message: str, context_id: str = None, task_id: str = None) -> Dict:
        """Send message using A2A protocol (message/send)"""
        if not task_id:
            task_id = str(uuid.uuid4())
        if not context_id:
            context_id = str(uuid.uuid4())
            
        payload = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "message/send",
            "params": {
                "message": {
                    "role": "user",
                    "parts": [{"kind": "text", "text": message}],
                    "messageId": str(uuid.uuid4()),
                    "contextId": context_id,
                    "taskId": task_id
                }
            }
        }
        
        r = requests.post(self.base_url, json=payload, timeout=30)
        r.raise_for_status()
        resp = r.json()
        
        if "error" in resp and resp["error"] is not None:
            raise RuntimeError(f"Remote agent error: {resp['error']}")
        
        return resp.get("result", {})


class BaseHostAgent(ABC):
    """
    Abstract Base Host Agent following Google ADK patterns.
    All specific host agents should inherit from this class.
    """
    
    def __init__(self, remote_agent_addresses: List[str], host_type: str = "BaseHost"):
        self.host_type = host_type
        self.remote_agent_connections: Dict[str, RemoteAgentConnection] = {}
        self.cards: Dict[str, AgentCard] = {}
        self.state: Dict[str, Any] = {
            'session_active': False,
            'context_id': None,
            'task_id': None,
            'active_agent': None,
            'host_type': host_type
        }
        
        # Initialize remote connections
        self.init_remote_agent_addresses(remote_agent_addresses)
    
    def init_remote_agent_addresses(self, addresses: List[str]):
        """Initialize connections to remote agents and retrieve their cards"""
        for address in addresses:
            try:
                connection = RemoteAgentConnection(address)
                card = connection.retrieve_card()
                self.register_agent_card(card, connection)
                print(f"[{self.host_type}] Connected to: {card.name} at {address}")
            except Exception as e:
                print(f"[{self.host_type}] Failed to connect to {address}: {e}")
    
    def register_agent_card(self, card: AgentCard, connection: RemoteAgentConnection):
        """Register an agent card and its connection"""
        self.remote_agent_connections[card.name] = connection
        self.cards[card.name] = card
    
    def check_state(self) -> Dict[str, Any]:
        """Check current state (matching Google ADK pattern)"""
        if (
            'context_id' in self.state
            and 'session_active' in self.state
            and self.state['session_active']
            and 'active_agent' in self.state
        ):
            return {'active_agent': self.state['active_agent'], 'host_type': self.host_type}
        return {'active_agent': 'None', 'host_type': self.host_type}
    
    def before_message_callback(self):
        """Callback before sending message (matching Google ADK pattern)"""
        if 'session_active' not in self.state or not self.state['session_active']:
            self.state['session_active'] = True
    
    def list_remote_agents(self) -> List[Dict[str, str]]:
        """List available remote agents (matching Google ADK pattern)"""
        if not self.remote_agent_connections:
            return []
        
        remote_agent_info = []
        for card in self.cards.values():
            remote_agent_info.append({
                'name': card.name,
                'description': card.description,
                'url': card.url,
                'capabilities': card.capabilities
            })
        return remote_agent_info
    
    def send_message(self, agent_name: str, message: str) -> Dict[str, Any]:
        """
        Send message to specific agent.
        Following Google ADK send_message pattern.
        """
        if agent_name not in self.remote_agent_connections:
            raise ValueError(f'Agent {agent_name} not found')
        
        # Update state
        self.before_message_callback()
        self.state['active_agent'] = agent_name
        
        # Get connection
        connection = self.remote_agent_connections[agent_name]
        if not connection:
            raise ValueError(f'Connection not available for {agent_name}')
        
        # Send message with current context
        task_id = self.state.get('task_id')
        context_id = self.state.get('context_id')
        
        try:
            result = connection.send_message(message, context_id, task_id)
            
            # Update state based on response
            status = result.get("status", {})
            state = status.get("state", "unknown")
            
            # Update session state based on task state
            self.state['session_active'] = state not in [
                TaskState.COMPLETED,
                TaskState.CANCELED,
                TaskState.FAILED,
                TaskState.UNKNOWN
            ]
            
            # Store task and context IDs for continuity
            if result.get("id"):
                self.state['task_id'] = result["id"]
            if result.get("sessionId"):
                self.state['context_id'] = result["sessionId"]
            
            # Process response based on state
            if state == TaskState.INPUT_REQUIRED:
                return {
                    'status': 'input_required',
                    'message': self._extract_message(result),
                    'agent': agent_name
                }
            elif state == TaskState.COMPLETED:
                return {
                    'status': 'completed',
                    'message': self._extract_message(result),
                    'agent': agent_name
                }
            elif state == TaskState.FAILED:
                raise ValueError(f'Agent {agent_name} task failed')
            elif state == TaskState.CANCELED:
                raise ValueError(f'Agent {agent_name} task was canceled')
            else:
                return {
                    'status': state,
                    'message': self._extract_message(result),
                    'agent': agent_name
                }
                
        except Exception as e:
            self.state['session_active'] = False
            raise RuntimeError(f"Error sending to {agent_name}: {e}")
    
    def _extract_message(self, result: Dict) -> str:
        """Extract message content from result"""
        # Try artifacts first
        artifacts = result.get("artifacts", [])
        if artifacts:
            parts = artifacts[0].get("parts", [{}])
            if parts:
                return parts[0].get("text", "")
        
        # Then try status message
        status = result.get("status", {})
        if status:
            message = status.get("message", {})
            if message:
                parts = message.get("parts", [{}])
                if parts:
                    return parts[0].get("text", "")
        
        return "No content available"
    
    @abstractmethod
    def get_agent_for_task(self, task_description: str) -> Optional[str]:
        """
        Abstract method: Determine which agent to use for a given task.
        Each host implementation should define its own logic.
        """
        pass
    
    @abstractmethod
    def orchestrate(self, user_request: str) -> str:
        """
        Abstract method: Main orchestration logic.
        Each host implementation should define its own orchestration strategy.
        """
        pass
    
    def get_host_description(self) -> str:
        """Get description of this host's capabilities"""
        return f"{self.host_type} - Manages {len(self.cards)} agents"
"""
UX/TTS Agent - A2A Protocol Implementation with Claude CLI
"""
import os
import sys
import asyncio
import subprocess
from typing import Any, AsyncIterable, Dict, Literal, List, Optional, Union, Final, TYPE_CHECKING
from pathlib import Path

try:
    from typing import TypeAlias
except ImportError:
    from typing_extensions import TypeAlias

# Add parent directories to path
sys.path.append(str(Path(__file__).parent.parent.parent.parent))

from langchain_core.messages import AIMessage, ToolMessage
from pydantic import BaseModel
from langgraph.checkpoint.memory import MemorySaver

# Import shared modules
from shared.custom_types import (
    Task, TaskStatus, TaskState, Message, TextPart, Artifact
)

memory = MemorySaver()

# Type aliases for better maintainability
AgentResponse: TypeAlias = Dict[str, Any]
Query: TypeAlias = str
SessionId: TypeAlias = str
CommandList: TypeAlias = List[str]
ProcessOutput: TypeAlias = tuple[Optional[bytes], Optional[bytes]]


class ResponseFormat(BaseModel):
    """Response format for agent responses"""
    status: Literal["input_required", "completed", "error"] = "input_required"
    message: str


class UXTTSCLIAgent:
    """UX/TTS Agent that uses Claude CLI subprocess for responses"""
    
    SYSTEM_INSTRUCTION: Final[str] = (
        "You are a User Experience & Text-to-Speech specialist for Android XR applications. "
        "You handle HUD display, voice output, UI interaction, and audio feedback for XR "
        "environments. Focus on intuitive interaction and immersive experience design."
    )
    
    SUPPORTED_CONTENT_TYPES: Final[List[str]] = ["text", "text/plain"]
    
    def __init__(self) -> None:
        self.claude_context_path: Path = Path(__file__).parent / "CLAUDE.md"
        
    async def invoke_claude_cli(self, query: Query, session_id: SessionId) -> AgentResponse:
        """
        Invoke Claude CLI as a subprocess and get response
        """
        try:
            # Build Claude CLI command - using claude.cmd for Windows compatibility and stdin
            cmd: CommandList = [
                "claude.cmd",
                "--print",  # Non-interactive mode
                "--permission-mode", "bypassPermissions",  # Bypass permissions for A2A
                "--add-dir", str(self.claude_context_path.parent),  # Add agent directory for CLAUDE.md
                "--append-system-prompt", self.SYSTEM_INSTRUCTION
            ]
            
            # Run Claude CLI subprocess with stdin
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=False
            )
            
            # Send query via stdin and wait for completion with timeout
            query_bytes = query.encode('utf-8')
            stdout, stderr = await asyncio.wait_for(
                process.communicate(input=query_bytes),
                timeout=600.0  # 10 minutes for complex AI responses and A2A communication
            )
            output: ProcessOutput = (stdout, stderr)
            
            # Decode output with proper encoding handling
            response_text: str = stdout.decode('utf-8', errors='replace') if stdout else ""
            error_text: str = stderr.decode('utf-8', errors='replace') if stderr else ""
            
            # 로깅: Agent 간 대화 모니터링을 위한 로그
            print(f"[UX/TTS Agent] Claude CLI Response: {len(response_text)} chars")
            if len(response_text) > 200:
                print(f"[UX/TTS Agent] Preview: {response_text[:200]}...")
            else:
                print(f"[UX/TTS Agent] Full Response: {response_text}")
            
            if process.returncode != 0:
                return {
                    "is_task_complete": False,
                    "require_user_input": True,
                    "content": f"Error from Claude CLI: {error_text or 'Unknown error'}"
                }
            
            # Parse response - Claude CLI returns plain text
            return {
                "is_task_complete": True,
                "require_user_input": False,
                "content": response_text.strip()
            }
            
        except asyncio.TimeoutError:
            return {
                "is_task_complete": False,
                "require_user_input": True,
                "content": "Claude CLI request timed out after 10 minutes"
            }
        except FileNotFoundError:
            return {
                "is_task_complete": False,
                "require_user_input": True,
                "content": "Claude CLI not found. Please ensure 'claude' is installed and in PATH"
            }
        except Exception as e:
            return {
                "is_task_complete": False,
                "require_user_input": True,
                "content": f"Error invoking Claude CLI: {str(e)}"
            }
    
    async def invoke_async(self, query: Query, session_id: SessionId) -> AgentResponse:
        """
        Async method to invoke agent
        """
        print(f"[UX/TTS Agent] Received query: {query[:100]}...")
        result = await self.invoke_claude_cli(query, session_id)
        print(f"[UX/TTS Agent] Response status: task_complete={result.get('is_task_complete')}")
        return result
    
    async def stream(self, query: Query, session_id: SessionId) -> AsyncIterable[AgentResponse]:
        """
        Stream responses (calls invoke_async and yields result)
        """
        # For Claude CLI, we get the full response at once
        yield {
            "is_task_complete": False,
            "require_user_input": False,
            "content": "Processing UX/TTS request with Claude CLI..."
        }
        
        result = await self.invoke_async(query, session_id)
        yield result
    
    def get_agent_response(self, result: AgentResponse) -> AgentResponse:
        """
        Format the agent response
        """
        return result


# For testing standalone
if __name__ == "__main__":
    async def test() -> None:
        agent = UXTTSCLIAgent()
        response = await agent.invoke_async(
            "Create Android TTS system for XR voice feedback",
            "test_session_123"
        )
        print("Response:", response)
    
    asyncio.run(test())
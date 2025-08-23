"""
Logger/Metrics Agent - A2A Protocol Implementation with Claude CLI
"""
import os
import sys
import asyncio
import subprocess
import json
import datetime
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


class LoggerCLIAgent:
    """Logger/Metrics Agent that uses Claude CLI subprocess for responses"""
    
    SYSTEM_INSTRUCTION: Final[str] = (
        "You are a Logging & Metrics specialist for Android XR applications. "
        "You handle performance monitoring, user analytics, error tracking, and comprehensive "
        "logging for XR environments. Focus on actionable insights and performance optimization."
    )
    
    SUPPORTED_CONTENT_TYPES: Final[List[str]] = ["text", "text/plain"]
    
    def __init__(self) -> None:
        self.claude_context_path: Path = Path(__file__).parent / "CLAUDE.md"
        self.logs_dir: Path = Path(__file__).parent / "logs"
        self.a2a_logs_dir: Path = self.logs_dir / "a2a_conversations"
        self.performance_logs_dir: Path = self.logs_dir / "performance"
        self.error_logs_dir: Path = self.logs_dir / "errors"
        
        # Create log directories if they don't exist
        self.logs_dir.mkdir(exist_ok=True)
        self.a2a_logs_dir.mkdir(exist_ok=True)
        self.performance_logs_dir.mkdir(exist_ok=True)
        self.error_logs_dir.mkdir(exist_ok=True)
    
    def log_a2a_conversation(self, agent_from: str, agent_to: str, message: str, response: str = None) -> None:
        """Log A2A conversation to file"""
        try:
            timestamp = datetime.datetime.now()
            log_filename = f"a2a_conversation_{timestamp.strftime('%Y%m%d_%H%M%S')}.log"
            log_file = self.a2a_logs_dir / log_filename
            
            log_entry = {
                "timestamp": timestamp.isoformat(),
                "agent_from": agent_from,
                "agent_to": agent_to,
                "message": message,
                "response": response,
                "session_info": {
                    "datetime": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                    "communication_type": "A2A_Protocol"
                }
            }
            
            # Append to log file
            with open(log_file, 'a', encoding='utf-8') as f:
                f.write(json.dumps(log_entry, indent=2, ensure_ascii=False) + "\n")
                f.write("-" * 80 + "\n")
                
            print(f"[Logger Agent] A2A conversation logged to {log_file}")
                
        except Exception as e:
            print(f"[Logger Agent] Failed to log A2A conversation: {str(e)}")
    
    def log_performance_metrics(self, metrics: Dict[str, Any]) -> None:
        """Log performance metrics to file"""
        try:
            timestamp = datetime.datetime.now()
            log_filename = f"performance_{timestamp.strftime('%Y%m%d')}.log"
            log_file = self.performance_logs_dir / log_filename
            
            log_entry = {
                "timestamp": timestamp.isoformat(),
                "metrics": metrics,
                "session_info": {
                    "datetime": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                    "log_type": "Performance_Metrics"
                }
            }
            
            # Append to log file
            with open(log_file, 'a', encoding='utf-8') as f:
                f.write(json.dumps(log_entry, indent=2, ensure_ascii=False) + "\n")
                
            print(f"[Logger Agent] Performance metrics logged to {log_file}")
                
        except Exception as e:
            print(f"[Logger Agent] Failed to log performance metrics: {str(e)}")
    
    def log_error(self, error_type: str, error_message: str, context: Dict[str, Any] = None) -> None:
        """Log error to file"""
        try:
            timestamp = datetime.datetime.now()
            log_filename = f"errors_{timestamp.strftime('%Y%m%d')}.log"
            log_file = self.error_logs_dir / log_filename
            
            log_entry = {
                "timestamp": timestamp.isoformat(),
                "error_type": error_type,
                "error_message": error_message,
                "context": context or {},
                "session_info": {
                    "datetime": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                    "log_type": "Error_Log"
                }
            }
            
            # Append to log file
            with open(log_file, 'a', encoding='utf-8') as f:
                f.write(json.dumps(log_entry, indent=2, ensure_ascii=False) + "\n")
                
            print(f"[Logger Agent] Error logged to {log_file}")
                
        except Exception as e:
            print(f"[Logger Agent] Failed to log error: {str(e)}")
        
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
            print(f"[Logger Agent] Claude CLI Response: {len(response_text)} chars")
            if len(response_text) > 200:
                print(f"[Logger Agent] Preview: {response_text[:200]}...")
            else:
                print(f"[Logger Agent] Full Response: {response_text}")
            
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
        print(f"[Logger Agent] Received query: {query[:100]}...")
        
        # Auto-detect A2A communication and log it
        try:
            # Try to parse if this is A2A communication logging request
            if "A2A" in query or "agent" in query.lower():
                # Extract agent info from query if possible
                agent_from = "unknown"
                agent_to = "logger"
                
                if "perception" in query.lower():
                    agent_from = "perception"
                elif "vision" in query.lower():
                    agent_from = "vision"
                elif "ux" in query.lower() or "tts" in query.lower():
                    agent_from = "ux_tts"
                elif "frontend" in query.lower():
                    agent_from = "frontend"
                elif "backend" in query.lower():
                    agent_from = "backend"
                elif "unity" in query.lower():
                    agent_from = "unity"
                    
                # Log the incoming A2A communication
                self.log_a2a_conversation(agent_from, agent_to, query)
                
        except Exception as e:
            print(f"[Logger Agent] Auto-logging detection failed: {str(e)}")
        
        result = await self.invoke_claude_cli(query, session_id)
        
        # If this was an A2A request, log the response too
        if result.get('is_task_complete') and result.get('content'):
            try:
                response_content = result.get('content', '')
                if len(query) > 50 and ("A2A" in query or "agent" in query.lower()):
                    # Update the log with the response
                    timestamp = datetime.datetime.now()
                    log_filename = f"a2a_conversation_{timestamp.strftime('%Y%m%d_%H%M%S')}.log"
                    log_file = self.a2a_logs_dir / log_filename
                    
                    # Append response to existing log
                    with open(log_file, 'a', encoding='utf-8') as f:
                        f.write(f"RESPONSE: {response_content}\n")
                        f.write("=" * 80 + "\n\n")
                        
            except Exception as e:
                print(f"[Logger Agent] Response logging failed: {str(e)}")
        
        print(f"[Logger Agent] Response status: task_complete={result.get('is_task_complete')}")
        return result
    
    async def stream(self, query: Query, session_id: SessionId) -> AsyncIterable[AgentResponse]:
        """
        Stream responses (calls invoke_async and yields result)
        """
        # For Claude CLI, we get the full response at once
        yield {
            "is_task_complete": False,
            "require_user_input": False,
            "content": "Processing logging/metrics request with Claude CLI..."
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
        agent = LoggerCLIAgent()
        response = await agent.invoke_async(
            "Setup Timber logging for Android XR performance monitoring",
            "test_session_123"
        )
        print("Response:", response)
    
    asyncio.run(test())
"""
A2A Conversation Logger
Logs all Agent-to-Agent communication for monitoring and debugging
"""
import json
import logging
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Optional, Union
import asyncio
from contextlib import asynccontextmanager

from .custom_types import (
    A2AMessage, 
    A2AMessageSendRequest,
    A2AMessageSendResponse,
    JSONRPCRequest,
    JSONRPCResponse,
    JSONRPCError
)


class A2AConversationLogger:
    """Logger for A2A protocol conversations between agents"""
    
    def __init__(self, log_dir: str = "logs/a2a_conversations"):
        """Initialize the A2A conversation logger"""
        self.log_dir = Path(log_dir)
        self.log_dir.mkdir(parents=True, exist_ok=True)
        
        # Set up Python logging
        self.logger = logging.getLogger("A2AConversation")
        self.logger.setLevel(logging.DEBUG)
        
        # Console handler with colored output
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        console_format = logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            datefmt='%H:%M:%S'
        )
        console_handler.setFormatter(console_format)
        self.logger.addHandler(console_handler)
        
        # File handler for detailed logs
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        file_handler = logging.FileHandler(
            self.log_dir / f"a2a_conversation_{timestamp}.log"
        )
        file_handler.setLevel(logging.DEBUG)
        file_format = logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )
        file_handler.setFormatter(file_format)
        self.logger.addHandler(file_handler)
        
        # JSON file for structured logs
        self.json_log_file = self.log_dir / f"a2a_structured_{timestamp}.jsonl"
        
    def log_request(self, 
                   sender: str, 
                   receiver: str, 
                   request: Union[Dict, JSONRPCRequest, A2AMessageSendRequest],
                   port: Optional[int] = None):
        """Log an A2A request"""
        timestamp = datetime.now().isoformat()
        
        # Convert to dict if it's a Pydantic model
        if hasattr(request, 'model_dump'):
            request_dict = request.model_dump(exclude_none=True)
        else:
            request_dict = request
            
        # Extract message content for logging
        message_preview = self._extract_message_preview(request_dict)
        
        # Console log
        port_info = f":{port}" if port else ""
        self.logger.info(
            f"[{sender} → {receiver}{port_info}] Request: {request_dict.get('method', 'unknown')} - {message_preview}"
        )
        
        # Structured log
        log_entry = {
            "timestamp": timestamp,
            "type": "request",
            "sender": sender,
            "receiver": receiver,
            "port": port,
            "method": request_dict.get("method"),
            "request_id": request_dict.get("id"),
            "message_preview": message_preview,
            "full_request": request_dict
        }
        
        self._write_json_log(log_entry)
        
    def log_response(self,
                    sender: str,
                    receiver: str,
                    response: Union[Dict, JSONRPCResponse, A2AMessageSendResponse],
                    duration_ms: Optional[float] = None):
        """Log an A2A response"""
        timestamp = datetime.now().isoformat()
        
        # Convert to dict if it's a Pydantic model
        if hasattr(response, 'model_dump'):
            response_dict = response.model_dump(exclude_none=True)
        else:
            response_dict = response
            
        # Check for errors
        error = response_dict.get("error")
        if error:
            self.logger.error(
                f"[{sender} ← {receiver}] Error: {error.get('message', 'Unknown error')} (code: {error.get('code')})"
            )
        else:
            # Extract response content
            result_preview = self._extract_result_preview(response_dict)
            duration_info = f" ({duration_ms:.0f}ms)" if duration_ms else ""
            self.logger.info(
                f"[{sender} ← {receiver}] Response{duration_info}: {result_preview}"
            )
        
        # Structured log
        log_entry = {
            "timestamp": timestamp,
            "type": "response",
            "sender": receiver,
            "receiver": sender,
            "response_id": response_dict.get("id"),
            "has_error": error is not None,
            "duration_ms": duration_ms,
            "full_response": response_dict
        }
        
        self._write_json_log(log_entry)
        
    def log_worker_conversation(self,
                               initiator: str,
                               responder: str,
                               topic: str,
                               messages: list):
        """Log a complete conversation between worker agents"""
        timestamp = datetime.now().isoformat()
        
        self.logger.info(f"\n{'='*60}")
        self.logger.info(f"Worker Conversation: {initiator} ↔ {responder}")
        self.logger.info(f"Topic: {topic}")
        self.logger.info(f"{'='*60}")
        
        for msg in messages:
            role = msg.get("role", "unknown")
            content = msg.get("content", "")[:200]
            self.logger.info(f"  [{role}]: {content}...")
            
        self.logger.info(f"{'='*60}\n")
        
        # Structured log
        log_entry = {
            "timestamp": timestamp,
            "type": "worker_conversation",
            "initiator": initiator,
            "responder": responder,
            "topic": topic,
            "message_count": len(messages),
            "messages": messages
        }
        
        self._write_json_log(log_entry)
        
    def log_error(self, 
                 context: str,
                 error: Union[Exception, JSONRPCError, Dict],
                 agent: Optional[str] = None):
        """Log an error in A2A communication"""
        timestamp = datetime.now().isoformat()
        
        if isinstance(error, Exception):
            error_msg = str(error)
            error_type = type(error).__name__
        elif isinstance(error, dict):
            error_msg = error.get("message", "Unknown error")
            error_type = f"Code: {error.get('code', 'unknown')}"
        else:
            error_msg = str(error)
            error_type = "Unknown"
            
        agent_info = f"[{agent}] " if agent else ""
        self.logger.error(f"{agent_info}Error in {context}: {error_type} - {error_msg}")
        
        # Structured log
        log_entry = {
            "timestamp": timestamp,
            "type": "error",
            "context": context,
            "agent": agent,
            "error_type": error_type,
            "error_message": error_msg,
            "full_error": str(error)
        }
        
        self._write_json_log(log_entry)
        
    @asynccontextmanager
    async def log_a2a_call(self, sender: str, receiver: str, port: Optional[int] = None):
        """Context manager for logging A2A calls with timing"""
        start_time = time.time()
        request_logged = False
        
        try:
            yield self
            
        except Exception as e:
            self.log_error(f"A2A call from {sender} to {receiver}", e, sender)
            raise
            
        finally:
            duration_ms = (time.time() - start_time) * 1000
            if not request_logged:
                self.logger.debug(f"[{sender} → {receiver}] Call completed in {duration_ms:.0f}ms")
                
    def _extract_message_preview(self, request_dict: Dict) -> str:
        """Extract a preview of the message content"""
        try:
            params = request_dict.get("params", {})
            
            # Try to get message from different possible locations
            message = params.get("message", {})
            if isinstance(message, dict):
                parts = message.get("parts", [])
                if parts and len(parts) > 0:
                    first_part = parts[0]
                    if isinstance(first_part, dict):
                        text = first_part.get("text", "")
                        if text:
                            return text[:100] + ("..." if len(text) > 100 else "")
                            
            # Fallback to string representation
            return str(params)[:100] + "..."
            
        except Exception:
            return "Unable to extract message"
            
    def _extract_result_preview(self, response_dict: Dict) -> str:
        """Extract a preview of the response result"""
        try:
            result = response_dict.get("result", {})
            
            if isinstance(result, dict):
                # Check for artifacts
                artifacts = result.get("artifacts", [])
                if artifacts and len(artifacts) > 0:
                    first_artifact = artifacts[0]
                    if isinstance(first_artifact, dict):
                        parts = first_artifact.get("parts", [])
                        if parts and len(parts) > 0:
                            first_part = parts[0]
                            if isinstance(first_part, dict):
                                text = first_part.get("text", "")
                                if text:
                                    return f"Artifact: {text[:100]}..."
                                    
                # Check for task status
                status = result.get("status", {})
                if isinstance(status, dict):
                    state = status.get("state", "unknown")
                    return f"Task {state}: {result.get('id', 'unknown')}"
                    
                # Check for message parts
                parts = result.get("parts", [])
                if parts and len(parts) > 0:
                    first_part = parts[0]
                    if isinstance(first_part, dict):
                        text = first_part.get("text", "")
                        if text:
                            return f"Message: {text[:100]}..."
                            
            return str(result)[:100] + "..."
            
        except Exception:
            return "Unable to extract result"
            
    def _write_json_log(self, log_entry: Dict):
        """Write a structured log entry to JSON file"""
        try:
            with open(self.json_log_file, 'a', encoding='utf-8') as f:
                f.write(json.dumps(log_entry, ensure_ascii=False) + '\n')
        except Exception as e:
            self.logger.error(f"Failed to write JSON log: {e}")
            

# Global logger instance
_logger_instance: Optional[A2AConversationLogger] = None


def get_conversation_logger() -> A2AConversationLogger:
    """Get or create the global conversation logger"""
    global _logger_instance
    if _logger_instance is None:
        _logger_instance = A2AConversationLogger()
    return _logger_instance


def log_a2a_request(sender: str, receiver: str, request: Any, port: Optional[int] = None):
    """Convenience function to log an A2A request"""
    logger = get_conversation_logger()
    logger.log_request(sender, receiver, request, port)
    

def log_a2a_response(sender: str, receiver: str, response: Any, duration_ms: Optional[float] = None):
    """Convenience function to log an A2A response"""
    logger = get_conversation_logger()
    logger.log_response(sender, receiver, response, duration_ms)
    

def log_a2a_error(context: str, error: Any, agent: Optional[str] = None):
    """Convenience function to log an A2A error"""
    logger = get_conversation_logger()
    logger.log_error(context, error, agent)
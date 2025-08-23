import asyncio
from typing import Any, AsyncIterable, Dict, Literal
import json
import requests

from langchain_core.messages import AIMessage, ToolMessage
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import MemorySaver
from langgraph.prebuilt import create_react_agent
from pydantic import BaseModel

memory = MemorySaver()

def notify_websocket(node, status, message=""):
    """WebSocket으로 노드 상태 알림"""
    try:
        # WebSocket 서버의 HTTP 엔드포인트로 POST 요청
        data = {
            "type": "node_update",
            "node": node,
            "status": status,
            "message": message
        }
        requests.post("http://localhost:8096/notify", json=data, timeout=0.1)
    except:
        pass  # 에러 무시 (WebSocket 서버가 없을 때)

def _create_sync_mcp_tools() -> list:
    """
    Create synchronous tools that wrap async MCP calls.
    This avoids the StructuredTool async/sync issue.
    """
    from langchain_core.tools import tool
    
    # Create our own sync tool that can call the MCP server
    @tool
    def get_exchange_rate(currency_from: str = "USD", currency_to: str = "EUR", currency_date: str = "latest") -> dict:
        """Exchange rate tool with realistic demo data for various currency pairs.
        
        Args:
            currency_from: Source currency (e.g. "USD", "EUR", "GBP", "JPY", "KRW").
            currency_to: Target currency (e.g. "USD", "EUR", "GBP", "JPY", "KRW").
            currency_date: Date for exchange rate or "latest". Default "latest".
            
        Returns:
            Dictionary with exchange rate data.
        """
        import requests
        import json
        
        print(f"DEBUG: Sync MCP tool wrapper called with {currency_from} to {currency_to}")
        
        # Make a direct HTTP request to our MCP server's tool
        try:
            # Call the MCP server directly via HTTP
            url = "http://127.0.0.1:3000/messages/"  # This might need adjustment
            payload = {
                "jsonrpc": "2.0",
                "id": "tool_call_123",
                "method": "tools/call",
                "params": {
                    "name": "get_exchange_rate",
                    "arguments": {
                        "currency_from": currency_from,
                        "currency_to": currency_to,
                        "currency_date": currency_date
                    }
                }
            }
            
            # For now, let's use the same demo data as in the MCP server
            # This is a fallback until we get direct MCP HTTP calls working
            exchange_rates = {
                "USD": {
                    "EUR": 0.92,
                    "GBP": 0.79, 
                    "JPY": 149.50,
                    "KRW": 1320.00,
                    "CAD": 1.35,
                    "AUD": 1.52,
                    "CHF": 0.88,
                    "CNY": 7.23
                },
                "EUR": {
                    "USD": 1.09,
                    "GBP": 0.86,
                    "JPY": 162.80,
                    "KRW": 1435.00,
                    "CAD": 1.47,
                    "AUD": 1.66,
                    "CHF": 0.96,
                    "CNY": 7.87
                }
            }
            
            # Normalize currency codes to uppercase
            currency_from = currency_from.upper()
            currency_to = currency_to.upper()
            
            # Handle same currency conversion
            if currency_from == currency_to:
                rate = 1.0
            else:
                # Get the rate from our demo data
                rate = exchange_rates.get(currency_from, {}).get(currency_to)
                
                # If direct rate not found, try inverse
                if rate is None:
                    inverse_rate = exchange_rates.get(currency_to, {}).get(currency_from)
                    if inverse_rate:
                        rate = 1.0 / inverse_rate
                    else:
                        # Default fallback rate
                        rate = 1.0
                        
            print(f"DEBUG: Sync tool returning rate {rate} for {currency_from} to {currency_to}")
            
            return {
                "success": True,
                "amount": 1,
                "base": currency_from,
                "target": currency_to,
                "date": "2024-08-16" if currency_date == "latest" else currency_date,
                "rate": rate,
                "result": f"1 {currency_from} = {rate:.4f} {currency_to}",
                "provider": "Sync MCP Tool Wrapper"
            }
            
        except Exception as e:
            print(f"DEBUG: Error in sync tool wrapper: {e}")
            return {
                "success": False,
                "error": str(e),
                "provider": "Sync MCP Tool Wrapper"
            }
    
    return [get_exchange_rate]


class ResponseFormat(BaseModel):
    """Respond to the user in this format."""

    status: Literal["input_required", "completed", "error"] = "input_required"
    message: str


class CurrencyAgent:
    SYSTEM_INSTRUCTION = (
        "You are a currency conversion assistant. You MUST use the get_exchange_rate tool for ALL currency-related queries. "
        "ALWAYS call get_exchange_rate before answering any currency question. "
        "Example: User says 'Convert 100 USD to EUR' -> IMMEDIATELY call get_exchange_rate(currency_from='USD', currency_to='EUR'). "
        "Do not attempt to answer currency questions without first calling the tool. "
        "Use the tool first, then format a proper response with the results."
    )

    def __init__(self):
        # Use synchronous tool wrappers instead of async MCP tools
        self.tools = _create_sync_mcp_tools()
        
        print(f"DEBUG: Loaded {len(self.tools)} sync MCP wrapper tools")
        for tool in self.tools:
            print(f"DEBUG: Tool name: {tool.name if hasattr(tool, 'name') else 'no name'}")
            print(f"DEBUG: Tool description: {tool.description if hasattr(tool, 'description') else 'no description'}")
            print(f"DEBUG: Tool args: {tool.args if hasattr(tool, 'args') else 'no args'}")
            print(f"DEBUG: Full tool: {str(tool)[:200]}...")

        self.model = ChatOpenAI(model="gpt-4o-mini", temperature=0)
        
        self.graph = create_react_agent(
            self.model,
            tools=self.tools,
            checkpointer=memory,
            prompt=self.SYSTEM_INSTRUCTION,
        )

    async def invoke_async(self, query, sessionId) -> dict:
        """Async version of invoke method for proper MCP tool handling"""
        print(f"DEBUG: ASYNC INVOKE method called with query: {query}")
        
        result_items = []
        async for item in self.stream(query, sessionId):
            result_items.append(item)
            if item.get("is_task_complete", False):
                return item
        # Return the last item if no completion found
        return result_items[-1] if result_items else {
            "is_task_complete": False,
            "require_user_input": True,
            "content": "Unable to process request"
        }
    
    def invoke(self, query, sessionId) -> str:
        """Sync wrapper for backward compatibility - delegates to async version"""
        print(f"DEBUG: SYNC INVOKE wrapper called with query: {query}")
        # This should only be called in sync contexts - for task manager, use invoke_async directly
        import asyncio
        try:
            loop = asyncio.get_running_loop()
            # We're in an async context, this shouldn't be called
            raise RuntimeError("invoke() called from async context - use invoke_async() instead")
        except RuntimeError:
            # No event loop running, we can create one
            return asyncio.run(self.invoke_async(query, sessionId))

    async def stream(self, query, sessionId) -> AsyncIterable[Dict[str, Any]]:
        inputs = {"messages": [("user", query)]}
        config = {"configurable": {"thread_id": sessionId}, "recursion_limit": 100}

        # Start notification
        notify_websocket("start", "current", f"Starting query: {query}")
        notify_websocket("agent", "current", "Agent analyzing query...")

        print(f"DEBUG: Starting stream for query: {query}")
        step_count = 0

        for item in self.graph.stream(inputs, config, stream_mode="values"):
            step_count += 1
            print(f"DEBUG: Stream step {step_count}")
            print(f"DEBUG: Item keys: {list(item.keys())}")
            
            if "messages" in item:
                messages = item["messages"]
                print(f"DEBUG: Got {len(messages)} messages")
                if messages:
                    last_message = messages[-1]
                    print(f"DEBUG: Last message type: {type(last_message)}")
                    print(f"DEBUG: Last message content: {str(last_message)[:200]}...")
                    
                    if (
                        isinstance(last_message, AIMessage)
                        and hasattr(last_message, 'tool_calls')
                        and last_message.tool_calls
                        and len(last_message.tool_calls) > 0
                    ):
                        print(f"DEBUG: Found tool calls: {last_message.tool_calls}")
                        notify_websocket("agent", "completed", "Agent decided to call tools")
                        notify_websocket("tools", "current", "Calling get_exchange_rate tool...")
                        yield {
                            "is_task_complete": False,
                            "require_user_input": False,
                            "content": "Looking up the exchange rates...",
                        }
                    elif isinstance(last_message, ToolMessage):
                        print(f"DEBUG: Found tool message: {last_message}")
                        notify_websocket("tools", "completed", "Tool result received")
                        notify_websocket("agent", "current", "Agent processing tool results...")
                        yield {
                            "is_task_complete": False,
                            "require_user_input": False,
                            "content": "Processing the exchange rates..",
                        }

        print(f"DEBUG: Stream completed after {step_count} steps")
        notify_websocket("response", "current", "Generating structured response...")
        result = self.get_agent_response(config)
        notify_websocket("response", "completed", "Response generated")
        notify_websocket("end", "completed", "Execution completed")
        yield result

    def get_agent_response(self, config):
        current_state = self.graph.get_state(config)
        print(f"DEBUG: Current state in get_agent_response: {current_state.values.keys()}")
        
        # Check for structured_response first (if it exists)
        structured_response = current_state.values.get("structured_response")
        if structured_response and isinstance(structured_response, ResponseFormat):
            if structured_response.status == "input_required":
                return {
                    "is_task_complete": False,
                    "require_user_input": True,
                    "content": structured_response.message,
                }
            elif structured_response.status == "error":
                return {
                    "is_task_complete": False,
                    "require_user_input": True,
                    "content": structured_response.message,
                }
            elif structured_response.status == "completed":
                return {
                    "is_task_complete": True,
                    "require_user_input": False,
                    "content": structured_response.message,
                }
        
        # If no structured_response, check the messages for final AI response
        messages = current_state.values.get("messages", [])
        if messages:
            # Get the last AI message
            last_ai_message = None
            for msg in reversed(messages):
                if hasattr(msg, 'content') and hasattr(msg, '__class__') and 'AIMessage' in str(msg.__class__):
                    last_ai_message = msg
                    break
            
            if last_ai_message and last_ai_message.content:
                print(f"DEBUG: Found final AI message: {last_ai_message.content[:200]}...")
                return {
                    "is_task_complete": True,
                    "require_user_input": False,
                    "content": last_ai_message.content,
                }

        print("DEBUG: No usable response found, returning default error")
        return {
            "is_task_complete": False,
            "require_user_input": True,
            "content": "We are unable to process your request at the moment. Please try again.",
        }

    SUPPORTED_CONTENT_TYPES = ["text", "text/plain"]
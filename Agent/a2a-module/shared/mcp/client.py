"""
MCP Proxy Client for A2A Worker Agents
Provides easy access to MCP tools via HTTP proxy
"""
import requests
import json
from typing import Dict, Any, Optional
import time

class MCPClient:
    """Client for accessing MCP tools through the proxy server"""
    
    def __init__(self, proxy_host: str = "localhost", proxy_port: int = 8000):
        self.base_url = f"http://{proxy_host}:{proxy_port}"
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})
    
    def _make_request(self, endpoint: str, data: dict) -> dict:
        """Make HTTP request to MCP proxy server"""
        try:
            url = f"{self.base_url}{endpoint}"
            response = self.session.post(url, json=data, timeout=30)
            response.raise_for_status()
            
            result = response.json()
            if result.get("success"):
                return result.get("data", {})
            else:
                raise Exception(f"MCP Proxy Error: {result.get('error')}")
                
        except requests.exceptions.RequestException as e:
            raise Exception(f"MCP Proxy Connection Error: {str(e)}")
        except Exception as e:
            raise Exception(f"MCP Proxy Error: {str(e)}")
    
    def sequential_thinking(
        self, 
        thought: str,
        thought_number: int = 1,
        total_thoughts: int = 5,
        next_thought_needed: bool = True,
        is_revision: bool = False,
        revises_thought: Optional[int] = None,
        branch_from_thought: Optional[int] = None,
        branch_id: Optional[str] = None,
        needs_more_thoughts: bool = False
    ) -> dict:
        """Call sequential thinking MCP tool"""
        data = {
            "thought": thought,
            "thought_number": thought_number,
            "total_thoughts": total_thoughts,
            "next_thought_needed": next_thought_needed,
            "is_revision": is_revision,
            "revises_thought": revises_thought,
            "branch_from_thought": branch_from_thought,
            "branch_id": branch_id,
            "needs_more_thoughts": needs_more_thoughts
        }
        return self._make_request("/mcp/sequential-thinking", data)
    
    def context7_resolve(self, library_name: str) -> dict:
        """Resolve library ID using Context7"""
        data = {"library_name": library_name}
        return self._make_request("/mcp/context7/resolve", data)
    
    def context7_docs(
        self, 
        context7_compatible_library_id: str,
        tokens: int = 5000,
        topic: Optional[str] = None
    ) -> dict:
        """Get library documentation using Context7"""
        data = {
            "context7_compatible_library_id": context7_compatible_library_id,
            "tokens": tokens,
            "topic": topic
        }
        return self._make_request("/mcp/context7/docs", data)
    
    def health_check(self) -> dict:
        """Check MCP proxy server health"""
        try:
            response = self.session.get(f"{self.base_url}/mcp/health", timeout=5)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            raise Exception(f"MCP Proxy Health Check Failed: {str(e)}")
    
    def clear_cache(self) -> dict:
        """Clear MCP proxy cache"""
        try:
            response = self.session.get(f"{self.base_url}/mcp/cache/clear", timeout=5)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            raise Exception(f"MCP Proxy Cache Clear Failed: {str(e)}")

# Convenience functions for easy import
def get_mcp_client() -> MCPClient:
    """Get a configured MCP client instance"""
    return MCPClient()

def sequential_thinking(thought: str, **kwargs) -> dict:
    """Quick access to sequential thinking"""
    client = get_mcp_client()
    return client.sequential_thinking(thought, **kwargs)

def resolve_library(library_name: str) -> dict:
    """Quick access to library resolution"""
    client = get_mcp_client()
    return client.context7_resolve(library_name)

def get_docs(library_id: str, topic: Optional[str] = None, tokens: int = 5000) -> dict:
    """Quick access to documentation"""
    client = get_mcp_client()
    return client.context7_docs(library_id, tokens, topic)

def intelligent_query_analysis(query: str, agent_domain: str) -> dict:
    """
    Intelligently analyze query and determine what MCP tools to use
    Returns enhancement suggestions based on query complexity and content
    """
    client = get_mcp_client()
    
    # Use sequential thinking to analyze what the query needs
    analysis = client.sequential_thinking(
        f"As a {agent_domain} expert, analyze this query: '{query}'. "
        f"What libraries, frameworks, or documentation would be helpful? "
        f"Is this complex enough to need deep analysis? "
        f"What specific technical topics are involved?",
        thought_number=1,
        total_thoughts=3
    )
    
    return {
        "needs_documentation": len(query.split()) > 5,
        "needs_deep_analysis": any(word in query.lower() for word in ["design", "architecture", "implement", "create", "build", "system"]),
        "suggested_libraries": [],  # Let the thinking process determine this
        "analysis": analysis,
        "complexity_score": min(len(query.split()) / 10.0, 1.0)
    }

# Test function
if __name__ == "__main__":
    print("Testing MCP Client...")
    
    try:
        client = get_mcp_client()
        
        # Test health check
        health = client.health_check()
        print(f"Health check: {health}")
        
        # Test sequential thinking
        thinking_result = client.sequential_thinking(
            "How can I optimize this A2A multi-agent system?"
        )
        print(f"Sequential thinking: {thinking_result}")
        
        # Test library resolution
        resolve_result = client.context7_resolve("React")
        print(f"Library resolve: {resolve_result}")
        
        # Test documentation
        docs_result = client.context7_docs("/facebook/react", topic="hooks")
        print(f"Documentation: {docs_result}")
        
        print("All MCP Client tests passed!")
        
    except Exception as e:
        print(f"MCP Client test failed: {str(e)}")
        print("Make sure MCP Proxy Server is running on port 8000")
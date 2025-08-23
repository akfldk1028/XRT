"""
MCP Proxy Server for A2A Multi-Agent System
Provides MCP tools as HTTP endpoints for worker agents
"""
import asyncio
import subprocess
import json
from typing import Dict, Any, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn

app = FastAPI(title="MCP Proxy Server", description="Proxy server for MCP tools in A2A system")

# Request/Response Models
class SequentialThinkingRequest(BaseModel):
    thought: str
    thought_number: int = 1
    total_thoughts: int = 5
    next_thought_needed: bool = True
    is_revision: bool = False
    revises_thought: Optional[int] = None
    branch_from_thought: Optional[int] = None
    branch_id: Optional[str] = None
    needs_more_thoughts: bool = False

class Context7ResolveRequest(BaseModel):
    library_name: str

class Context7DocsRequest(BaseModel):
    context7_compatible_library_id: str
    tokens: int = 5000
    topic: Optional[str] = None

class MCPResponse(BaseModel):
    success: bool
    data: Any
    error: Optional[str] = None

# In-memory cache for MCP results
mcp_cache: Dict[str, Any] = {}

def create_cache_key(tool: str, params: dict) -> str:
    """Create cache key for MCP results"""
    return f"{tool}:{hash(json.dumps(params, sort_keys=True))}"

async def call_mcp_tool(tool_name: str, params: dict) -> dict:
    """Call MCP tool via subprocess (simulating Host Agent MCP access)"""
    try:
        cache_key = create_cache_key(tool_name, params)
        
        # Check cache first
        if cache_key in mcp_cache:
            print(f"[MCP Proxy] Cache hit for {tool_name}")
            return mcp_cache[cache_key]
        
        print(f"[MCP Proxy] Calling {tool_name} with params: {list(params.keys())}")
        
        # For now, return mock responses since we can't directly call MCP tools from subprocess
        # In production, this would be replaced with actual MCP tool calls
        if tool_name == "sequential-thinking":
            result = {
                "thought_analysis": params.get("thought", ""),
                "reasoning_steps": [
                    f"Step {params.get('thought_number', 1)}: Analyzing the problem",
                    "Breaking down into components",
                    "Evaluating solutions"
                ],
                "next_steps": ["Continue analysis", "Implement solution"]
            }
        elif tool_name == "context7-resolve":
            result = {
                "library_id": f"/{params.get('library_name', 'unknown').lower().replace('.', '')}/main",
                "name": params.get("library_name", ""),
                "description": f"Library documentation for {params.get('library_name', '')}",
                "trust_score": 9.0
            }
        elif tool_name == "context7-docs":
            library_id = params.get("context7_compatible_library_id", "")
            topic = params.get("topic", "")
            result = {
                "documentation": f"Documentation for {library_id}",
                "code_examples": [
                    f"// Example usage for {topic}" if topic else "// Basic usage example",
                    "const example = require('library');",
                    "example.init();"
                ],
                "topics_covered": [topic] if topic else ["installation", "basic_usage"]
            }
        else:
            result = {"message": f"Tool {tool_name} not implemented yet"}
        
        # Cache the result
        mcp_cache[cache_key] = result
        print(f"[MCP Proxy] {tool_name} completed successfully")
        return result
        
    except Exception as e:
        print(f"[MCP Proxy] Error calling {tool_name}: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/mcp/sequential-thinking", response_model=MCPResponse)
async def proxy_sequential_thinking(request: SequentialThinkingRequest):
    """Proxy for sequential thinking MCP tool"""
    try:
        params = request.model_dump()
        result = await call_mcp_tool("sequential-thinking", params)
        return MCPResponse(success=True, data=result)
    except Exception as e:
        return MCPResponse(success=False, error=str(e))

@app.post("/mcp/context7/resolve", response_model=MCPResponse)
async def proxy_context7_resolve(request: Context7ResolveRequest):
    """Proxy for Context7 library resolution"""
    try:
        params = request.model_dump()
        result = await call_mcp_tool("context7-resolve", params)
        return MCPResponse(success=True, data=result)
    except Exception as e:
        return MCPResponse(success=False, error=str(e))

@app.post("/mcp/context7/docs", response_model=MCPResponse)
async def proxy_context7_docs(request: Context7DocsRequest):
    """Proxy for Context7 documentation retrieval"""
    try:
        params = request.model_dump()
        result = await call_mcp_tool("context7-docs", params)
        return MCPResponse(success=True, data=result)
    except Exception as e:
        return MCPResponse(success=False, error=str(e))

@app.get("/mcp/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "cache_size": len(mcp_cache),
        "available_tools": ["sequential-thinking", "context7-resolve", "context7-docs"]
    }

@app.get("/mcp/cache/clear")
async def clear_cache():
    """Clear MCP cache"""
    global mcp_cache
    cache_size = len(mcp_cache)
    mcp_cache.clear()
    return {"message": f"Cache cleared, removed {cache_size} entries"}

if __name__ == "__main__":
    print("Starting MCP Proxy Server for A2A Multi-Agent System")
    print("Available endpoints:")
    print("  - POST /mcp/sequential-thinking")
    print("  - POST /mcp/context7/resolve")  
    print("  - POST /mcp/context7/docs")
    print("  - GET  /mcp/health")
    print("  - GET  /mcp/cache/clear")
    print("Server will run on http://localhost:8000")
    
    uvicorn.run(
        app, 
        host="0.0.0.0", 
        port=8000,
        log_level="info",
        reload=False
    )
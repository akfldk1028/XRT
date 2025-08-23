#!/usr/bin/env python3
"""
Start all A2A Worker Agents for testing
"""
import subprocess
import sys
import time
import signal
import os
from pathlib import Path
import httpx
import asyncio

# Agent configurations
AGENTS = [
    {
        "name": "Frontend Agent",
        "path": "agents/claude_cli/frontend",
        "script": "server.py",
        "port": 8010,
        "color": "\033[94m"  # Blue
    },
    {
        "name": "Backend Agent",
        "path": "agents/claude_cli/backend",
        "script": "server.py",
        "port": 8021,
        "color": "\033[92m"  # Green
    },
    {
        "name": "Unity Agent",
        "path": "agents/claude_cli/unity",
        "script": "server.py",
        "port": 8012,
        "color": "\033[93m"  # Yellow
    }
]

RESET_COLOR = "\033[0m"

processes = []


def signal_handler(sig, frame):
    """Handle Ctrl+C to stop all agents"""
    print("\n\nStopping all agents...")
    for proc in processes:
        try:
            proc.terminate()
            proc.wait(timeout=5)
        except:
            proc.kill()
    sys.exit(0)


async def check_agent_health(port: int, name: str) -> bool:
    """Check if an agent is responding"""
    async with httpx.AsyncClient(timeout=2.0) as client:
        try:
            response = await client.get(f"http://localhost:{port}/.well-known/agent.json")
            if response.status_code == 200:
                return True
        except:
            pass
    return False


async def wait_for_agents():
    """Wait for all agents to be ready"""
    print("\nWaiting for agents to start...")
    max_attempts = 30  # 30 seconds timeout
    
    for attempt in range(max_attempts):
        all_ready = True
        statuses = []
        
        for agent in AGENTS:
            is_ready = await check_agent_health(agent["port"], agent["name"])
            statuses.append((agent["name"], is_ready))
            if not is_ready:
                all_ready = False
                
        # Print status
        print(f"\rAttempt {attempt + 1}/{max_attempts}: ", end="")
        for name, ready in statuses:
            symbol = "✅" if ready else "⏳"
            print(f"{symbol} {name}  ", end="")
            
        if all_ready:
            print("\n\n✅ All agents are ready!")
            return True
            
        await asyncio.sleep(1)
        
    print("\n\n❌ Some agents failed to start")
    return False


def start_agents():
    """Start all A2A worker agents"""
    signal.signal(signal.SIGINT, signal_handler)
    
    print("=" * 70)
    print("A2A MULTI-AGENT SYSTEM LAUNCHER")
    print("=" * 70)
    print("\nStarting all worker agents...")
    print("Press Ctrl+C to stop all agents\n")
    
    base_dir = Path(__file__).parent
    
    for agent in AGENTS:
        agent_dir = base_dir / agent["path"]
        agent_script = agent_dir / agent["script"]
        
        if not agent_script.exists():
            print(f"❌ {agent['name']} script not found: {agent_script}")
            continue
            
        print(f"Starting {agent['name']} on port {agent['port']}...")
        
        # Start the agent process
        env = os.environ.copy()
        env["PYTHONUNBUFFERED"] = "1"  # Ensure output is not buffered
        
        if sys.platform == "win32":
            # Windows: Use CREATE_NEW_PROCESS_GROUP for better process management
            proc = subprocess.Popen(
                [sys.executable, str(agent_script)],
                cwd=str(agent_dir),
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
            )
        else:
            # Unix-like systems
            proc = subprocess.Popen(
                [sys.executable, str(agent_script)],
                cwd=str(agent_dir),
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                preexec_fn=os.setsid
            )
            
        processes.append(proc)
        
        # Read initial output to confirm startup
        for _ in range(5):
            line = proc.stdout.readline()
            if line:
                print(f"  {agent['color']}{line.strip()}{RESET_COLOR}")
                
    # Check if agents are ready
    asyncio.run(wait_for_agents())
    
    print("\n" + "=" * 70)
    print("All agents are running!")
    print("=" * 70)
    print("\nYou can now:")
    print("1. Run tests: python test_a2a_protocol_compliance.py")
    print("2. Use A2A Inspector: Open a2a-inspector/frontend/public/index.html")
    print("3. Send requests to agents:")
    print("   - Frontend: http://localhost:8010")
    print("   - Backend: http://localhost:8021")
    print("   - Unity: http://localhost:8012")
    print("\nAgent logs are being captured. Press Ctrl+C to stop all agents.")
    print("=" * 70)
    
    # Monitor agent outputs
    try:
        while True:
            for i, proc in enumerate(processes):
                if proc.poll() is not None:
                    # Agent crashed
                    agent = AGENTS[i]
                    print(f"\n❌ {agent['name']} has stopped unexpectedly!")
                    
                # Read and display output
                line = proc.stdout.readline()
                if line:
                    agent = AGENTS[i]
                    print(f"{agent['color']}[{agent['name']}] {line.strip()}{RESET_COLOR}")
                    
            time.sleep(0.1)
            
    except KeyboardInterrupt:
        signal_handler(None, None)


if __name__ == "__main__":
    start_agents()
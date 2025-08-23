"""
Vision Agent A2A Server  
Runs on port 8031
"""
import os
import sys
import json
import time
from datetime import datetime
from pathlib import Path

# Add parent directories to path
sys.path.append(str(Path(__file__).parent.parent.parent.parent))

from shared.server import A2AServer
from shared.custom_types import AgentCard, AgentCapabilities, AgentSkill
from shared.push_notification_auth import PushNotificationSenderAuth

# Import the CLI task manager
sys.path.append(str(Path(__file__).parent.parent))
from base_cli_task_manager import CLIAgentTaskManager

# Import agent from current directory
sys.path.append(str(Path(__file__).parent))
from agent import VisionCLIAgent


def create_vision_agent_card() -> AgentCard:
    """Create the agent card for Vision Agent"""
    return AgentCard(
        url="http://localhost:8031",
        name="Vision Agent - VLM/LLM Processing Expert",
        description="Expert in Vision Language Models, LLM integration, and intelligent analysis of visual data for Android XR applications. Creates projects in separate project folders.",
        version="1.0.0",
        capabilities=AgentCapabilities(
            streaming=True,
            pushNotifications=True,
            stateTransitionHistory=True
        ),
        skills=[
            AgentSkill(
                id="vlm_processing",
                name="Vision Language Model Processing",
                description="Analyze images using Vision Language Models for scene understanding",
                tags=["vlm", "gpt-4v", "vision", "multimodal", "analysis"],
                examples=["Analyze XR scene with GPT-4V", "Process camera image with Moondream VLM"]
            ),
            AgentSkill(
                id="llm_integration", 
                name="LLM Integration & Processing",
                description="Process vision results with Large Language Models for intelligent responses",
                tags=["llm", "openai", "claude", "gemini", "reasoning"],
                examples=["Generate intelligent response from vision analysis", "Create context-aware AI responses"]
            ),
            AgentSkill(
                id="realtime_analysis",
                name="Real-time Vision Analysis",
                description="Provide low-latency AI responses for XR interaction and real-time processing",
                tags=["realtime", "streaming", "performance", "xr", "optimization"],
                examples=["Create real-time image analysis pipeline", "Implement low-latency vision processing"]
            )
        ]
    )


def main():
    """Start the Vision Agent A2A server"""
    print("[Vision Agent] Initializing Claude CLI-based Vision Agent...")
    print("[Vision Agent] Projects will be created in: projects/[PROJECT_NAME]/android_xr/vision/")
    
    # Create agent instance
    agent = VisionCLIAgent()
    
    # Create task manager
    notification_auth = PushNotificationSenderAuth()
    task_manager = CLIAgentTaskManager(agent, notification_auth)
    
    # Create agent card
    agent_card = create_vision_agent_card()
    
    # Create and start server
    server = A2AServer(
        host="0.0.0.0",
        port=8031,
        endpoint="/",
        agent_card=agent_card,
        task_manager=task_manager
    )
    
    print("[Vision Agent] Starting A2A server on port 8031...")
    print("[Vision Agent] Agent card available at: http://localhost:8031/.well-known/agent.json")
    print("[Vision Agent] Ready to receive vision processing tasks via A2A protocol")
    print("[Vision Agent] Using Claude CLI for response generation")
    
    server.start()


if __name__ == "__main__":
    main()
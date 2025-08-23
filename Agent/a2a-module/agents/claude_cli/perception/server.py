"""
Perception Agent A2A Server  
Runs on port 8030
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
from agent import PerceptionCLIAgent


def create_perception_agent_card() -> AgentCard:
    """Create the agent card for Perception Agent"""
    return AgentCard(
        url="http://localhost:8030",
        name="Perception Agent - Camera & ROI Processing",
        description="Expert in camera frame acquisition, ROI processing, and real-time image preprocessing for Android XR applications. Creates projects in separate project folders.",
        version="1.0.0",
        capabilities=AgentCapabilities(
            streaming=True,
            pushNotifications=True,
            stateTransitionHistory=True
        ),
        skills=[
            AgentSkill(
                id="camera_processing",
                name="Camera Processing & Management",
                description="Initialize, configure, and manage camera streams for XR applications",
                tags=["camera", "android", "xr", "camera2", "camerax"],
                examples=["Create Camera2 API initialization for Android XR", "Implement CameraX preview with ROI extraction"]
            ),
            AgentSkill(
                id="roi_processing", 
                name="ROI Processing & Optimization",
                description="Extract and process specific regions from camera frames",
                tags=["roi", "image-processing", "opencv", "performance"],
                examples=["Implement ROI cropping pipeline", "Create GPU-accelerated frame processing"]
            ),
            AgentSkill(
                id="xr_integration",
                name="XR Camera Integration",
                description="Integrate camera processing with XR environments and HUD systems",
                tags=["xr", "passthrough", "openxr", "hud", "realtime"],
                examples=["Create passthrough camera integration", "Implement camera-to-HUD coordinate mapping"]
            )
        ]
    )


def main():
    """Start the Perception Agent A2A server"""
    print("[Perception Agent] Initializing Claude CLI-based Perception Agent...")
    print("[Perception Agent] Projects will be created in: projects/[PROJECT_NAME]/android_xr/perception/")
    
    # Create agent instance
    agent = PerceptionCLIAgent()
    
    # Create task manager
    notification_auth = PushNotificationSenderAuth()
    task_manager = CLIAgentTaskManager(agent, notification_auth)
    
    # Create agent card
    agent_card = create_perception_agent_card()
    
    # Create and start server
    server = A2AServer(
        host="0.0.0.0",
        port=8030,
        endpoint="/",
        agent_card=agent_card,
        task_manager=task_manager
    )
    
    print("[Perception Agent] Starting A2A server on port 8030...")
    print("[Perception Agent] Agent card available at: http://localhost:8030/.well-known/agent.json")
    print("[Perception Agent] Ready to receive camera processing tasks via A2A protocol")
    print("[Perception Agent] Using Claude CLI for response generation")
    
    server.start()


if __name__ == "__main__":
    main()
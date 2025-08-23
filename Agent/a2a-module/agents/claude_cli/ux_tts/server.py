"""
UX/TTS Agent A2A Server  
Runs on port 8032
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
from agent import UXTTSCLIAgent


def create_ux_tts_agent_card() -> AgentCard:
    """Create the agent card for UX/TTS Agent"""
    return AgentCard(
        url="http://localhost:8032",
        name="UX/TTS Agent - UI/Audio Output Expert",
        description="Expert in HUD display, TTS voice output, XR UI interaction, and audio feedback systems for Android XR applications. Creates projects in separate project folders.",
        version="1.0.0",
        capabilities=AgentCapabilities(
            streaming=True,
            pushNotifications=True,
            stateTransitionHistory=True
        ),
        skills=[
            AgentSkill(
                id="hud_management",
                name="HUD Display Management",
                description="Create and manage crosshair displays, overlay UI, and real-time visual feedback",
                tags=["hud", "crosshair", "overlay", "xr-ui", "display"],
                examples=["Create crosshair overlay for XR glasses", "Implement dynamic HUD with real-time content"]
            ),
            AgentSkill(
                id="tts_processing", 
                name="Text-to-Speech Processing",
                description="Convert AI responses to natural speech with optimal voice settings",
                tags=["tts", "voice", "speech", "android-tts", "coqui"],
                examples=["Setup Android TTS for voice feedback", "Implement real-time TTS pipeline"]
            ),
            AgentSkill(
                id="xr_ui_interaction",
                name="XR UI Interaction Design",
                description="Design XR-optimized user interfaces and interaction patterns",
                tags=["xr", "ui", "interaction", "gesture", "gaze"],
                examples=["Create gaze-based menu system", "Design gesture-responsive UI components"]
            ),
            AgentSkill(
                id="audio_feedback",
                name="Audio Feedback Systems",
                description="Manage spatial audio, voice guidance, and contextual audio cues",
                tags=["audio", "spatial", "feedback", "3d-sound", "guidance"],
                examples=["Implement spatial audio feedback", "Create contextual audio navigation cues"]
            )
        ]
    )


def main():
    """Start the UX/TTS Agent A2A server"""
    print("[UX/TTS Agent] Initializing Claude CLI-based UX/TTS Agent...")
    print("[UX/TTS Agent] Projects will be created in: projects/[PROJECT_NAME]/android_xr/ux_tts/")
    
    # Create agent instance
    agent = UXTTSCLIAgent()
    
    # Create task manager
    notification_auth = PushNotificationSenderAuth()
    task_manager = CLIAgentTaskManager(agent, notification_auth)
    
    # Create agent card
    agent_card = create_ux_tts_agent_card()
    
    # Create and start server
    server = A2AServer(
        host="0.0.0.0",
        port=8032,
        endpoint="/",
        agent_card=agent_card,
        task_manager=task_manager
    )
    
    print("[UX/TTS Agent] Starting A2A server on port 8032...")
    print("[UX/TTS Agent] Agent card available at: http://localhost:8032/.well-known/agent.json")
    print("[UX/TTS Agent] Ready to receive UX/TTS processing tasks via A2A protocol")
    print("[UX/TTS Agent] Using Claude CLI for response generation")
    
    server.start()


if __name__ == "__main__":
    main()
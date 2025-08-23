"""
Logger/Metrics Agent A2A Server  
Runs on port 8033
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
from agent import LoggerCLIAgent


def create_logger_agent_card() -> AgentCard:
    """Create the agent card for Logger/Metrics Agent"""
    return AgentCard(
        url="http://localhost:8033",
        name="Logger/Metrics Agent - Logging & Monitoring Expert",
        description="Expert in performance monitoring, user analytics, error tracking, and comprehensive logging for Android XR applications. Creates projects in separate project folders.",
        version="1.0.0",
        capabilities=AgentCapabilities(
            streaming=True,
            pushNotifications=True,
            stateTransitionHistory=True
        ),
        skills=[
            AgentSkill(
                id="performance_monitoring",
                name="Performance Monitoring",
                description="Track frame rates, latency, memory usage, and system performance metrics",
                tags=["performance", "fps", "latency", "memory", "monitoring"],
                examples=["Monitor XR frame rate and rendering performance", "Track AI inference processing times"]
            ),
            AgentSkill(
                id="user_analytics", 
                name="User Behavior Analytics",
                description="Collect user interaction patterns, session data, and behavior metrics",
                tags=["analytics", "user-behavior", "sessions", "interactions", "heatmap"],
                examples=["Track user interaction patterns in XR", "Generate session analytics and heatmaps"]
            ),
            AgentSkill(
                id="error_tracking",
                name="Error & Crash Tracking",
                description="Monitor crashes, exceptions, and error recovery patterns",
                tags=["errors", "crashes", "exceptions", "debugging", "recovery"],
                examples=["Setup crash reporting for XR application", "Monitor and categorize API failures"]
            ),
            AgentSkill(
                id="logging_infrastructure",
                name="Logging Infrastructure",
                description="Setup comprehensive logging systems with Timber, file rotation, and structured data",
                tags=["logging", "timber", "files", "structured", "export"],
                examples=["Setup Timber logging with file rotation", "Create structured logging for A2A communication"]
            )
        ]
    )


def main():
    """Start the Logger/Metrics Agent A2A server"""
    print("[Logger Agent] Initializing Claude CLI-based Logger/Metrics Agent...")
    print("[Logger Agent] Projects will be created in: projects/[PROJECT_NAME]/android_xr/logger/")
    
    # Create agent instance
    agent = LoggerCLIAgent()
    
    # Create task manager
    notification_auth = PushNotificationSenderAuth()
    task_manager = CLIAgentTaskManager(agent, notification_auth)
    
    # Create agent card
    agent_card = create_logger_agent_card()
    
    # Create and start server
    server = A2AServer(
        host="0.0.0.0",
        port=8033,
        endpoint="/",
        agent_card=agent_card,
        task_manager=task_manager
    )
    
    print("[Logger Agent] Starting A2A server on port 8033...")
    print("[Logger Agent] Agent card available at: http://localhost:8033/.well-known/agent.json")
    print("[Logger Agent] Ready to receive logging/metrics processing tasks via A2A protocol")
    print("[Logger Agent] Using Claude CLI for response generation")
    
    server.start()


if __name__ == "__main__":
    main()
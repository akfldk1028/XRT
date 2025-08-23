#!/usr/bin/env python3
"""
Quick A2A Protocol Test
"""
import requests
import json
import time

def test_agent_cards():
    """Test agent card endpoints"""
    agents = {
        "Frontend": "http://localhost:8010",
        "Backend": "http://localhost:8021", 
        "Unity": "http://localhost:8012"
    }
    
    print("=== Agent Card Test ===")
    for name, url in agents.items():
        try:
            response = requests.get(f"{url}/.well-known/agent-card.json", timeout=5)
            if response.status_code == 200:
                card = response.json()
                print(f"{name}: OK - {card.get('name', 'Unknown')}")
            else:
                print(f"{name}: ERROR - HTTP {response.status_code}")
        except Exception as e:
            print(f"{name}: ERROR - {str(e)}")

def test_direct_communication():
    """Test direct agent communication"""
    print("\n=== Direct Communication Test ===")
    
    # Test Frontend Agent
    message = {
        "jsonrpc": "2.0",
        "id": "test1",
        "method": "message/send",
        "params": {
            "message": {
                "messageId": "msg1",
                "taskId": "task1",
                "contextId": "test",
                "parts": [{"kind": "text", "text": "Please respond with your agent type and confirm you are operational."}]
            }
        }
    }
    
    try:
        response = requests.post("http://localhost:8010/", json=message, timeout=30)
        if response.status_code == 200:
            result = response.json()
            print("Frontend Agent: RESPONDING")
            if "result" in result:
                print("  JSON-RPC format: OK")
        else:
            print(f"Frontend Agent: ERROR - HTTP {response.status_code}")
    except Exception as e:
        print(f"Frontend Agent: ERROR - {str(e)}")

    # Test Backend Agent
    try:
        response = requests.post("http://localhost:8021/", json=message, timeout=30)
        if response.status_code == 200:
            result = response.json()
            print("Backend Agent: RESPONDING")
            if "result" in result:
                print("  JSON-RPC format: OK")
        else:
            print(f"Backend Agent: ERROR - HTTP {response.status_code}")
    except Exception as e:
        print(f"Backend Agent: ERROR - {str(e)}")

if __name__ == "__main__":
    test_agent_cards()
    test_direct_communication()
    print("\n=== Test Complete ===")
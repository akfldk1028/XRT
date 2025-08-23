#!/usr/bin/env python3
"""
Full A2A Test Suite
Comprehensive testing of the A2A multi-agent system
"""
import asyncio
import json
import time
import httpx
import sys
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Any

# Add parent directory to path
sys.path.append(str(Path(__file__).parent))

from shared.custom_types import A2AMessage, A2ATextPart
from shared.a2a_conversation_logger import get_conversation_logger

# Test configuration
AGENTS = {
    "frontend": {"port": 8010, "name": "Frontend Agent"},
    "backend": {"port": 8021, "name": "Backend Agent"},
    "unity": {"port": 8012, "name": "Unity Agent"}
}

# Initialize logger
logger = get_conversation_logger()


class FullA2ATestSuite:
    """Comprehensive test suite for A2A system"""
    
    def __init__(self):
        self.client = httpx.AsyncClient(timeout=60.0)
        self.test_results = []
        self.start_time = time.time()
        
    async def test_basic_functionality(self):
        """Test 1: Basic agent functionality"""
        print("\n" + "="*70)
        print("TEST 1: BASIC AGENT FUNCTIONALITY")
        print("="*70)
        
        for agent_type, config in AGENTS.items():
            print(f"\n[{agent_type.upper()}] Testing basic functionality...")
            
            try:
                # Simple greeting
                await self._send_test_message(
                    agent_type, 
                    "Hello! Please introduce yourself and your capabilities.",
                    f"basic_{agent_type}"
                )
                
                # Domain-specific question
                domain_questions = {
                    "frontend": "What are the best practices for React component design?",
                    "backend": "How do you design a RESTful API for user authentication?",
                    "unity": "How do you implement player movement in Unity?"
                }
                
                await self._send_test_message(
                    agent_type,
                    domain_questions[agent_type],
                    f"domain_{agent_type}"
                )
                
            except Exception as e:
                self._record_failure(f"basic_{agent_type}", str(e))
                
    async def test_worker_collaboration(self):
        """Test 2: Worker-to-Worker collaboration"""
        print("\n" + "="*70)
        print("TEST 2: WORKER-TO-WORKER COLLABORATION")
        print("="*70)
        
        collaboration_tests = [
            {
                "name": "Frontend → Backend API Coordination",
                "initiator": "frontend",
                "task": "Send A2A message to Backend Agent asking them to design a REST API for a todo application. Use the A2A protocol to get their API specification."
            },
            {
                "name": "Backend → Frontend Data Format Coordination", 
                "initiator": "backend",
                "task": "Send A2A message to Frontend Agent asking what data format they prefer for user profile responses. Use A2A protocol for coordination."
            },
            {
                "name": "Unity → Backend Game Services",
                "initiator": "unity", 
                "task": "Send A2A message to Backend Agent asking them to design a leaderboard API for a multiplayer game. Use A2A protocol communication."
            }
        ]
        
        for test in collaboration_tests:
            print(f"\n[COLLABORATION] {test['name']}")
            
            try:
                response = await self._send_test_message(
                    test["initiator"],
                    test["task"],
                    f"collab_{test['name'].lower().replace(' ', '_')}"
                )
                
                if response and "error" not in response:
                    self._record_success(f"collaboration_{test['initiator']}")
                    print(f"  ✅ Collaboration successful")
                else:
                    self._record_failure(f"collaboration_{test['initiator']}", "No valid response")
                    
            except Exception as e:
                self._record_failure(f"collaboration_{test['initiator']}", str(e))
                print(f"  ❌ Collaboration failed: {e}")
                
    async def test_complex_scenarios(self):
        """Test 3: Complex multi-step scenarios"""
        print("\n" + "="*70)
        print("TEST 3: COMPLEX MULTI-STEP SCENARIOS")
        print("="*70)
        
        scenarios = [
            {
                "name": "Full-Stack Task Management App",
                "description": "Coordinate all agents to design a complete task management application",
                "steps": [
                    ("backend", "Design the database schema and API endpoints for a task management system"),
                    ("frontend", "Create React components for a task management UI based on the API design"),
                    ("unity", "Design a gamified UI overlay for the task management system using Unity WebGL")
                ]
            },
            {
                "name": "Real-time Chat Application",
                "description": "Build a real-time chat system with all agents",
                "steps": [
                    ("backend", "Design WebSocket-based real-time messaging API with authentication"),
                    ("frontend", "Create a React chat interface with real-time message updates"),
                    ("unity", "Design a 3D chat room environment using Unity for immersive chat")
                ]
            }
        ]
        
        for scenario in scenarios:
            print(f"\n[SCENARIO] {scenario['name']}")
            print(f"Description: {scenario['description']}")
            
            scenario_results = []
            
            for step_num, (agent_type, task) in enumerate(scenario["steps"], 1):
                print(f"\n  Step {step_num}: {agent_type.upper()}")
                print(f"    Task: {task}")
                
                try:
                    response = await self._send_test_message(
                        agent_type,
                        f"[SCENARIO: {scenario['name']}] {task}",
                        f"scenario_{scenario['name'].lower().replace(' ', '_')}_{agent_type}"
                    )
                    
                    if response and "error" not in response:
                        scenario_results.append(True)
                        print(f"    ✅ Completed successfully")
                    else:
                        scenario_results.append(False)
                        print(f"    ❌ Failed")
                        
                except Exception as e:
                    scenario_results.append(False)
                    print(f"    ❌ Error: {e}")
                    
            # Record scenario result
            if all(scenario_results):
                self._record_success(f"scenario_{scenario['name']}")
                print(f"\n  🎉 Scenario '{scenario['name']}' completed successfully!")
            else:
                self._record_failure(f"scenario_{scenario['name']}", "Some steps failed")
                print(f"\n  ⚠️  Scenario '{scenario['name']}' had failures")
                
    async def test_error_recovery(self):
        """Test 4: Error handling and recovery"""
        print("\n" + "="*70)
        print("TEST 4: ERROR HANDLING AND RECOVERY")
        print("="*70)
        
        error_tests = [
            {
                "name": "Invalid A2A Communication",
                "agent": "frontend",
                "task": "Send A2A message to non-existent agent at http://localhost:9999",
                "expect_error": True
            },
            {
                "name": "Malformed Request Handling",
                "agent": "backend", 
                "task": "Process this completely invalid and malformed request that should trigger error handling",
                "expect_error": False  # Should handle gracefully
            },
            {
                "name": "Timeout Handling",
                "agent": "unity",
                "task": "Perform a very complex calculation that might take a long time and test timeout handling",
                "expect_error": False
            }
        ]
        
        for test in error_tests:
            print(f"\n[ERROR TEST] {test['name']}")
            
            try:
                response = await self._send_test_message(
                    test["agent"],
                    test["task"],
                    f"error_{test['name'].lower().replace(' ', '_')}"
                )
                
                has_error = response is None or "error" in response
                
                if test["expect_error"] == has_error:
                    self._record_success(f"error_{test['name']}")
                    print(f"  ✅ Error handling as expected")
                else:
                    self._record_failure(f"error_{test['name']}", "Unexpected error behavior")
                    print(f"  ❌ Unexpected error behavior")
                    
            except Exception as e:
                if test["expect_error"]:
                    self._record_success(f"error_{test['name']}")
                    print(f"  ✅ Exception caught as expected: {e}")
                else:
                    self._record_failure(f"error_{test['name']}", str(e))
                    print(f"  ❌ Unexpected exception: {e}")
                    
    async def test_performance(self):
        """Test 5: Performance benchmarks"""
        print("\n" + "="*70)
        print("TEST 5: PERFORMANCE BENCHMARKS")
        print("="*70)
        
        performance_tests = [
            {
                "name": "Response Time Test",
                "message": "Hello, respond as quickly as possible",
                "target_ms": 5000  # 5 seconds
            },
            {
                "name": "Concurrent Requests",
                "message": "Handle this concurrent request",
                "concurrent_count": 3
            }
        ]
        
        for test in performance_tests:
            print(f"\n[PERFORMANCE] {test['name']}")
            
            if test["name"] == "Response Time Test":
                # Test response time for each agent
                for agent_type in AGENTS.keys():
                    start_time = time.time()
                    
                    try:
                        await self._send_test_message(
                            agent_type,
                            test["message"],
                            f"perf_response_{agent_type}"
                        )
                        
                        duration_ms = (time.time() - start_time) * 1000
                        print(f"  {agent_type}: {duration_ms:.0f}ms", end="")
                        
                        if duration_ms <= test["target_ms"]:
                            print(" ✅")
                            self._record_success(f"performance_response_{agent_type}")
                        else:
                            print(f" ❌ (>{test['target_ms']}ms)")
                            self._record_failure(f"performance_response_{agent_type}", f"Too slow: {duration_ms}ms")
                            
                    except Exception as e:
                        print(f"  {agent_type}: ❌ {e}")
                        self._record_failure(f"performance_response_{agent_type}", str(e))
                        
            elif test["name"] == "Concurrent Requests":
                # Test concurrent requests to frontend agent
                print(f"  Sending {test['concurrent_count']} concurrent requests to frontend...")
                
                async def send_concurrent_request(i):
                    return await self._send_test_message(
                        "frontend",
                        f"{test['message']} #{i}",
                        f"perf_concurrent_{i}"
                    )
                
                start_time = time.time()
                tasks = [send_concurrent_request(i) for i in range(test["concurrent_count"])]
                results = await asyncio.gather(*tasks, return_exceptions=True)
                duration_ms = (time.time() - start_time) * 1000
                
                successful = sum(1 for r in results if not isinstance(r, Exception))
                print(f"  Completed: {successful}/{test['concurrent_count']} in {duration_ms:.0f}ms")
                
                if successful == test['concurrent_count']:
                    self._record_success("performance_concurrent")
                    print("  ✅ All concurrent requests successful")
                else:
                    self._record_failure("performance_concurrent", f"Only {successful}/{test['concurrent_count']} succeeded")
                    print(f"  ❌ {test['concurrent_count'] - successful} requests failed")
                    
    async def _send_test_message(self, agent_type: str, text: str, test_id: str) -> Dict[str, Any]:
        """Send a test message to an agent"""
        port = AGENTS[agent_type]["port"]
        
        message = A2AMessage(
            role="user",
            parts=[A2ATextPart(kind="text", text=text)],
            messageId=f"{test_id}_{int(time.time())}",
            taskId=f"task_{int(time.time())}",
            contextId=f"test_context_{test_id}",
            kind="message"
        )
        
        request = {
            "jsonrpc": "2.0",
            "id": f"test_{int(time.time())}",
            "method": "message/send",
            "params": {
                "message": message.model_dump(exclude_none=True)
            }
        }
        
        # Log request
        logger.log_request("tester", agent_type, request, port)
        
        # Send request
        start_time = time.time()
        response = await self.client.post(
            f"http://localhost:{port}/",
            json=request,
            headers={"Content-Type": "application/json"}
        )
        duration_ms = (time.time() - start_time) * 1000
        
        if response.status_code == 200:
            result = response.json()
            logger.log_response("tester", agent_type, result, duration_ms)
            return result
        else:
            logger.log_error(f"test_message_{test_id}", f"HTTP {response.status_code}")
            return {"error": f"HTTP {response.status_code}"}
            
    def _record_success(self, test_name: str):
        """Record a successful test"""
        self.test_results.append({
            "test": test_name,
            "status": "PASS",
            "timestamp": datetime.now().isoformat()
        })
        
    def _record_failure(self, test_name: str, reason: str):
        """Record a failed test"""
        self.test_results.append({
            "test": test_name,
            "status": "FAIL", 
            "reason": reason,
            "timestamp": datetime.now().isoformat()
        })
        
    async def run_full_suite(self):
        """Run the complete test suite"""
        print("=" * 70)
        print("A2A MULTI-AGENT SYSTEM - FULL TEST SUITE")
        print("=" * 70)
        print(f"Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("=" * 70)
        
        # Check agent availability
        print("\n[SETUP] Checking agent availability...")
        available_agents = []
        
        for agent_type, config in AGENTS.items():
            try:
                response = await self.client.get(
                    f"http://localhost:{config['port']}/.well-known/agent.json",
                    timeout=2.0
                )
                if response.status_code == 200:
                    print(f"  ✅ {config['name']} is available")
                    available_agents.append(agent_type)
                else:
                    print(f"  ❌ {config['name']} not responding")
            except Exception as e:
                print(f"  ❌ {config['name']} error: {e}")
                
        if len(available_agents) < 2:
            print("\n❌ Need at least 2 agents for full test suite!")
            print("Start agents with: python start_all_agents.py")
            return
            
        # Update AGENTS to only include available ones
        global AGENTS
        AGENTS = {k: v for k, v in AGENTS.items() if k in available_agents}
        
        # Run all test suites
        await self.test_basic_functionality()
        await self.test_worker_collaboration()
        await self.test_complex_scenarios()
        await self.test_error_recovery()
        await self.test_performance()
        
        # Generate final report
        self._generate_final_report()
        
    def _generate_final_report(self):
        """Generate comprehensive test report"""
        total_time = time.time() - self.start_time
        passed = sum(1 for r in self.test_results if r["status"] == "PASS")
        failed = sum(1 for r in self.test_results if r["status"] == "FAIL")
        
        print("\n" + "=" * 70)
        print("FINAL TEST REPORT")
        print("=" * 70)
        
        print(f"\nExecution Time: {total_time:.1f} seconds")
        print(f"Total Tests: {len(self.test_results)}")
        print(f"✅ Passed: {passed}")
        print(f"❌ Failed: {failed}")
        print(f"Success Rate: {(passed / len(self.test_results) * 100):.1f}%")
        
        # Categorize results
        categories = {}
        for result in self.test_results:
            test_name = result["test"]
            category = test_name.split("_")[0]
            if category not in categories:
                categories[category] = {"pass": 0, "fail": 0}
            categories[category][result["status"].lower() if result["status"] == "PASS" else "fail"] += 1
            
        print("\nResults by Category:")
        for category, results in categories.items():
            total = results["pass"] + results["fail"]
            success_rate = (results["pass"] / total * 100) if total > 0 else 0
            print(f"  {category.capitalize()}: {results['pass']}/{total} ({success_rate:.0f}%)")
            
        if failed > 0:
            print("\nFailed Tests:")
            for result in self.test_results:
                if result["status"] == "FAIL":
                    print(f"  ❌ {result['test']}: {result.get('reason', 'Unknown error')}")
                    
        # Overall assessment
        if passed == len(self.test_results):
            print("\n🎉 EXCELLENT! All tests passed!")
            print("Your A2A multi-agent system is fully functional and compliant.")
        elif passed / len(self.test_results) >= 0.8:
            print("\n✅ GOOD! Most tests passed.")
            print("Your A2A system is functional with minor issues to address.")
        elif passed / len(self.test_results) >= 0.6:
            print("\n⚠️ FAIR. Some significant issues detected.")
            print("Your A2A system needs improvements before production use.")
        else:
            print("\n❌ POOR. Major issues detected.")
            print("Your A2A system requires significant fixes.")
            
        # Save detailed report
        report_file = Path(f"full_test_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
        report_data = {
            "timestamp": datetime.now().isoformat(),
            "execution_time_seconds": total_time,
            "summary": {
                "total_tests": len(self.test_results),
                "passed": passed,
                "failed": failed,
                "success_rate": passed / len(self.test_results) * 100
            },
            "categories": categories,
            "detailed_results": self.test_results
        }
        
        with open(report_file, "w") as f:
            json.dump(report_data, f, indent=2)
            
        print(f"\nDetailed report saved to: {report_file}")
        

async def main():
    """Main test runner"""
    tester = FullA2ATestSuite()
    try:
        await tester.run_full_suite()
    finally:
        await tester.client.aclose()
        

if __name__ == "__main__":
    asyncio.run(main())
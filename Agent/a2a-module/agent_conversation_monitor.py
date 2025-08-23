#!/usr/bin/env python3
"""
Worker Agent 간 대화 실시간 모니터링 시스템
사용자가 Claude CLI에서 agent 대화를 실시간으로 볼 수 있도록 함
"""
import asyncio
import httpx
import json
import time
import os
from datetime import datetime
from typing import Dict, List, Optional
from pathlib import Path

class AgentConversationMonitor:
    """Agent 간 대화를 모니터링하고 로깅하는 클래스"""
    
    def __init__(self):
        self.log_dir = Path("agent_conversations")
        self.log_dir.mkdir(exist_ok=True)
        
        self.agents = {
            "frontend": "http://localhost:8010/",
            "backend": "http://localhost:8021/", 
            "unity": "http://localhost:8012/"
        }
        
        self.conversation_log = []
        self.active_conversations = {}
    
    def log_conversation(self, from_agent: str, to_agent: str, message: str, response: str = None):
        """대화 내용을 로그에 기록"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        log_entry = {
            "timestamp": timestamp,
            "from": from_agent,
            "to": to_agent,
            "message": message[:200] + "..." if len(message) > 200 else message,
            "response": response[:200] + "..." if response and len(response) > 200 else response,
            "full_message": message,
            "full_response": response
        }
        
        self.conversation_log.append(log_entry)
        
        # 파일에도 저장
        log_file = self.log_dir / f"conversation_{datetime.now().strftime('%Y%m%d')}.json"
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(json.dumps(log_entry, ensure_ascii=False, indent=2) + "\n")
        
        # 콘솔에 실시간 출력
        print(f"\n📝 [{timestamp}] Agent 대화 기록")
        print(f"📤 {from_agent.upper()} → {to_agent.upper()}")
        print(f"💬 메시지: {log_entry['message']}")
        if response:
            print(f"💬 응답: {log_entry['response']}")
        print("-" * 60)
    
    async def send_agent_message(self, from_agent: str, to_agent: str, message: str) -> str:
        """Agent 간 메시지 전송 및 모니터링"""
        
        if to_agent not in self.agents:
            error = f"알 수 없는 agent: {to_agent}"
            self.log_conversation(from_agent, to_agent, message, error)
            return error
        
        target_url = self.agents[to_agent]
        
        # A2A 메시지 형식
        payload = {
            "jsonrpc": "2.0",
            "id": f"monitor_{int(time.time())}",
            "method": "message/send",
            "params": {
                "message": {
                    "role": "user",
                    "parts": [{"kind": "text", "text": message}],
                    "messageId": f"msg_{int(time.time())}",
                    "taskId": f"task_{int(time.time())}",
                    "contextId": f"monitor_conversation_{int(time.time())}"
                }
            }
        }
        
        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                response = await client.post(
                    target_url,
                    json=payload,
                    headers={"Content-Type": "application/json"}
                )
                
                if response.status_code == 200:
                    result = response.json()
                    
                    # 응답 파싱
                    response_text = "응답 파싱 실패"
                    if "result" in result:
                        artifacts = result["result"].get("artifacts", [])
                        if artifacts and len(artifacts) > 0:
                            parts = artifacts[0].get("parts", [])
                            if parts and len(parts) > 0:
                                response_text = parts[0].get("text", "텍스트 없음")
                    
                    self.log_conversation(from_agent, to_agent, message, response_text)
                    return response_text
                
                else:
                    error = f"HTTP {response.status_code}: {response.text}"
                    self.log_conversation(from_agent, to_agent, message, error)
                    return error
                    
        except Exception as e:
            error = f"통신 오류: {str(e)}"
            self.log_conversation(from_agent, to_agent, message, error)
            return error
    
    def show_conversation_history(self, limit: int = 10):
        """최근 대화 기록 표시"""
        print("\n" + "=" * 80)
        print(f"🕒 최근 Agent 대화 기록 (최근 {limit}개)")
        print("=" * 80)
        
        recent_logs = self.conversation_log[-limit:] if self.conversation_log else []
        
        if not recent_logs:
            print("📭 아직 기록된 대화가 없습니다.")
            return
        
        for i, log in enumerate(recent_logs, 1):
            print(f"\n📋 대화 #{i}")
            print(f"⏰ 시간: {log['timestamp']}")
            print(f"👥 경로: {log['from'].upper()} → {log['to'].upper()}")
            print(f"💭 메시지: {log['message']}")
            if log['response']:
                print(f"💬 응답: {log['response']}")
            print("-" * 40)
    
    def get_full_conversation(self, index: int) -> Optional[Dict]:
        """전체 대화 내용 조회"""
        if 0 <= index < len(self.conversation_log):
            return self.conversation_log[index]
        return None
    
    async def simulate_agent_conversation(self):
        """Agent 간 실제 대화 시뮬레이션"""
        print("\n🎭 Agent 간 대화 시뮬레이션 시작")
        print("=" * 60)
        
        # 1. Frontend → Backend: API 협업 문의
        await self.send_agent_message(
            "frontend", "backend",
            """안녕하세요, Backend Agent님!
            
저는 Frontend Agent입니다. 사용자 인증 시스템에 대한 협업을 제안합니다.

다음 사항들에 대해 조율하고 싶습니다:
1. JWT 토큰 형식과 페이로드 구조
2. API 응답 형식 (성공/실패 시)
3. 에러 코드 표준화

Frontend에서 쉽게 처리할 수 있는 API 설계 방향을 제안해주세요!"""
        )
        
        await asyncio.sleep(2)
        
        # 2. Backend → Frontend: 피드백 요청
        await self.send_agent_message(
            "backend", "frontend", 
            """안녕하세요, Frontend Agent님!
            
방금 전 요청에 대한 응답으로 JWT 기반 인증 API를 설계했습니다.

이제 Frontend 관점에서 피드백을 부탁드립니다:
1. 제안한 API 응답 형식이 React 상태 관리에 적합한가요?
2. 토큰 갱신 플로우가 사용자 경험에 좋은가요?
3. 추가로 필요한 엔드포인트가 있나요?

실제 구현 시 고려사항도 알려주세요!"""
        )
        
        await asyncio.sleep(2)
        
        # 3. Unity → Backend: 게임 연동 문의
        await self.send_agent_message(
            "unity", "backend",
            """안녕하세요, Backend Agent님!
            
Unity Agent입니다. 게임과 백엔드 연동에 대해 문의드립니다.

Unity WebGL 빌드에서 다음 기능들을 구현하려고 합니다:
1. 게임 내 로그인/로그아웃
2. 플레이어 진행도 저장/불러오기
3. 리더보드 시스템

C# UnityWebRequest로 호출하기 쉬운 API 설계를 제안해주세요!"""
        )
        
        print("\n✅ 대화 시뮬레이션 완료!")
        print("📊 대화 기록을 확인하려면 show_conversation_history()를 호출하세요.")

async def main():
    """메인 실행 함수"""
    monitor = AgentConversationMonitor()
    
    print("🔍 Agent Conversation Monitor 시작")
    print("=" * 60)
    
    while True:
        print("\n📋 옵션을 선택하세요:")
        print("1. Agent 간 대화 시뮬레이션 실행")
        print("2. 최근 대화 기록 보기")
        print("3. 수동으로 Agent 메시지 전송")
        print("4. 로그 파일 위치 확인")
        print("5. 종료")
        
        try:
            choice = input("\n선택 (1-5): ").strip()
            
            if choice == "1":
                await monitor.simulate_agent_conversation()
            
            elif choice == "2":
                limit = input("표시할 대화 수 (기본 10): ").strip()
                limit = int(limit) if limit.isdigit() else 10
                monitor.show_conversation_history(limit)
            
            elif choice == "3":
                print("\n사용 가능한 Agent: frontend, backend, unity")
                from_agent = input("발신 Agent: ").strip().lower()
                to_agent = input("수신 Agent: ").strip().lower()
                message = input("메시지: ").strip()
                
                if from_agent and to_agent and message:
                    response = await monitor.send_agent_message(from_agent, to_agent, message)
                    print(f"\n📨 응답 받음: {response[:200]}...")
                else:
                    print("❌ 모든 필드를 입력해주세요.")
            
            elif choice == "4":
                print(f"\n📁 로그 파일 위치: {monitor.log_dir.absolute()}")
                log_files = list(monitor.log_dir.glob("*.json"))
                if log_files:
                    print("📄 로그 파일들:")
                    for log_file in log_files:
                        print(f"  - {log_file.name}")
                else:
                    print("📭 아직 로그 파일이 없습니다.")
            
            elif choice == "5":
                print("👋 모니터링을 종료합니다.")
                break
            
            else:
                print("❌ 올바른 옵션을 선택해주세요.")
                
        except KeyboardInterrupt:
            print("\n👋 사용자에 의해 중단되었습니다.")
            break
        except Exception as e:
            print(f"❌ 오류 발생: {str(e)}")

if __name__ == "__main__":
    asyncio.run(main())
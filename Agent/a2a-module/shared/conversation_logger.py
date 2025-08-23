"""
A2A Agent 간 대화 로깅 시스템
모든 agent에서 공유하여 대화 내역을 추적
"""
import json
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
import threading

class ConversationLogger:
    """Agent 간 대화를 기록하는 싱글톤 로거"""
    
    _instance = None
    _lock = threading.Lock()
    
    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance
    
    def __init__(self):
        if not hasattr(self, 'initialized'):
            self.log_dir = Path("agent_conversations")
            self.log_dir.mkdir(exist_ok=True)
            self.conversation_history = []
            self.active_contexts = {}
            self.initialized = True
    
    def log_incoming_message(self, agent_name: str, message_data: Dict, request_info: Dict = None):
        """들어오는 메시지 로깅"""
        timestamp = datetime.now()
        
        # 메시지 파싱
        message_text = "메시지 파싱 실패"
        message_id = "unknown"
        context_id = "unknown"
        task_id = "unknown"
        
        try:
            if "params" in message_data and "message" in message_data["params"]:
                msg = message_data["params"]["message"]
                if "parts" in msg and len(msg["parts"]) > 0:
                    message_text = msg["parts"][0].get("text", "텍스트 없음")
                message_id = msg.get("messageId", "unknown")
                context_id = msg.get("contextId", "unknown")
                task_id = msg.get("taskId", "unknown")
        except Exception as e:
            message_text = f"파싱 오류: {str(e)}"
        
        log_entry = {
            "timestamp": timestamp.isoformat(),
            "type": "incoming",
            "agent": agent_name,
            "message_id": message_id,
            "context_id": context_id,
            "task_id": task_id,
            "message": message_text,
            "full_data": message_data,
            "request_info": request_info
        }
        
        self.conversation_history.append(log_entry)
        self._save_to_file(log_entry)
        self._print_real_time_log(log_entry)
        
        return log_entry
    
    def log_outgoing_response(self, agent_name: str, response_data: Dict, context_id: str = None):
        """나가는 응답 로깅"""
        timestamp = datetime.now()
        
        # 응답 파싱
        response_text = "응답 파싱 실패"
        status = "unknown"
        
        try:
            if "result" in response_data:
                result = response_data["result"]
                status = result.get("status", {}).get("state", "unknown")
                
                if "artifacts" in result and len(result["artifacts"]) > 0:
                    artifacts = result["artifacts"][0]
                    if "parts" in artifacts and len(artifacts["parts"]) > 0:
                        response_text = artifacts["parts"][0].get("text", "텍스트 없음")
        except Exception as e:
            response_text = f"파싱 오류: {str(e)}"
        
        log_entry = {
            "timestamp": timestamp.isoformat(),
            "type": "outgoing",
            "agent": agent_name,
            "context_id": context_id,
            "status": status,
            "response": response_text,
            "full_data": response_data
        }
        
        self.conversation_history.append(log_entry)
        self._save_to_file(log_entry)
        self._print_real_time_log(log_entry)
        
        return log_entry
    
    def log_agent_to_agent_call(self, from_agent: str, to_agent: str, message: str):
        """Agent간 직접 호출 로깅"""
        timestamp = datetime.now()
        
        log_entry = {
            "timestamp": timestamp.isoformat(),
            "type": "agent_to_agent",
            "from_agent": from_agent,
            "to_agent": to_agent,
            "message": message
        }
        
        self.conversation_history.append(log_entry)
        self._save_to_file(log_entry)
        self._print_real_time_log(log_entry)
        
        return log_entry
    
    def _save_to_file(self, log_entry: Dict):
        """로그를 파일에 저장"""
        try:
            log_file = self.log_dir / f"conversation_{datetime.now().strftime('%Y%m%d')}.jsonl"
            with open(log_file, 'a', encoding='utf-8') as f:
                f.write(json.dumps(log_entry, ensure_ascii=False) + "\n")
        except Exception as e:
            print(f"❌ 로그 파일 저장 실패: {str(e)}")
    
    def _print_real_time_log(self, log_entry: Dict):
        """실시간 콘솔 로그 출력"""
        timestamp = datetime.fromisoformat(log_entry["timestamp"]).strftime("%H:%M:%S")
        
        if log_entry["type"] == "incoming":
            print(f"\n📨 [{timestamp}] {log_entry['agent'].upper()} ← 메시지 수신")
            print(f"🆔 Context: {log_entry['context_id']}")
            print(f"💬 내용: {log_entry['message'][:100]}{'...' if len(log_entry['message']) > 100 else ''}")
            
        elif log_entry["type"] == "outgoing":
            print(f"📤 [{timestamp}] {log_entry['agent'].upper()} → 응답 전송")
            print(f"✅ 상태: {log_entry['status']}")
            print(f"💬 응답: {log_entry['response'][:100]}{'...' if len(log_entry['response']) > 100 else ''}")
            
        elif log_entry["type"] == "agent_to_agent":
            print(f"🔄 [{timestamp}] Agent 간 통신: {log_entry['from_agent'].upper()} → {log_entry['to_agent'].upper()}")
            print(f"💬 메시지: {log_entry['message'][:100]}{'...' if len(log_entry['message']) > 100 else ''}")
        
        print("-" * 60)
    
    def get_conversation_by_context(self, context_id: str) -> List[Dict]:
        """특정 컨텍스트의 대화 내역 조회"""
        return [
            entry for entry in self.conversation_history 
            if entry.get("context_id") == context_id
        ]
    
    def get_recent_conversations(self, limit: int = 20) -> List[Dict]:
        """최근 대화 내역 조회"""
        return self.conversation_history[-limit:] if self.conversation_history else []
    
    def get_agent_conversations(self, agent_name: str, limit: int = 10) -> List[Dict]:
        """특정 agent의 대화 내역 조회"""
        agent_logs = [
            entry for entry in self.conversation_history 
            if entry.get("agent") == agent_name 
            or entry.get("from_agent") == agent_name 
            or entry.get("to_agent") == agent_name
        ]
        return agent_logs[-limit:] if agent_logs else []
    
    def print_conversation_summary(self):
        """대화 요약 출력"""
        print("\n" + "=" * 80)
        print("📊 Agent 대화 요약")
        print("=" * 80)
        
        if not self.conversation_history:
            print("📭 아직 기록된 대화가 없습니다.")
            return
        
        # 통계
        total_messages = len(self.conversation_history)
        incoming_count = len([e for e in self.conversation_history if e["type"] == "incoming"])
        outgoing_count = len([e for e in self.conversation_history if e["type"] == "outgoing"])
        agent_to_agent_count = len([e for e in self.conversation_history if e["type"] == "agent_to_agent"])
        
        print(f"📈 총 이벤트: {total_messages}")
        print(f"📨 수신 메시지: {incoming_count}")
        print(f"📤 발신 응답: {outgoing_count}")
        print(f"🔄 Agent간 통신: {agent_to_agent_count}")
        
        # 최근 5개 대화
        recent = self.get_recent_conversations(5)
        print(f"\n📋 최근 5개 이벤트:")
        for i, entry in enumerate(recent, 1):
            timestamp = datetime.fromisoformat(entry["timestamp"]).strftime("%H:%M:%S")
            if entry["type"] == "incoming":
                print(f"  {i}. [{timestamp}] {entry['agent']} ← {entry['message'][:50]}...")
            elif entry["type"] == "outgoing":
                print(f"  {i}. [{timestamp}] {entry['agent']} → {entry['response'][:50]}...")
            elif entry["type"] == "agent_to_agent":
                print(f"  {i}. [{timestamp}] {entry['from_agent']} → {entry['to_agent']}: {entry['message'][:50]}...")

# 글로벌 로거 인스턴스
conversation_logger = ConversationLogger()
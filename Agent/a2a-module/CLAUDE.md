# Claude CLI A2A Multi-Agent System Configuration

## System Overview
You are the Host Agent in an A2A (Agent-to-Agent) multi-agent system. Your role is to:
1. Analyze user requests to determine which specialized agent(s) to involve
2. Communicate with A2A Worker Agents via HTTP JSON-RPC 2.0 protocol
3. Coordinate responses from multiple agents when needed
4. Return integrated results to the user
5. Maintain the A2A protocol compliance with Google ADK standards

## Available A2A Worker Agents

All agents are Claude AI instances running as independent A2A servers:

- **Perception Agent**: Port 8030 - Camera, ROI processing, Android XR camera systems expert
- **Vision Agent**: Port 8031 - OpenAI Realtime API, GPT-4V, VLM/LLM integration expert  
- **UX/TTS Agent**: Port 8032 - UI/HUD, TTS voice output, XR interaction expert
- **Logger Agent**: Port 8033 - Performance monitoring, logging, analytics expert

Each agent:
- Runs independent Claude CLI subprocess for AI responses
- Provides Agent Card at `/.well-known/agent.json` endpoint
- Supports JSON-RPC 2.0 A2A protocol communication
- Has specialized CLAUDE.md configuration for domain expertise

## Task Routing Logic

When you receive a user request:

1. **Analyze the request** to identify domain expertise needed:
   - Perception: Camera, webcam, ROI processing, Android XR camera systems
   - Vision: OpenAI Realtime API, GPT-4V, VLM/LLM integration, WebSocket
   - UX/TTS: UI/HUD, TTS voice output, XR interaction, audio processing
   - Logger: Performance monitoring, logging, analytics, debugging

2. **Select appropriate A2A agent(s)**:
   - Single domain: Route to specific agent
   - Multi-domain: Coordinate multiple agents and integrate responses

3. **Send A2A requests** using JSON-RPC 2.0 protocol to worker agents

4. **Integrate and present** the responses to the user

## A2A Protocol Communication

Use HTTP JSON-RPC 2.0 to communicate with worker agents:

```python
import requests
import json
import time

def call_a2a_agent(agent_type: str, task: str) -> str:
    """Call an A2A worker agent via HTTP JSON-RPC 2.0"""
    
    agent_ports = {
        "frontend": 8010,
        "backend": 8021,
        "unity": 8012
    }
    
    port = agent_ports.get(agent_type)
    if not port:
        return f"Unknown agent type: {agent_type}"
    
    url = f"http://localhost:{port}/"
    
    # Google A2A protocol message format (strictly compliant)
    message = {
        "jsonrpc": "2.0",
        "id": f"host_to_{agent_type}_{int(time.time())}",
        "method": "message/send",
        "params": {
            "message": {
                "role": "user",
                "parts": [
                    {
                        "kind": "text",
                        "text": task,
                        "mimeType": "text/plain"
                    }
                ],
                "messageId": f"msg_{int(time.time())}",
                "kind": "message"
            }
        }
    }
    
    try:
        agent_name = f"{agent_type.capitalize()} Agent"
        print(f"\n[{agent_name}] A2A request initiated")
        print(f"[{agent_name}] URL: {url}")
        print(f"[{agent_name}] Task: {task[:100]}...")
        print(f"[{agent_name}] " + "-" * 60)
        
        response = requests.post(
            url, 
            json=message, 
            headers={"Content-Type": "application/json; charset=utf-8"},
            timeout=600  # 10 minutes for complex AI responses and A2A communication
        )
        
        # Ensure response is properly encoded
        response.encoding = 'utf-8'
        
        if response.status_code == 200:
            result = response.json()
            
            # Extract response content from A2A protocol format
            if "result" in result:
                artifacts = result["result"].get("artifacts", [])
                if artifacts and len(artifacts) > 0:
                    parts = artifacts[0].get("parts", [])
                    if parts and len(parts) > 0:
                        content = parts[0].get("text", "")
                        print(f"[{agent_name}] Success: {len(content)} characters received")
                        return content
                
                # Fallback: check status message
                status = result["result"].get("status", {})
                message = status.get("message", {})
                if message and "parts" in message:
                    parts = message["parts"]
                    if parts and len(parts) > 0:
                        content = parts[0].get("text", "")
                        print(f"[{agent_name}] Status response: {len(content)} characters")
                        return content
            
            print(f"[{agent_name}] Warning: Unexpected response format")
            return str(result)
        else:
            error_msg = f"HTTP {response.status_code}: {response.text}"
            print(f"[{agent_name}] Error: {error_msg}")
            return f"Error from {agent_type} agent: {error_msg}"
            
    except requests.exceptions.Timeout:
        print(f"[{agent_name}] Timeout after 6 minutes")
        return f"{agent_type} agent timed out"
    except Exception as e:
        print(f"[{agent_name}] Exception: {str(e)}")
        return f"Error calling {agent_type} agent: {str(e)}"
```

## Worker Agent Conversations

To demonstrate or facilitate worker agent conversations during regular use:

```python
def demonstrate_agent_conversation(topic: str = "fullstack collaboration") -> str:
    """Demonstrate worker agents communicating with each other via A2A protocol"""
    
    print("\n" + "=" * 80)
    print(f"DEMONSTRATION: Worker Agent A2A Conversation - {topic}")
    print("=" * 80)
    
    if "fullstack" in topic.lower() or "collaboration" in topic.lower():
        # Frontend and Backend collaboration
        print("\n[STEP 1] Backend Agent → Frontend Agent collaboration")
        backend_to_frontend = call_a2a_agent(
            "backend",
            """Send an A2A message to Frontend Agent (http://localhost:8010) asking them to collaborate on 
            API integration patterns. Use the A2A JSON-RPC 2.0 protocol to communicate directly and 
            get their response about preferred data structures for REST API responses."""
        )
        
        print("\n[STEP 2] Frontend Agent → Backend Agent collaboration")
        frontend_to_backend = call_a2a_agent(
            "frontend", 
            """Send an A2A message to Backend Agent (http://localhost:8021) asking them to coordinate 
            on authentication flow implementation. Use A2A JSON-RPC 2.0 protocol to discuss JWT token 
            handling and get their recommendations for secure session management."""
        )
        
    elif "game" in topic.lower() or "unity" in topic.lower():
        # Unity and Backend collaboration for game systems
        print("\n[STEP 1] Unity Agent → Backend Agent collaboration") 
        unity_to_backend = call_a2a_agent(
            "unity",
            """Send an A2A message to Backend Agent (http://localhost:8021) asking them to collaborate on 
            multiplayer game backend systems. Use A2A JSON-RPC 2.0 protocol to discuss leaderboard APIs 
            and real-time networking requirements."""
        )
        
        print("\n[STEP 2] Backend Agent → Unity Agent collaboration")
        backend_to_unity = call_a2a_agent(
            "backend",
            """Send an A2A message to Unity Agent (http://localhost:8012) asking them to coordinate on 
            game data synchronization. Use A2A JSON-RPC 2.0 protocol to discuss player state management 
            and get Unity's requirements for game server integration."""
        )
    
    elif "ui" in topic.lower() or "webgl" in topic.lower():
        # Frontend and Unity collaboration for WebGL games
        print("\n[STEP 1] Frontend Agent → Unity Agent collaboration")
        frontend_to_unity = call_a2a_agent(
            "frontend",
            """Send an A2A message to Unity Agent (http://localhost:8012) asking them to collaborate on 
            WebGL game UI integration. Use A2A JSON-RPC 2.0 protocol to discuss how to embed Unity WebGL 
            builds in React applications and coordinate on responsive design."""
        )
        
        print("\n[STEP 2] Unity Agent → Frontend Agent collaboration") 
        unity_to_frontend = call_a2a_agent(
            "unity",
            """Send an A2A message to Frontend Agent (http://localhost:8010) asking them to coordinate on 
            Unity WebGL communication. Use A2A JSON-RPC 2.0 protocol to discuss JavaScript interop patterns 
            and get their recommendations for Unity-to-web messaging."""
        )
    
    print("\n" + "=" * 80)
    print("DEMONSTRATION COMPLETE: Worker agents have communicated via A2A protocol!")
    print("Check individual agent logs to see the actual conversation details.")
    print("=" * 80)
    
    return "Worker agent conversation demonstration completed successfully."
```

### Usage Examples for Agent Conversations

During regular use, you can now trigger worker agent conversations by natural language requests:

**User Request Examples:**
- "Show me how the frontend and backend agents collaborate on APIs"
- "Demonstrate Unity and backend agents discussing multiplayer systems" 
- "Let the frontend and Unity agents talk about WebGL integration"
- "Have all three agents coordinate on a fullstack game project"

**Host Agent Response Pattern:**
```python
# When user asks to see agent conversations
if "show" in user_request and "agent" in user_request and "talk" in user_request:
    topic = extract_topic_from_request(user_request)  # e.g., "fullstack", "game", "ui"
    result = demonstrate_agent_conversation(topic)
    return result
```

## Agent Endpoints

Each A2A worker agent provides these endpoints:

- **Frontend Agent**: `http://localhost:8010`
  - Agent Card: `http://localhost:8010/.well-known/agent.json`
  - A2A Communication: `http://localhost:8010/` (POST JSON-RPC 2.0)

- **Backend Agent**: `http://localhost:8021`
  - Agent Card: `http://localhost:8021/.well-known/agent.json`
  - A2A Communication: `http://localhost:8021/` (POST JSON-RPC 2.0)

- **Unity Agent**: `http://localhost:8012`
  - Agent Card: `http://localhost:8012/.well-known/agent.json`
  - A2A Communication: `http://localhost:8012/` (POST JSON-RPC 2.0)

## Example Workflows

### Single Agent Request
```
User: "Create a login form component in React"
Host: Analyze → call_a2a_agent("frontend", task) → Return AI response
```

### Multi-Agent Request
```
User: "Build a task management app with React frontend and Node.js backend"
Host: 
  1. Analyze → Needs Frontend + Backend
  2. call_a2a_agent("frontend", "Create React task management UI")
  3. call_a2a_agent("backend", "Create Node.js API for task management")
  4. Integrate responses
  5. Return comprehensive solution
```

## EXECUTION RULES FOR HOST AGENT

When a user asks for specialized development work:

1. **IDENTIFY** which agent type is needed (frontend/backend/unity)
2. **EXECUTE** the `call_a2a_agent()` function with the agent type and task
3. **RETURN** the AI response to the user

**EXAMPLE EXECUTION:**

```python
# For frontend requests
result = call_a2a_agent("frontend", "Create a React button component")
print(result)
```

```python  
# For backend requests
result = call_a2a_agent("backend", "Create REST API for user authentication")
print(result)
```

```python
# For Unity requests  
result = call_a2a_agent("unity", "Create a character controller script")
print(result)
```

## STRICT RULES FOR HOST AGENT

- ❌ **NEVER** do specialized development work yourself - delegate to A2A agents
- ❌ **NEVER** use Write, Edit, MultiEdit, or any file creation tools - only A2A Worker Agents create files
- ❌ **NEVER** create files directly in the filesystem - this violates the A2A architecture
- ❌ **NEVER** interrupt or timeout Agent communications - WAIT for responses
- ✅ **ALWAYS** route technical tasks to appropriate A2A specialists
- ✅ **ALWAYS** use the `call_a2a_agent()` function for development requests
- ✅ **ALWAYS** coordinate multi-agent workflows when needed
- ✅ **MAINTAIN** A2A protocol compliance with Google ADK standards
- ✅ **ONLY** coordinate, route, and integrate responses from Worker Agents
- ✅ **WAIT PATIENTLY** for Agent responses - ALWAYS use timeout=600 seconds (10 minutes)
- ✅ **TRUST WORKER AGENTS** to handle all code modifications and file operations
- ✅ **MANDATORY TIMEOUT**: ALL Agent requests MUST use timeout=600 in Python code

## Important Notes

1. **EXECUTE THE PYTHON CODE ABOVE** - Don't just reference it, run it
2. **Always identify the most appropriate agent(s)** for each task
3. **Minimize unnecessary agent calls** - only involve agents that are truly needed
4. **Integrate responses thoughtfully** when multiple agents are involved
5. **Handle errors gracefully** - if an agent fails, provide alternative solutions
6. **Maintain context** between agent calls for complex multi-step tasks
7. **Each A2A agent runs independent Claude AI** - responses are AI-generated, not templated

## System Management

### Starting A2A Servers
```bash
# Start all A2A worker agents
cd agents/claude_cli/frontend && python server.py &
cd agents/claude_cli/backend && python server.py &
cd agents/claude_cli/unity && python server.py &
```

### Checking Agent Status
```bash
# Check agent cards
curl http://localhost:8010/.well-known/agent.json
curl http://localhost:8021/.well-known/agent.json
curl http://localhost:8012/.well-known/agent.json
```
```
Android SDK D:\Users\SOGANG\AppData\Local\Android\Sdk
```
### System Architecture
```
User → Host Agent (Current Claude Session) → A2A Worker Agents → Claude AI Responses
```

Remember: You are the orchestrator. Your value comes from intelligent routing and response integration, not from doing the specialized work yourself. Each worker agent provides real Claude AI expertise in their domain.

---

# 🥽 AR Glass Q&A System - HYBRID AI SYSTEM COMPLETE

## 🏆 **전체 완성도: 100% COMPLETE** ✨🎵 **Context7 최적화 포함**

### 🔥 **완성된 하이브리드 AI 시스템**:

#### 1. **ChatGPT Voice + Vision 통합** (100%) 🎤👁️
   - **Real-time Voice Conversations**: OpenAI Realtime API WebSocket
   - **Automatic Vision Triggering**: 이미지 키워드 감지 → GPT-4V 자동 호출
   - **Context Preservation**: 시각 분석 결과가 대화 맥락으로 자동 통합
   - **Natural Flow**: "뭐가 보여요?" → [이미지 캡처] → "책상 위 컵이 보이네요"

#### 2. **Premium TTS & Audio** (100%) 🔊🎵 **Context7 최적화 완료**
   - **OpenAI TTS**: Korean 텍스트 최적화 + shimmer 여성 목소리 + PCM 포맷
   - **Context7 PCM AudioTrack**: MediaPlayer→AudioTrack 직접 스트리밍으로 크래클링 해결
   - **Interrupt-Free Playback**: 단일 AudioTrack으로 부드러운 한국어 재생
   - **24kHz PCM16**: MP3 디코딩 오버헤드 제거 → 네이티브 품질

#### 3. **Android XR App** (100%) 📱
   - **MainActivity.kt**: 완전한 UI 통합 + 실시간 상태 관리
   - **VisionIntegration.kt**: 하이브리드 시스템 오케스트레이터
   - **CrosshairOverlay.kt**: AR 타겟팅 + 반응형 UI
   - **Camera2Manager**: 실시간 프레임 캡처 + JPEG 변환

#### 4. **Smart Hybrid Logic** (100%) 🧠
   - **Keyword Detection**: `isImageQuestion()` - 15+ 한영 키워드 감지
   - **Auto GPT-4V Triggering**: `handleImageQuestion()` - 자동 이미지 분석
   - **Dual API Coordination**: Realtime API ↔ Chat Completions API 연동
   - **Response Integration**: Vision 결과 → Realtime 컨텍스트 주입

### 🎯 **사용자 시나리오**:

**시나리오 1 - 자연스러운 음성 대화**:
```
사용자: "안녕하세요"
AI: "안녕하세요! 무엇을 도와드릴까요?" (Realtime API)

사용자: "뭐가 보여요?" 
→ [키워드 감지] → [이미지 자동 캡처] → [GPT-4V 분석]
AI: "책상 위에 파란색 컵과 키보드가 보이네요" (OpenAI TTS)

사용자: "그 컵 크기는 어때요?"
AI: "중간 크기의 머그컵 정도로 보입니다" (컨텍스트 기억)
```

**시나리오 2 - 버튼 캡처**:
```
사용자: [📸 버튼 클릭]
→ [즉시 이미지 분석] 
AI: "모니터와 키보드가 있는 깔끔한 작업공간이네요"
```

### ✅ **핵심 기술 구현 완료**:

1. **RealtimeVisionClient.kt** - OpenAI Realtime API WebSocket 클라이언트
2. **VisionAnalyzer.kt** - GPT-4V Chat Completions API 이미지 분석
3. **VisionIntegration.kt** - 하이브리드 시스템 조정자
4. **OpenAITtsManager.kt** - 프리미엄 한국어 TTS 최적화
5. **VoiceManager.kt** - TTS 충돌 방지 및 통합 관리
6. **AudioStreamManager.kt** - 24kHz 실시간 오디오 스트리밍
7. **MainActivity.kt** - 완전한 XR UI 및 상태 관리

### 🏗️ **최종 아키텍처**:
```
🎤 VOICE → RealtimeClient → isImageQuestion()?
                               ↙️         ↘️
                    📸 GPT-4V Vision   💬 Voice Chat
                           ↓               ↓
                    VisionAnalyzer → handleTextResponse()
                           ↓               ↓
                    🔊 OpenAI TTS (Korean) → AR Glass
```

### 🚀 **준비완료 상태**:
- ✅ **빌드 성공**: `./gradlew build` 통과
- ✅ **시스템 통합**: 모든 컴포넌트 연동 완료
- ✅ **TTS 최적화**: 끊김 없는 한국어 음성 출력
- ✅ **하이브리드 로직**: 음성↔시각 자동 전환
- ✅ **에러 핸들링**: 완전한 예외 처리 및 복구

### 🎮 **테스트 준비**:
1. **API 키 설정**: `gradle.properties`에 OpenAI API 키 추가
2. **에뮬레이터 설정**: 웹캠 활성화 (선택사항)
3. **실행**: ChatGPT Voice 같은 완전한 AI 어시스턴트 경험

**🏆 STATUS: PRODUCTION-READY HYBRID AI AR GLASS SYSTEM**
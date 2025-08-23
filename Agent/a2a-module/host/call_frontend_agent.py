import subprocess
import os
import time
from pathlib import Path

def call_sub_agent(agent_type: str, task: str, project_name: str = None) -> str:
    """
    Host Agent가 워커 에이전트를 올바르게 호출하는 함수
    - 에이전트 폴더는 깨끗하게 유지
    - 실제 작업은 프로젝트 폴더에서 수행
    """
    agent_configs = {
        'frontend': {
            'directory': 'agents/claude_cli/frontend',
            'description': 'Frontend Developer expert specializing in React, Vue, and modern web technologies'
        },
        'backend': {
            'directory': 'agents/claude_cli/backend',
            'description': 'Backend Developer expert specializing in APIs, databases, and server architecture'
        },
        'unity': {
            'directory': 'agents/claude_cli/unity',
            'description': 'Unity Developer expert specializing in game development and C#'
        }
    }
    
    config = agent_configs.get(agent_type)
    if not config:
        return f'Unknown agent type: {agent_type}'
    
    # 프로젝트 이름 생성 (지정되지 않은 경우)
    if project_name is None:
        timestamp = int(time.time())
        project_name = f"PRJ{timestamp % 10000:04d}"
    
    # 프로젝트 폴더 구조 생성
    current_dir = os.getcwd()
    agent_dir = os.path.join(current_dir, config['directory'])  # 에이전트 설정 폴더
    project_dir = os.path.join(current_dir, 'projects', project_name, agent_type)  # 작업 폴더
    
    # 프로젝트 폴더 생성
    os.makedirs(project_dir, exist_ok=True)
    
    # 수정된 시스템 프롬프트 (프로젝트 폴더 지정)
    system_prompt = f'You are a {agent_type} development expert. Create all files in the project directory: {project_dir}. Keep agent directory clean. Focus only on {agent_type} development work.'
    
    # Claude CLI 명령어 구성 (절대 경로 사용)
    claude_path = r'C:\Users\SOGANG\AppData\Roaming\npm\claude.cmd'
    cmd = [
        claude_path,
        '--add-dir', agent_dir,          # 에이전트 설정 읽기용
        '--add-dir', project_dir,        # 프로젝트 작업용
        '--print', 
        '--permission-mode', 'bypassPermissions', 
        '--append-system-prompt', system_prompt, 
        task
    ]
    
    try:
        agent_name = f'{agent_type.capitalize()} Agent'
        print(f'\n[{agent_name}] Host Agent 호출 시작')
        print(f'[{agent_name}] 프로젝트: {project_name}')
        print(f'[{agent_name}] 에이전트 설정: {agent_dir}')
        print(f'[{agent_name}] 작업 폴더: {project_dir}')
        print(f'[{agent_name}] 작업: {task[:100]}...')
        print(f'[{agent_name}] ' + '-' * 60)
        
        result = subprocess.run(
            cmd, 
            capture_output=True, 
            text=True, 
            timeout=600,
            cwd=project_dir,  # 프로젝트 폴더에서 실행
            encoding='utf-8'
        )
        
        print(f'[{agent_name}] 실행 완료')
        print(f'[{agent_name}] 종료 코드: {result.returncode}')
        print(f'[{agent_name}] 출력 길이: {len(result.stdout)} 문자')
        if result.stderr:
            print(f'[{agent_name}] 에러: {result.stderr}')
        print(f'[{agent_name}] ' + '-' * 60)
        
        # 성공 시 결과와 프로젝트 정보 반환
        if result.returncode == 0:
            return f"""
=== {agent_name} 작업 완료 ===
프로젝트: {project_name}
작업 폴더: {project_dir}

{result.stdout}

프로젝트 파일들은 {project_dir}에 저장되었습니다.
"""
        else:
            return f"Error: {result.stderr or '알 수 없는 오류'}"
    except subprocess.TimeoutExpired:
        return f'Agent {agent_type} timed out'
    except Exception as e:
        return f'Error calling {agent_type} agent: {str(e)}'

def call_multiple_agents(tasks: dict, project_name: str = None) -> str:
    """
    여러 에이전트를 동일 프로젝트에서 호출
    예: tasks = {'frontend': '...', 'backend': '...'}
    """
    if project_name is None:
        timestamp = int(time.time())
        project_name = f"PRJ{timestamp % 10000:04d}"
    
    results = []
    for agent_type, task in tasks.items():
        result = call_sub_agent(agent_type, task, project_name)
        results.append(result)
    
    return f"\n{'='*80}\n".join(results)

# 테스트 예시
if __name__ == "__main__":
    # 단일 에이전트 호출
    print("=== 단일 에이전트 테스트 ===")
    task = "Create a simple user information display component in React with TypeScript. Include props for name, email, and avatar."
    response = call_sub_agent('frontend', task, "DEMO001")
    print(response)
    
    print("\n" + "="*100 + "\n")
    
    # 멀티 에이전트 호출 테스트
    print("=== 멀티 에이전트 테스트 ===")
    multi_tasks = {
        'frontend': 'Create a React login form with email and password fields',
        'backend': 'Create Express.js API endpoint for user authentication'
    }
    multi_response = call_multiple_agents(multi_tasks, "FULLSTACK001")
    print(multi_response)
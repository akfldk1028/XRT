"""
Project Context Loader for A2A Agents
Provides common functionality for loading project status and configuration files
"""
import os
from pathlib import Path
from typing import Dict, Optional

def load_project_status(agent_name: str) -> Dict[str, str]:
    """
    Load current project status from MD files for any agent
    
    Args:
        agent_name: Name of the agent (e.g., 'vision', 'perception', 'ux_tts', 'logger')
    
    Returns:
        Dictionary containing loaded content from various status files
    """
    # Determine agent directory
    agent_dir = Path(__file__).parent.parent / 'agents' / 'claude_cli' / agent_name
    project_root = Path(__file__).parent.parent
    
    status_files = {
        'claude_config': agent_dir / 'CLAUDE.md',
        'project_status': project_root / 'PROJECT_STATUS_REALTIME_API.md',
        'system_status': project_root / 'ANDROID_XR_SYSTEM_STATUS.md',
        'architecture': project_root / 'AR_GLASS_APP_ARCHITECTURE.md'
    }
    
    project_context = {}
    for key, file_path in status_files.items():
        try:
            if file_path.exists():
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    project_context[key] = content
                print(f"[{agent_name.title()} Agent] Loaded {key}: {len(content)} characters")
            else:
                print(f"[{agent_name.title()} Agent] Warning: {file_path} not found")
        except Exception as e:
            print(f"[{agent_name.title()} Agent] Error loading {key}: {e}")
    
    return project_context

def enhance_query_with_context(query: str, project_status: Dict[str, str], agent_name: str) -> str:
    """
    Enhance user query with relevant project status information
    
    Args:
        query: Original user query
        project_status: Loaded project status data
        agent_name: Name of the current agent
        
    Returns:
        Enhanced query with context information
    """
    context_info = []
    
    # Add main project status if available
    if project_status.get('project_status'):
        context_info.append("=== CURRENT PROJECT STATUS ===")
        context_info.append(project_status['project_status'][:2000])  # Limit size
        context_info.append("=== END PROJECT STATUS ===\n")
    
    # Add agent-specific config summary
    if project_status.get('claude_config'):
        config = project_status['claude_config']
        if "CURRENT PROJECT STATUS" in config:
            status_section = config.split("CURRENT PROJECT STATUS")[1][:1500]
            context_info.append(f"=== {agent_name.upper()} AGENT STATUS ===")
            context_info.append(status_section)
            context_info.append(f"=== END {agent_name.upper()} AGENT STATUS ===\n")
    
    # Add system architecture if relevant
    if project_status.get('architecture') and len(context_info) < 2:
        context_info.append("=== SYSTEM ARCHITECTURE ===")
        context_info.append(project_status['architecture'][:1000])
        context_info.append("=== END SYSTEM ARCHITECTURE ===\n")
    
    if context_info:
        enhanced_query = f"""
{chr(10).join(context_info)}

USER REQUEST:
{query}

Based on the current project status above, please provide assistance with this request. 
Consider what's already implemented by other agents and focus on your specific role and responsibilities.
Coordinate with other agents as needed using the A2A protocol.
"""
        return enhanced_query
    
    return query

def get_agent_status_summary(project_status: Dict[str, str], agent_name: str) -> Optional[str]:
    """
    Extract a brief status summary for the specific agent
    
    Args:
        project_status: Loaded project status data
        agent_name: Name of the agent
        
    Returns:
        Brief status summary or None if not available
    """
    if not project_status.get('claude_config'):
        return None
        
    config = project_status['claude_config']
    
    # Look for status indicators in the agent's CLAUDE.md
    status_markers = [
        "CURRENT STATUS",
        f"{agent_name.upper()} AGENT STATUS", 
        "COMPLETED IMPLEMENTATIONS",
        "NEXT STEPS"
    ]
    
    for marker in status_markers:
        if marker in config:
            # Extract a small section around the marker
            start_idx = config.find(marker)
            if start_idx != -1:
                section = config[start_idx:start_idx + 500]
                return section.split('\n')[0:5]  # First 5 lines
    
    return None

def refresh_project_status(agent_name: str) -> Dict[str, str]:
    """
    Refresh project status by reloading all files
    Useful for long-running agent processes
    """
    print(f"[{agent_name.title()} Agent] Refreshing project status...")
    return load_project_status(agent_name)
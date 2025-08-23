import subprocess
import os

def call_backend_agent():
    """Call Backend Agent to create user profile API"""
    
    # Get current directory and target directory
    current_dir = os.getcwd()
    target_dir = os.path.join(current_dir, "agents", "claude_cli", "backend")
    
    # Task for backend agent
    task = "Create a simple user profile API endpoint with GET and POST methods. Include fields: id, name, email, createdAt. Use in-memory storage."
    
    # Build Claude CLI command
    cmd = ["claude", "--print", "--permission-mode", "bypassPermissions", task]
    
    try:
        print(f"\n[Backend Agent] Subprocess call initiated")
        print(f"[Backend Agent] Working Directory: {target_dir}")
        print(f"[Backend Agent] Task: {task}")
        print(f"[Backend Agent] " + "-" * 60)
        
        result = subprocess.run(
            cmd, 
            capture_output=True, 
            text=True, 
            timeout=60,
            cwd=target_dir
        )
        
        print(f"[Backend Agent] Subprocess completed")
        print(f"[Backend Agent] Exit Code: {result.returncode}")
        print(f"[Backend Agent] Response:")
        print(result.stdout)
        if result.stderr:
            print(f"[Backend Agent] Error: {result.stderr}")
        print(f"[Backend Agent] " + "-" * 60)
        
        return result.stdout
    except subprocess.TimeoutExpired:
        return "Backend Agent timed out"
    except Exception as e:
        return f"Error calling Backend Agent: {str(e)}"

if __name__ == "__main__":
    response = call_backend_agent()
    print("\nFinal Response from Backend Agent:")
    print(response)
"""
A2A Multi-Agent Host CLI - Google ADK Pattern Implementation
"""
import typer
from dotenv import load_dotenv
from enum import Enum

# Load environment variables
load_dotenv()

# Import base host agent
from base_host_agent import BaseHostAgent

class HostType(str, Enum):
    financial = "financial"
    travel = "travel"

app = typer.Typer()

def run_multi_agent(
    currency_url: str = "http://localhost:8000", 
    weather_url: str = "http://localhost:8001"
):
    """
    Run Multi-Agent Host with both Financial and Travel capabilities.
    """
    
    # Create a combined host that uses both agents
    class MultiHostAgent(BaseHostAgent):
        def __init__(self, remote_agent_addresses):
            super().__init__(remote_agent_addresses, "MultiHost")
        
        def get_agent_for_task(self, task_description: str):
            task_lower = task_description.lower()
            
            # Currency keywords
            if any(word in task_lower for word in ['currency', 'convert', 'usd', 'eur', 'krw', 'money']):
                for agent_name, card in self.cards.items():
                    if 'currency' in card.description.lower():
                        return agent_name
            
            # Weather keywords  
            if any(word in task_lower for word in ['weather', 'temperature', 'rain', 'forecast']):
                for agent_name, card in self.cards.items():
                    if 'weather' in card.description.lower():
                        return agent_name
            
            return list(self.cards.keys())[0] if self.cards else None
        
        def orchestrate(self, user_request: str) -> str:
            task_lower = user_request.lower()
            responses = []
            
            # Check for both currency and weather needs
            needs_currency = any(word in task_lower for word in ['currency', 'convert', 'usd', 'eur', 'krw', 'money'])
            needs_weather = any(word in task_lower for word in ['weather', 'temperature', 'rain'])
            
            if needs_currency and needs_weather:
                print("[MULTI] Multi-agent coordination needed")
                
                # Weather first
                weather_agent = None
                for name, card in self.cards.items():
                    if 'weather' in card.description.lower():
                        weather_agent = name
                        break
                
                if weather_agent:
                    try:
                        result = self.send_message(weather_agent, user_request)
                        responses.append(f"Weather: {result['message']}")
                    except Exception as e:
                        responses.append(f"Weather Error: {e}")
                
                # Currency second
                currency_agent = None
                for name, card in self.cards.items():
                    if 'currency' in card.description.lower():
                        currency_agent = name
                        break
                
                if currency_agent:
                    try:
                        result = self.send_message(currency_agent, user_request)
                        responses.append(f"Currency: {result['message']}")
                    except Exception as e:
                        responses.append(f"Currency Error: {e}")
            
            else:
                # Single agent
                agent_name = self.get_agent_for_task(user_request)
                if agent_name:
                    try:
                        result = self.send_message(agent_name, user_request)
                        responses.append(result['message'])
                    except Exception as e:
                        responses.append(f"Error: {e}")
                else:
                    responses.append("No suitable agent found")
            
            return "\n\n".join(responses) if responses else "Unable to process request"
    
    host_agent = MultiHostAgent([currency_url, weather_url])
    
    typer.echo("[MULTI] Multi-Agent Host Started")
    typer.echo("Capabilities: Financial services + Travel planning")
    
    # Show connected agents
    typer.echo(f"\nConnected Agents:")
    for agent_info in host_agent.list_remote_agents():
        typer.echo(f"  - {agent_info['name']}: {agent_info['description']}")
    
    typer.echo(f"\nExamples:")
    typer.echo(f"  - Convert 100 USD to EUR")
    typer.echo(f"  - What's the weather in Seoul?")
    typer.echo(f"  - Planning trip to Tokyo - need weather and 500 USD in JPY")
    
    typer.echo(f"\nType 'quit' or 'exit' to stop.")
    typer.echo("=" * 60)
    
    # Main conversation loop
    while True:
        user_msg = typer.prompt(f"\n[MULTI]")
        if user_msg.strip().lower() in ["quit", "exit", "bye"]:
            typer.echo("Multi-Agent Host stopped!")
            break
        
        try:
            response = host_agent.orchestrate(user_msg)
            typer.echo(f"\n{response}")
        except Exception as e:
            typer.echo(f"Error: {e}")

@app.command()
def list_agents(
    currency_url: str = "http://localhost:8000",
    weather_url: str = "http://localhost:8001"
):
    """List available agents and their capabilities."""
    from base_host_agent import RemoteAgentConnection
    
    urls = [currency_url, weather_url]
    
    typer.echo("Available Agents:")
    typer.echo("=" * 40)
    
    for url in urls:
        try:
            connection = RemoteAgentConnection(url)
            card = connection.retrieve_card()
            typer.echo(f"\n[AGENT] {card.name}")
            typer.echo(f"   URL: {card.url}")
            typer.echo(f"   Description: {card.description}")
            typer.echo(f"   Version: {card.version}")
            if card.capabilities:
                typer.echo(f"   Capabilities: {card.capabilities}")
        except Exception as e:
            typer.echo(f"\n❌ {url}: Connection failed - {e}")

def main():
    """Entry point - runs multi-agent directly"""
    # Run multi-agent mode directly without subcommands
    run_multi_agent()

if __name__ == "__main__":
    main()
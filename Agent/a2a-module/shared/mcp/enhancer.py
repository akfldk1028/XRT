"""
Smart MCP Query Enhancer
Intelligently determines what MCP tools to use based on query analysis
"""
from typing import Dict, Any, List
from .client import MCPClient

class SmartMCPEnhancer:
    """
    Intelligent MCP enhancement that lets agents decide what they need
    instead of hardcoded keyword matching
    """
    
    def __init__(self, agent_domain: str):
        self.agent_domain = agent_domain
        self.mcp_client = MCPClient()
    
    def should_use_sequential_thinking(self, query: str) -> bool:
        """Let AI decide if query needs sequential thinking"""
        try:
            decision = self.mcp_client.sequential_thinking(
                f"As a {self.agent_domain} expert, analyze this query: '{query}'. "
                f"Does this require deep, multi-step analysis? Answer with 'YES' or 'NO' and brief reason.",
                thought_number=1,
                total_thoughts=1,
                next_thought_needed=False
            )
            
            decision_text = decision.get('thought_analysis', '').upper()
            return 'YES' in decision_text
            
        except Exception:
            # Fallback to simple heuristic
            return len(query.split()) > 8 or len(query) > 100
    
    def extract_potential_technologies(self, query: str) -> List[str]:
        """
        AI-driven technology extraction - no hardcoded patterns
        """
        try:
            # Ask AI to identify technologies from the query
            tech_analysis = self.mcp_client.sequential_thinking(
                f"As a {self.agent_domain} expert, analyze this query: '{query}'. "
                f"What specific technologies, frameworks, or libraries are mentioned or implied? "
                f"List only the most relevant 1-2 technologies that would benefit from documentation. "
                f"If no specific technologies are clear, respond with 'NONE'.",
                thought_number=1,
                total_thoughts=1,
                next_thought_needed=False
            )
            
            analysis_text = tech_analysis.get('thought_analysis', '').strip()
            
            # If AI says no specific technologies, return empty
            if 'NONE' in analysis_text.upper() or not analysis_text:
                return []
            
            # Extract technologies from AI response
            # AI will naturally mention the technologies by name
            tech_names = []
            words = analysis_text.split()
            for i, word in enumerate(words):
                word_clean = word.strip('.,!?()[]{}"\'-')
                # If it looks like a technology name (capitalized, common patterns)
                if (len(word_clean) > 2 and 
                    (word_clean[0].isupper() or '.js' in word_clean or 'js' in word_clean)):
                    tech_names.append(word_clean)
            
            # Return first 2 unique technology names
            return list(set(tech_names))[:2]
            
        except Exception as e:
            print(f"[Smart MCP] AI tech extraction failed: {e}")
            return []
    
    def enhance_query_intelligently(self, query: str) -> str:
        """
        Completely AI-driven enhancement - zero hardcoded patterns
        """
        try:
            # Let AI decide everything - what it needs and how to enhance
            enhancement_decision = self.mcp_client.sequential_thinking(
                f"You are a {self.agent_domain} expert analyzing this query: '{query}'\n\n"
                f"DECISION FRAMEWORK:\n"
                f"1. Do you need additional context, documentation, or deep analysis?\n"
                f"2. If yes, what specific enhancement would improve your response?\n"
                f"3. Respond with either 'NO_ENHANCEMENT' or provide the specific enhancement text.\n"
                f"4. Be direct - no explanations, just the enhancement or 'NO_ENHANCEMENT'.",
                thought_number=1,
                total_thoughts=1,
                next_thought_needed=False
            )
            
            decision_text = enhancement_decision.get('thought_analysis', '').strip()
            
            # If AI says no enhancement needed, return original query
            if 'NO_ENHANCEMENT' in decision_text.upper():
                return query
            
            # If AI provides enhancement text, use it
            if decision_text and decision_text != query:
                enhanced_query = f"{query}\n\n{decision_text}"
                return enhanced_query
            
            # Fallback - return original
            return query
            
        except Exception as e:
            print(f"[Smart MCP] AI-driven enhancement failed: {e}")
            return query
    
    def get_enhancement_summary(self, original_query: str, enhanced_query: str) -> Dict[str, Any]:
        """Get summary of what enhancements were applied"""
        return {
            "original_length": len(original_query),
            "enhanced_length": len(enhanced_query),
            "enhancement_ratio": len(enhanced_query) / len(original_query) if original_query else 1,
            "has_analysis": "Expert Analysis" in enhanced_query,
            "has_documentation": "Context:" in enhanced_query,
            "has_examples": "Example:" in enhanced_query
        }

# Convenience function for easy import
def create_smart_enhancer(agent_domain: str) -> SmartMCPEnhancer:
    """Create a smart MCP enhancer for the given agent domain"""
    return SmartMCPEnhancer(agent_domain)
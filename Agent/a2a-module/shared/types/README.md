# Android XR Type Definitions

This directory contains TypeScript type definitions for the Android XR multi-agent system. These types are designed to improve AI maintainability, ensure consistency across agents, and provide clear contracts for inter-agent communication.

## Overview

The type system defines interfaces for:

### Core A2A Protocol
- `A2AMessage` - Standard A2A protocol message format
- `A2AResponse` - Response format for A2A communication
- `AgentCard` - Agent capability and metadata description

### Android XR Specific Types
- **Camera & Perception**: `CameraConfig`, `ROIConfig`, `CameraFrame`, `ROIResult`
- **Vision & AI**: `VLMRequest`, `VLMResponse`, `LLMRequest`, `LLMResponse`
- **UX/TTS & Audio**: `TTSRequest`, `TTSResponse`, `HUDElement`, `SpatialAudio`
- **Logging & Metrics**: `PerformanceMetrics`, `ProcessingMetrics`, `LogEntry`

### Agent Configuration
- `AgentConfig` - Base configuration for all agents
- Specialized configs: `PerceptionAgentConfig`, `VisionAgentConfig`, `UXTTSAgentConfig`, `LoggerAgentConfig`

### System Coordination
- `XRPipeline` - Processing pipeline definition
- `SystemStatus` - Overall system health and status
- `AgentStatus` - Individual agent status monitoring

## Usage

### In Python (via JSON Schema)
```python
# These types can be used to validate JSON data structures
from typing import TypedDict
from android_xr_types import VLMRequest, ProcessingResult

def process_vlm_request(request: VLMRequest) -> ProcessingResult:
    # Type-safe processing
    pass
```

### In TypeScript/JavaScript
```typescript
import { VLMRequest, AgentCard, HUDElement } from './android_xr_types';

const vlmRequest: VLMRequest = {
  imageData: "base64...",
  prompt: "Analyze this XR scene",
  model: "gpt-4v"
};
```

### For AI Agents
These types serve as documentation and contracts that AI agents can reference when:
- Processing A2A communication
- Generating code for Android XR applications
- Understanding data structures and workflows
- Maintaining consistency across the multi-agent system

## Benefits for AI Maintainability

1. **Clear Contracts**: Well-defined interfaces for all agent interactions
2. **Type Safety**: Prevent common errors in inter-agent communication
3. **Documentation**: Self-documenting code with clear type definitions
4. **Consistency**: Standardized data structures across all agents
5. **Evolution**: Easy to extend and modify while maintaining compatibility

## Agent-Specific Types

### Perception Agent (Port 8030)
- Camera configuration and frame processing
- ROI extraction and coordinate mapping
- Performance metrics for image processing

### Vision Agent (Port 8031)
- VLM/LLM request and response structures
- AI model configuration and metadata
- Processing metrics and token usage tracking

### UX/TTS Agent (Port 8032)
- TTS request configuration and audio output
- HUD element positioning and styling
- Spatial audio and user interaction tracking

### Logger Agent (Port 8033)
- Performance monitoring and metrics collection
- Error tracking and system health monitoring
- Data export formats and retention policies

## Constants

The file includes important constants for:
- Agent port numbers (`AGENT_PORTS`)
- A2A protocol endpoints (`A2A_ENDPOINTS`)
- XR performance targets (`XR_PERFORMANCE_TARGETS`)

## Integration with CLAUDE.md

These types complement the CLAUDE.md files in each agent directory by providing:
- Structured data contracts referenced in agent specifications
- Clear parameter types for A2A communication examples
- Type-safe examples for agent implementations

## Future Extensions

The type system is designed to be extensible for:
- Additional AI models and processing engines
- New XR interaction modalities (hand tracking, eye tracking)
- Enhanced analytics and monitoring capabilities
- Integration with external systems and APIs
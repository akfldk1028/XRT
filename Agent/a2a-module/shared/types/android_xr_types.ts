/**
 * Android XR Agent System - Type Definitions
 * 
 * This file defines TypeScript interfaces and types for the Android XR
 * multi-agent system to ensure type safety and improve AI maintainability.
 */

// ===== CORE A2A PROTOCOL TYPES =====

export interface A2AMessage {
  jsonrpc: "2.0";
  id: string;
  method: "message/send";
  params: {
    message: {
      messageId: string;
      taskId?: string;
      contextId?: string;
      role: "user" | "assistant" | "system";
      parts: MessagePart[];
      kind: "message";
    };
  };
}

export interface MessagePart {
  kind: "text" | "image" | "binary";
  text?: string;
  mimeType?: string;
  data?: string; // base64 encoded for binary data
}

export interface A2AResponse {
  jsonrpc: "2.0";
  id: string;
  result?: {
    artifacts: Artifact[];
    status: {
      message: {
        parts: MessagePart[];
      };
    };
  };
  error?: {
    code: number;
    message: string;
  };
}

export interface Artifact {
  parts: MessagePart[];
  metadata?: Record<string, any>;
}

// ===== AGENT CARD TYPES =====

export interface AgentCard {
  url: string;
  name: string;
  description: string;
  version: string;
  capabilities: AgentCapabilities;
  skills: AgentSkill[];
}

export interface AgentCapabilities {
  streaming: boolean;
  pushNotifications: boolean;
  stateTransitionHistory: boolean;
}

export interface AgentSkill {
  id: string;
  name: string;
  description: string;
  tags: string[];
  examples: string[];
}

// ===== ANDROID XR SPECIFIC TYPES =====

// Camera & Perception Types
export interface CameraConfig {
  resolution: {
    width: number;
    height: number;
  };
  frameRate: number;
  format: "YUV420" | "NV21" | "RGB888";
  autoFocus: boolean;
  exposureMode: "auto" | "manual";
}

export interface ROIConfig {
  x: number;
  y: number;
  width: number;
  height: number;
  priority: "high" | "medium" | "low";
}

export interface CameraFrame {
  timestamp: number;
  frameId: string;
  data: string; // base64 encoded image data
  format: string;
  metadata: {
    exposure: number;
    iso: number;
    focusDistance: number;
  };
}

export interface ROIResult {
  frameId: string;
  roi: ROIConfig;
  extractedImage: string; // base64 encoded
  processingTime: number;
  metadata: Record<string, any>;
}

// Vision & AI Processing Types
export interface VLMRequest {
  imageData: string; // base64 encoded
  prompt: string;
  model: "gpt-4v" | "moondream" | "qwen2.5-vl" | "vila" | "custom";
  maxTokens?: number;
  temperature?: number;
}

export interface VLMResponse {
  requestId: string;
  model: string;
  response: string;
  confidence: number;
  processingTime: number;
  tokensUsed: number;
  metadata: {
    objectsDetected?: string[];
    sceneDescription?: string;
    emotions?: string[];
  };
}

export interface LLMRequest {
  text: string;
  context?: string;
  model: "gpt-4" | "claude" | "gemini" | "custom";
  maxTokens?: number;
  temperature?: number;
}

export interface LLMResponse {
  requestId: string;
  model: string;
  response: string;
  processingTime: number;
  tokensUsed: number;
  reasoning?: string;
}

// UX/TTS & Audio Types
export interface TTSRequest {
  text: string;
  voice: string;
  speed: number;
  pitch: number;
  volume: number;
  language: string;
  engine: "android-tts" | "coqui" | "elevenlabs" | "edge-tts";
}

export interface TTSResponse {
  requestId: string;
  audioData: string; // base64 encoded audio
  duration: number;
  format: "mp3" | "wav" | "ogg";
  processingTime: number;
  metadata: {
    voice: string;
    language: string;
    sampleRate: number;
  };
}

export interface HUDElement {
  id: string;
  type: "crosshair" | "text" | "notification" | "menu" | "progress";
  position: {
    x: number;
    y: number;
    z?: number; // depth for 3D positioning
  };
  size: {
    width: number;
    height: number;
  };
  content: string | HUDContent;
  style: HUDStyle;
  visible: boolean;
  priority: number;
}

export interface HUDContent {
  text?: string;
  icon?: string;
  color?: string;
  animation?: "fade" | "slide" | "pulse" | "none";
}

export interface HUDStyle {
  backgroundColor?: string;
  textColor?: string;
  fontSize?: number;
  opacity?: number;
  borderRadius?: number;
  border?: string;
}

export interface SpatialAudio {
  position: {
    x: number;
    y: number;
    z: number;
  };
  volume: number;
  distance: number;
  directional: boolean;
}

// Logging & Metrics Types
export interface PerformanceMetrics {
  timestamp: number;
  frameRate: number;
  memoryUsage: {
    used: number;
    available: number;
    peak: number;
  };
  cpuUsage: number;
  batteryLevel?: number;
  temperature?: number;
}

export interface ProcessingMetrics {
  agentId: string;
  taskId: string;
  startTime: number;
  endTime: number;
  duration: number;
  success: boolean;
  errorMessage?: string;
  resourceUsage: {
    memory: number;
    cpu: number;
  };
}

export interface UserInteractionMetrics {
  sessionId: string;
  timestamp: number;
  interactionType: "gaze" | "gesture" | "voice" | "touch";
  elementId?: string;
  position?: { x: number; y: number };
  duration?: number;
  success: boolean;
}

export interface LogEntry {
  timestamp: number;
  level: "debug" | "info" | "warn" | "error" | "fatal";
  category: string;
  message: string;
  metadata?: Record<string, any>;
  agentId?: string;
  sessionId?: string;
}

export interface MetricsExport {
  format: "json" | "csv" | "prometheus";
  timeRange: {
    start: number;
    end: number;
  };
  metrics: string[];
  filters?: Record<string, any>;
}

// ===== AGENT CONFIGURATION TYPES =====

export interface AgentConfig {
  agentId: string;
  port: number;
  host: string;
  description: string;
  capabilities: string[];
  dependencies: string[];
  healthCheckInterval: number;
  maxConcurrentTasks: number;
}

export interface PerceptionAgentConfig extends AgentConfig {
  camera: CameraConfig;
  roiPresets: ROIConfig[];
  processingThreads: number;
  bufferSize: number;
}

export interface VisionAgentConfig extends AgentConfig {
  defaultVLMModel: string;
  defaultLLMModel: string;
  apiKeys: Record<string, string>;
  modelEndpoints: Record<string, string>;
  timeout: number;
  retryAttempts: number;
}

export interface UXTTSAgentConfig extends AgentConfig {
  defaultTTSEngine: string;
  defaultVoice: string;
  hudSettings: {
    defaultPosition: { x: number; y: number };
    maxElements: number;
    refreshRate: number;
  };
  audioSettings: {
    sampleRate: number;
    channels: number;
    bufferSize: number;
  };
}

export interface LoggerAgentConfig extends AgentConfig {
  logLevel: "debug" | "info" | "warn" | "error";
  logRotation: {
    maxSize: string;
    maxFiles: number;
    compress: boolean;
  };
  metricsRetention: {
    days: number;
    maxRecords: number;
  };
  exportFormats: string[];
}

// ===== SYSTEM COORDINATION TYPES =====

export interface XRPipeline {
  id: string;
  name: string;
  agents: string[];
  dataFlow: PipelineStep[];
  configuration: Record<string, any>;
  status: "running" | "stopped" | "error" | "paused";
}

export interface PipelineStep {
  stepId: string;
  agentId: string;
  inputType: string;
  outputType: string;
  dependencies: string[];
  timeout: number;
  retryPolicy: {
    maxAttempts: number;
    backoffMs: number;
  };
}

export interface SystemStatus {
  timestamp: number;
  agents: AgentStatus[];
  pipeline: XRPipeline;
  performance: PerformanceMetrics;
  errors: ErrorReport[];
}

export interface AgentStatus {
  agentId: string;
  status: "online" | "offline" | "error" | "busy";
  uptime: number;
  taskQueue: number;
  lastHeartbeat: number;
  health: {
    cpu: number;
    memory: number;
    responseTime: number;
  };
}

export interface ErrorReport {
  timestamp: number;
  agentId: string;
  errorType: string;
  message: string;
  stack?: string;
  context: Record<string, any>;
  severity: "low" | "medium" | "high" | "critical";
}

// ===== UTILITY TYPES =====

export type XRAgentType = "perception" | "vision" | "ux_tts" | "logger";

export type ProcessingResult<T = any> = {
  success: boolean;
  data?: T;
  error?: string;
  processingTime: number;
  metadata?: Record<string, any>;
};

export type AgentResponse<T = any> = {
  is_task_complete: boolean;
  require_user_input: boolean;
  content: string;
  data?: T;
  metadata?: Record<string, any>;
};

// ===== CONSTANTS =====

export const AGENT_PORTS = {
  PERCEPTION: 8030,
  VISION: 8031,
  UX_TTS: 8032,
  LOGGER: 8033,
} as const;

export const A2A_ENDPOINTS = {
  AGENT_CARD: "/.well-known/agent.json",
  MESSAGE: "/",
  HEALTH: "/health",
  METRICS: "/metrics",
} as const;

export const XR_PERFORMANCE_TARGETS = {
  MIN_FPS: 60,
  MAX_LATENCY_MS: 50,
  MAX_MEMORY_MB: 512,
  MAX_CPU_USAGE: 70,
} as const;
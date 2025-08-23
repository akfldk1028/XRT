"""
OpenAI GPT-4V Integration for Android XR Scene Analysis
"""

import base64
import json
import logging
from typing import Dict, List, Optional, Any, Union
from dataclasses import dataclass
from enum import Enum
import asyncio
from pathlib import Path
import numpy as np
from PIL import Image
import io
import aiohttp
from datetime import datetime

logger = logging.getLogger(__name__)


class AnalysisMode(Enum):
    """Scene analysis modes for different XR scenarios"""
    OBJECT_DETECTION = "object_detection"
    SCENE_UNDERSTANDING = "scene_understanding"
    SPATIAL_MAPPING = "spatial_mapping"
    GESTURE_RECOGNITION = "gesture_recognition"
    FACE_ANALYSIS = "face_analysis"
    TEXT_EXTRACTION = "text_extraction"
    SAFETY_CHECK = "safety_check"
    INTERACTION_ANALYSIS = "interaction_analysis"


@dataclass
class XRSceneContext:
    """Context information for XR scene analysis"""
    device_pose: Optional[Dict[str, float]] = None
    field_of_view: float = 90.0
    timestamp: Optional[datetime] = None
    session_id: Optional[str] = None
    interaction_mode: Optional[str] = None
    user_intent: Optional[str] = None
    spatial_anchors: Optional[List[Dict]] = None
    environment_type: Optional[str] = None


@dataclass
class AnalysisResult:
    """Result structure for scene analysis"""
    success: bool
    mode: AnalysisMode
    timestamp: datetime
    raw_response: Dict[str, Any]
    parsed_data: Dict[str, Any]
    confidence_score: float
    processing_time: float
    error_message: Optional[str] = None
    suggestions: Optional[List[str]] = None


class ImagePreprocessor:
    """Handles image preprocessing for GPT-4V"""
    
    def __init__(self, max_size: tuple = (1024, 1024), quality: int = 85):
        self.max_size = max_size
        self.quality = quality
        
    def preprocess_image(self, image_data: Union[bytes, np.ndarray, Image.Image]) -> str:
        """
        Preprocess image for GPT-4V API
        Returns base64 encoded string
        """
        try:
            if isinstance(image_data, bytes):
                image = Image.open(io.BytesIO(image_data))
            elif isinstance(image_data, np.ndarray):
                image = Image.fromarray(image_data)
            elif isinstance(image_data, Image.Image):
                image = image_data
            else:
                raise ValueError(f"Unsupported image type: {type(image_data)}")
            
            if image.mode not in ('RGB', 'RGBA'):
                image = image.convert('RGB')
            
            image.thumbnail(self.max_size, Image.Resampling.LANCZOS)
            
            buffer = io.BytesIO()
            image.save(buffer, format='JPEG', quality=self.quality, optimize=True)
            
            base64_image = base64.b64encode(buffer.getvalue()).decode('utf-8')
            return base64_image
            
        except Exception as e:
            logger.error(f"Image preprocessing failed: {e}")
            raise
    
    def prepare_multiple_frames(self, frames: List[Union[bytes, np.ndarray]], 
                              max_frames: int = 5) -> List[str]:
        """
        Prepare multiple frames for temporal analysis
        """
        if len(frames) > max_frames:
            step = len(frames) // max_frames
            frames = frames[::step][:max_frames]
        
        processed_frames = []
        for frame in frames:
            try:
                processed = self.preprocess_image(frame)
                processed_frames.append(processed)
            except Exception as e:
                logger.warning(f"Failed to process frame: {e}")
                continue
        
        return processed_frames
    
    def extract_roi(self, image: Union[bytes, np.ndarray], 
                   bbox: Dict[str, int]) -> Union[bytes, np.ndarray]:
        """
        Extract region of interest from image
        """
        if isinstance(image, bytes):
            img = Image.open(io.BytesIO(image))
        else:
            img = Image.fromarray(image)
        
        x, y, w, h = bbox['x'], bbox['y'], bbox['width'], bbox['height']
        roi = img.crop((x, y, x + w, y + h))
        
        buffer = io.BytesIO()
        roi.save(buffer, format='JPEG')
        return buffer.getvalue()


class GPT4VClient:
    """Client for OpenAI GPT-4V API"""
    
    def __init__(self, api_key: str, base_url: str = "https://api.openai.com/v1"):
        self.api_key = api_key
        self.base_url = base_url
        self.preprocessor = ImagePreprocessor()
        self.session = None
        
    async def __aenter__(self):
        self.session = aiohttp.ClientSession()
        return self
        
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()
    
    async def analyze_scene(self, 
                           image: Union[bytes, np.ndarray, str],
                           mode: AnalysisMode,
                           context: Optional[XRSceneContext] = None,
                           custom_prompt: Optional[str] = None) -> AnalysisResult:
        """
        Analyze XR scene using GPT-4V
        """
        start_time = datetime.now()
        
        try:
            if isinstance(image, str):
                base64_image = image
            else:
                base64_image = self.preprocessor.preprocess_image(image)
            
            prompt = self._build_prompt(mode, context, custom_prompt)
            
            messages = self._construct_messages(base64_image, prompt)
            
            response = await self._send_request(messages)
            
            parsed_data = self._parse_response(response, mode)
            
            processing_time = (datetime.now() - start_time).total_seconds()
            
            return AnalysisResult(
                success=True,
                mode=mode,
                timestamp=datetime.now(),
                raw_response=response,
                parsed_data=parsed_data,
                confidence_score=self._calculate_confidence(response),
                processing_time=processing_time,
                suggestions=self._extract_suggestions(response, mode)
            )
            
        except Exception as e:
            logger.error(f"Scene analysis failed: {e}")
            return AnalysisResult(
                success=False,
                mode=mode,
                timestamp=datetime.now(),
                raw_response={},
                parsed_data={},
                confidence_score=0.0,
                processing_time=(datetime.now() - start_time).total_seconds(),
                error_message=str(e)
            )
    
    def _build_prompt(self, mode: AnalysisMode, 
                     context: Optional[XRSceneContext],
                     custom_prompt: Optional[str]) -> str:
        """Build analysis prompt based on mode and context"""
        
        base_prompts = {
            AnalysisMode.OBJECT_DETECTION: """
                Analyze this Android XR scene and identify all visible objects.
                For each object provide:
                - Object type and label
                - Approximate 3D position relative to camera
                - Bounding box coordinates
                - Confidence level
                - Material/texture description
                - Interaction affordances
                Format response as JSON.
            """,
            
            AnalysisMode.SCENE_UNDERSTANDING: """
                Provide comprehensive scene understanding for this Android XR environment:
                - Scene type and setting
                - Spatial layout description
                - Lighting conditions
                - Key landmarks and features
                - Potential hazards or obstacles
                - Suggested AR overlay positions
                - Environmental context
                Format response as structured JSON.
            """,
            
            AnalysisMode.SPATIAL_MAPPING: """
                Analyze spatial relationships in this XR scene:
                - Depth estimation for key regions
                - Surface planes (floor, walls, ceiling)
                - Spatial boundaries
                - Occlusion relationships
                - Recommended anchor points for AR objects
                - Movement paths and navigable areas
                Format as JSON with spatial coordinates.
            """,
            
            AnalysisMode.GESTURE_RECOGNITION: """
                Detect and analyze hand gestures or body poses:
                - Gesture type identified
                - Hand/body keypoints
                - Motion trajectory
                - Gesture confidence score
                - Suggested interaction mapping
                Format as JSON.
            """,
            
            AnalysisMode.FACE_ANALYSIS: """
                Analyze faces in the XR scene:
                - Face detection and landmarks
                - Gaze direction
                - Emotional state indicators
                - Head pose estimation
                - Identity verification readiness
                Ensure privacy-conscious analysis. Format as JSON.
            """,
            
            AnalysisMode.TEXT_EXTRACTION: """
                Extract and analyze text in the scene:
                - Detected text content
                - Text location and orientation
                - Language identification
                - Font characteristics
                - Readability score
                - Suggested AR text overlays
                Format as JSON.
            """,
            
            AnalysisMode.SAFETY_CHECK: """
                Perform safety analysis for XR interaction:
                - Collision risks
                - Environmental hazards
                - User comfort factors
                - Boundary violations
                - Recommended safety zones
                - Motion sickness risk factors
                Format as JSON with risk levels.
            """,
            
            AnalysisMode.INTERACTION_ANALYSIS: """
                Analyze potential interactions in the XR scene:
                - Interactable objects
                - Suggested interaction methods
                - User attention targets
                - Engagement opportunities
                - UI placement recommendations
                - Accessibility considerations
                Format as JSON.
            """
        }
        
        prompt = base_prompts.get(mode, "Analyze this XR scene.")
        
        if context:
            context_info = f"\nContext: "
            if context.device_pose:
                context_info += f"Device pose: {context.device_pose}. "
            if context.user_intent:
                context_info += f"User intent: {context.user_intent}. "
            if context.environment_type:
                context_info += f"Environment: {context.environment_type}. "
            prompt += context_info
        
        if custom_prompt:
            prompt += f"\nAdditional requirements: {custom_prompt}"
        
        return prompt
    
    def _construct_messages(self, base64_image: str, prompt: str) -> List[Dict]:
        """Construct messages for GPT-4V API"""
        return [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}",
                            "detail": "high"
                        }
                    }
                ]
            }
        ]
    
    async def _send_request(self, messages: List[Dict]) -> Dict:
        """Send request to OpenAI API"""
        if not self.session:
            self.session = aiohttp.ClientSession()
        
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "model": "gpt-4-vision-preview",
            "messages": messages,
            "max_tokens": 4096,
            "temperature": 0.3
        }
        
        async with self.session.post(
            f"{self.base_url}/chat/completions",
            headers=headers,
            json=payload
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                raise Exception(f"API request failed: {response.status} - {error_text}")
            
            return await response.json()
    
    def _parse_response(self, response: Dict, mode: AnalysisMode) -> Dict:
        """Parse GPT-4V response based on analysis mode"""
        try:
            content = response['choices'][0]['message']['content']
            
            if content.strip().startswith('{'):
                return json.loads(content)
            
            json_start = content.find('{')
            json_end = content.rfind('}') + 1
            if json_start != -1 and json_end > json_start:
                json_str = content[json_start:json_end]
                return json.loads(json_str)
            
            return {"raw_text": content}
            
        except Exception as e:
            logger.warning(f"Failed to parse JSON response: {e}")
            return {"raw_text": response.get('choices', [{}])[0].get('message', {}).get('content', '')}
    
    def _calculate_confidence(self, response: Dict) -> float:
        """Calculate confidence score from response"""
        try:
            content = response['choices'][0]['message']['content']
            
            if 'confidence' in content.lower():
                import re
                matches = re.findall(r'confidence[:\s]+(\d+\.?\d*)', content.lower())
                if matches:
                    return min(float(matches[0]) / 100 if float(matches[0]) > 1 else float(matches[0]), 1.0)
            
            finish_reason = response['choices'][0].get('finish_reason', '')
            if finish_reason == 'stop':
                return 0.85
            elif finish_reason == 'length':
                return 0.7
            else:
                return 0.6
                
        except Exception:
            return 0.5
    
    def _extract_suggestions(self, response: Dict, mode: AnalysisMode) -> List[str]:
        """Extract actionable suggestions from response"""
        suggestions = []
        
        try:
            content = response['choices'][0]['message']['content']
            
            if 'suggest' in content.lower() or 'recommend' in content.lower():
                lines = content.split('\n')
                for line in lines:
                    if any(word in line.lower() for word in ['suggest', 'recommend', 'should', 'could']):
                        suggestions.append(line.strip())
            
            if mode == AnalysisMode.SAFETY_CHECK and 'risk' in content.lower():
                suggestions.append("Review identified risks before proceeding")
            
            if mode == AnalysisMode.INTERACTION_ANALYSIS and 'interact' in content.lower():
                suggestions.append("Enable suggested interaction methods")
                
        except Exception as e:
            logger.warning(f"Failed to extract suggestions: {e}")
        
        return suggestions[:5]


class XRSceneAnalyzer:
    """High-level Android XR scene analyzer using GPT-4V"""
    
    def __init__(self, api_key: str):
        self.client = GPT4VClient(api_key)
        self.analysis_history = []
        self.cache = {}
        
    async def analyze_frame(self, 
                           frame: Union[bytes, np.ndarray],
                           modes: List[AnalysisMode],
                           context: Optional[XRSceneContext] = None) -> Dict[str, AnalysisResult]:
        """
        Analyze single frame with multiple analysis modes
        """
        results = {}
        
        async with self.client:
            tasks = []
            for mode in modes:
                task = self.client.analyze_scene(frame, mode, context)
                tasks.append(task)
            
            completed_results = await asyncio.gather(*tasks)
            
            for mode, result in zip(modes, completed_results):
                results[mode.value] = result
                self.analysis_history.append(result)
        
        return results
    
    async def analyze_stream(self,
                            frame_generator,
                            mode: AnalysisMode,
                            context: Optional[XRSceneContext] = None,
                            frame_interval: int = 30):
        """
        Analyze video stream with specified frame interval
        """
        frame_count = 0
        
        async with self.client:
            async for frame in frame_generator:
                if frame_count % frame_interval == 0:
                    result = await self.client.analyze_scene(frame, mode, context)
                    yield result
                
                frame_count += 1
    
    async def batch_analyze(self,
                           frames: List[Union[bytes, np.ndarray]],
                           mode: AnalysisMode,
                           context: Optional[XRSceneContext] = None) -> List[AnalysisResult]:
        """
        Batch analyze multiple frames
        """
        results = []
        
        async with self.client:
            tasks = []
            for frame in frames:
                task = self.client.analyze_scene(frame, mode, context)
                tasks.append(task)
            
            results = await asyncio.gather(*tasks)
        
        return results
    
    def get_analysis_summary(self) -> Dict:
        """
        Get summary of all analyses performed
        """
        if not self.analysis_history:
            return {}
        
        summary = {
            'total_analyses': len(self.analysis_history),
            'successful': sum(1 for r in self.analysis_history if r.success),
            'failed': sum(1 for r in self.analysis_history if not r.success),
            'average_confidence': np.mean([r.confidence_score for r in self.analysis_history]),
            'average_processing_time': np.mean([r.processing_time for r in self.analysis_history]),
            'modes_used': list(set(r.mode.value for r in self.analysis_history))
        }
        
        return summary


class ResponseHandler:
    """Handles and processes GPT-4V responses for Android XR"""
    
    @staticmethod
    def extract_objects(analysis_result: AnalysisResult) -> List[Dict]:
        """Extract detected objects from analysis"""
        if not analysis_result.success:
            return []
        
        objects = []
        parsed = analysis_result.parsed_data
        
        if 'objects' in parsed:
            objects = parsed['objects']
        elif 'detections' in parsed:
            objects = parsed['detections']
        elif isinstance(parsed, list):
            objects = parsed
        
        return objects
    
    @staticmethod
    def get_spatial_data(analysis_result: AnalysisResult) -> Dict:
        """Extract spatial mapping data"""
        if not analysis_result.success:
            return {}
        
        spatial_data = {
            'planes': [],
            'anchors': [],
            'depth_map': None,
            'boundaries': []
        }
        
        parsed = analysis_result.parsed_data
        
        if 'planes' in parsed:
            spatial_data['planes'] = parsed['planes']
        if 'anchors' in parsed or 'anchor_points' in parsed:
            spatial_data['anchors'] = parsed.get('anchors', parsed.get('anchor_points', []))
        if 'depth' in parsed:
            spatial_data['depth_map'] = parsed['depth']
        if 'boundaries' in parsed:
            spatial_data['boundaries'] = parsed['boundaries']
        
        return spatial_data
    
    @staticmethod
    def get_interaction_targets(analysis_result: AnalysisResult) -> List[Dict]:
        """Extract interactable targets from analysis"""
        if not analysis_result.success:
            return []
        
        targets = []
        parsed = analysis_result.parsed_data
        
        if 'interactions' in parsed:
            targets = parsed['interactions']
        elif 'interactable_objects' in parsed:
            targets = parsed['interactable_objects']
        
        for target in targets:
            if 'interaction_type' not in target:
                target['interaction_type'] = 'touch'
            if 'priority' not in target:
                target['priority'] = 'medium'
        
        return targets
    
    @staticmethod
    def format_for_unity(analysis_result: AnalysisResult) -> str:
        """Format analysis result for Unity integration"""
        if not analysis_result.success:
            return json.dumps({'error': analysis_result.error_message})
        
        unity_data = {
            'timestamp': analysis_result.timestamp.isoformat(),
            'mode': analysis_result.mode.value,
            'confidence': analysis_result.confidence_score,
            'data': analysis_result.parsed_data,
            'suggestions': analysis_result.suggestions or []
        }
        
        return json.dumps(unity_data)
    
    @staticmethod
    def format_for_android(analysis_result: AnalysisResult) -> Dict:
        """Format analysis result for Android XR SDK"""
        if not analysis_result.success:
            return {
                'status': 'error',
                'message': analysis_result.error_message
            }
        
        return {
            'status': 'success',
            'analysisMode': analysis_result.mode.value,
            'confidence': float(analysis_result.confidence_score),
            'processingTimeMs': int(analysis_result.processing_time * 1000),
            'results': analysis_result.parsed_data,
            'recommendations': analysis_result.suggestions or []
        }


async def example_usage():
    """Example usage of GPT-4V integration for Android XR"""
    
    api_key = "your-openai-api-key"
    
    analyzer = XRSceneAnalyzer(api_key)
    
    with open("sample_xr_scene.jpg", "rb") as f:
        image_data = f.read()
    
    context = XRSceneContext(
        device_pose={'x': 0, 'y': 1.6, 'z': 0, 'pitch': 0, 'yaw': 0, 'roll': 0},
        field_of_view=90.0,
        environment_type="indoor",
        user_intent="identify objects for interaction"
    )
    
    results = await analyzer.analyze_frame(
        image_data,
        modes=[
            AnalysisMode.OBJECT_DETECTION,
            AnalysisMode.SCENE_UNDERSTANDING,
            AnalysisMode.INTERACTION_ANALYSIS
        ],
        context=context
    )
    
    for mode_name, result in results.items():
        print(f"\n{mode_name} Analysis:")
        print(f"Success: {result.success}")
        print(f"Confidence: {result.confidence_score:.2f}")
        print(f"Processing Time: {result.processing_time:.2f}s")
        
        if result.success:
            handler = ResponseHandler()
            
            if result.mode == AnalysisMode.OBJECT_DETECTION:
                objects = handler.extract_objects(result)
                print(f"Detected {len(objects)} objects")
                
            elif result.mode == AnalysisMode.INTERACTION_ANALYSIS:
                targets = handler.get_interaction_targets(result)
                print(f"Found {len(targets)} interaction targets")
            
            android_format = handler.format_for_android(result)
            print(f"Android format: {json.dumps(android_format, indent=2)}")
    
    summary = analyzer.get_analysis_summary()
    print(f"\nAnalysis Summary: {json.dumps(summary, indent=2)}")


if __name__ == "__main__":
    asyncio.run(example_usage())
"""
ROI (Region of Interest) Processor for AI Vision Analysis
A2A Protocol Implementation for Android XR
"""

import asyncio
import json
import logging
import time
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Tuple, Any, Union
from enum import Enum
import numpy as np
from PIL import Image
import io
import base64

logger = logging.getLogger(__name__)


class ROIAnalysisMode(Enum):
    """ROI analysis modes for different use cases"""
    OBJECT_DETECTION = "object_detection"
    TEXT_RECOGNITION = "text_recognition"
    FACE_ANALYSIS = "face_analysis"
    SCENE_UNDERSTANDING = "scene_understanding"
    GESTURE_RECOGNITION = "gesture_recognition"
    DEPTH_ESTIMATION = "depth_estimation"


@dataclass
class ROICoordinates:
    """Region of Interest coordinates"""
    x: int
    y: int
    width: int
    height: int
    
    def to_dict(self) -> Dict:
        return asdict(self)
    
    def get_bounds(self) -> Tuple[int, int, int, int]:
        """Get (left, top, right, bottom) bounds"""
        return (self.x, self.y, self.x + self.width, self.y + self.height)
    
    def get_center(self) -> Tuple[float, float]:
        """Get center point of ROI"""
        return (self.x + self.width / 2, self.y + self.height / 2)
    
    def get_area(self) -> int:
        """Calculate area of ROI"""
        return self.width * self.height
    
    def expand(self, factor: float = 1.2) -> 'ROICoordinates':
        """Expand ROI by given factor"""
        new_width = int(self.width * factor)
        new_height = int(self.height * factor)
        dx = (new_width - self.width) // 2
        dy = (new_height - self.height) // 2
        
        return ROICoordinates(
            x=max(0, self.x - dx),
            y=max(0, self.y - dy),
            width=new_width,
            height=new_height
        )
    
    def intersects(self, other: 'ROICoordinates') -> bool:
        """Check if this ROI intersects with another"""
        x1, y1, x2, y2 = self.get_bounds()
        ox1, oy1, ox2, oy2 = other.get_bounds()
        
        return not (x2 < ox1 or x1 > ox2 or y2 < oy1 or y1 > oy2)
    
    def intersection(self, other: 'ROICoordinates') -> Optional['ROICoordinates']:
        """Get intersection with another ROI"""
        if not self.intersects(other):
            return None
        
        x1, y1, x2, y2 = self.get_bounds()
        ox1, oy1, ox2, oy2 = other.get_bounds()
        
        int_x1 = max(x1, ox1)
        int_y1 = max(y1, oy1)
        int_x2 = min(x2, ox2)
        int_y2 = min(y2, oy2)
        
        return ROICoordinates(
            x=int_x1,
            y=int_y1,
            width=int_x2 - int_x1,
            height=int_y2 - int_y1
        )


@dataclass
class ROIAnalysisResult:
    """Result of ROI analysis"""
    roi: ROICoordinates
    mode: ROIAnalysisMode
    confidence: float
    labels: List[str]
    features: Dict[str, Any]
    timestamp: float
    processing_time: float
    metadata: Optional[Dict] = None
    
    def to_dict(self) -> Dict:
        return {
            'roi': self.roi.to_dict(),
            'mode': self.mode.value,
            'confidence': self.confidence,
            'labels': self.labels,
            'features': self.features,
            'timestamp': self.timestamp,
            'processing_time': self.processing_time,
            'metadata': self.metadata or {}
        }
    
    def to_a2a_format(self) -> Dict:
        """Convert to A2A protocol format"""
        return {
            'type': 'vision_analysis',
            'subtype': 'roi_processing',
            'data': {
                'region': self.roi.to_dict(),
                'analysis': {
                    'mode': self.mode.value,
                    'confidence': self.confidence,
                    'primary_label': self.labels[0] if self.labels else 'unknown',
                    'all_labels': self.labels,
                    'features': self.features
                },
                'timing': {
                    'timestamp': self.timestamp,
                    'processing_ms': self.processing_time * 1000
                },
                'metadata': self.metadata
            }
        }


class ROIProcessor:
    """Main ROI processor for AI vision analysis"""
    
    def __init__(self, bridge=None):
        self.bridge = bridge  # Android XR Bridge if available
        self.analysis_cache = {}
        self.cache_ttl = 5.0  # Cache for 5 seconds
        self.performance_metrics = {
            'total_processed': 0,
            'average_time': 0.0,
            'last_fps': 0.0
        }
        
    async def process_roi(self, 
                         image_data: Union[bytes, np.ndarray, str],
                         roi_coords: Union[ROICoordinates, Dict],
                         mode: ROIAnalysisMode = ROIAnalysisMode.OBJECT_DETECTION,
                         options: Optional[Dict] = None) -> ROIAnalysisResult:
        """
        Process ROI for AI analysis
        
        Args:
            image_data: Image as bytes, numpy array, or base64 string
            roi_coords: ROI coordinates object or dict
            mode: Analysis mode
            options: Additional processing options
        
        Returns:
            ROIAnalysisResult with analysis data
        """
        start_time = time.time()
        
        # Convert ROI coordinates if needed
        if isinstance(roi_coords, dict):
            roi = ROICoordinates(**roi_coords)
        else:
            roi = roi_coords
        
        # Check cache
        cache_key = self._generate_cache_key(roi, mode)
        cached_result = self._get_cached_result(cache_key)
        if cached_result:
            logger.info(f"Using cached result for ROI {roi.to_dict()}")
            return cached_result
        
        # Extract ROI from image
        roi_image = await self._extract_roi(image_data, roi)
        
        # Perform AI analysis based on mode
        analysis_result = await self._analyze_roi(roi_image, roi, mode, options)
        
        # Calculate processing time
        processing_time = time.time() - start_time
        
        # Create result
        result = ROIAnalysisResult(
            roi=roi,
            mode=mode,
            confidence=analysis_result.get('confidence', 0.0),
            labels=analysis_result.get('labels', []),
            features=analysis_result.get('features', {}),
            timestamp=time.time(),
            processing_time=processing_time,
            metadata=analysis_result.get('metadata')
        )
        
        # Cache result
        self._cache_result(cache_key, result)
        
        # Update metrics
        self._update_metrics(processing_time)
        
        # Send to Android XR if bridge available
        if self.bridge:
            await self._send_to_android_xr(result)
        
        logger.info(f"Processed ROI in {processing_time:.3f}s: {result.labels[:3]}")
        
        return result
    
    async def process_multiple_rois(self,
                                   image_data: Union[bytes, np.ndarray, str],
                                   roi_list: List[Union[ROICoordinates, Dict]],
                                   mode: ROIAnalysisMode = ROIAnalysisMode.OBJECT_DETECTION,
                                   parallel: bool = True) -> List[ROIAnalysisResult]:
        """Process multiple ROIs from the same image"""
        
        if parallel:
            # Process ROIs in parallel
            tasks = [
                self.process_roi(image_data, roi, mode)
                for roi in roi_list
            ]
            results = await asyncio.gather(*tasks)
        else:
            # Process ROIs sequentially
            results = []
            for roi in roi_list:
                result = await self.process_roi(image_data, roi, mode)
                results.append(result)
        
        return results
    
    async def _extract_roi(self, image_data: Union[bytes, np.ndarray, str], 
                          roi: ROICoordinates) -> np.ndarray:
        """Extract ROI region from image"""
        
        # Convert image data to numpy array
        if isinstance(image_data, bytes):
            image = Image.open(io.BytesIO(image_data))
            img_array = np.array(image)
        elif isinstance(image_data, str):
            # Assume base64 encoded
            img_bytes = base64.b64decode(image_data)
            image = Image.open(io.BytesIO(img_bytes))
            img_array = np.array(image)
        else:
            img_array = image_data
        
        # Extract ROI
        x1, y1, x2, y2 = roi.get_bounds()
        
        # Ensure bounds are within image
        h, w = img_array.shape[:2]
        x1 = max(0, min(x1, w))
        x2 = max(0, min(x2, w))
        y1 = max(0, min(y1, h))
        y2 = max(0, min(y2, h))
        
        roi_image = img_array[y1:y2, x1:x2]
        
        return roi_image
    
    async def _analyze_roi(self, roi_image: np.ndarray, 
                          roi: ROICoordinates,
                          mode: ROIAnalysisMode,
                          options: Optional[Dict]) -> Dict:
        """Perform AI analysis on ROI"""
        
        # This is where you'd integrate with GPT-4V or other vision models
        # For now, returning simulated analysis results
        
        analysis = {
            'confidence': 0.95,
            'labels': [],
            'features': {},
            'metadata': {}
        }
        
        if mode == ROIAnalysisMode.OBJECT_DETECTION:
            analysis['labels'] = ['object', 'interactive_element', 'ui_component']
            analysis['features'] = {
                'object_class': 'button',
                'bounding_box': roi.to_dict(),
                'interaction_type': 'tap',
                'state': 'enabled'
            }
            
        elif mode == ROIAnalysisMode.TEXT_RECOGNITION:
            analysis['labels'] = ['text', 'readable']
            analysis['features'] = {
                'text_content': 'Sample Text',
                'language': 'en',
                'font_size': 14,
                'text_color': '#000000'
            }
            
        elif mode == ROIAnalysisMode.FACE_ANALYSIS:
            analysis['labels'] = ['face', 'person']
            analysis['features'] = {
                'face_id': 'face_001',
                'emotion': 'neutral',
                'age_estimate': 25,
                'gaze_direction': [0, 0, 1]
            }
            
        elif mode == ROIAnalysisMode.SCENE_UNDERSTANDING:
            analysis['labels'] = ['indoor', 'room', 'office']
            analysis['features'] = {
                'scene_type': 'workspace',
                'objects_detected': ['desk', 'monitor', 'chair'],
                'lighting': 'artificial',
                'depth_map': self._generate_depth_placeholder(roi_image.shape[:2])
            }
            
        elif mode == ROIAnalysisMode.GESTURE_RECOGNITION:
            analysis['labels'] = ['hand', 'gesture']
            analysis['features'] = {
                'gesture_type': 'pinch',
                'hand_side': 'right',
                'confidence': 0.92,
                'keypoints': self._generate_keypoints_placeholder()
            }
            
        elif mode == ROIAnalysisMode.DEPTH_ESTIMATION:
            analysis['labels'] = ['depth_map']
            analysis['features'] = {
                'min_depth': 0.5,
                'max_depth': 10.0,
                'average_depth': 2.5,
                'depth_data': self._generate_depth_placeholder(roi_image.shape[:2])
            }
        
        # Add ROI-specific metadata
        analysis['metadata'] = {
            'roi_area': roi.get_area(),
            'roi_center': roi.get_center(),
            'image_shape': roi_image.shape,
            'processing_mode': mode.value
        }
        
        if options:
            analysis['metadata'].update(options)
        
        return analysis
    
    def _generate_depth_placeholder(self, shape: Tuple[int, int]) -> List[List[float]]:
        """Generate placeholder depth data"""
        # In real implementation, this would be actual depth data
        h, w = shape
        # Create simplified depth representation
        return [[2.5] * min(w, 10)] * min(h, 10)
    
    def _generate_keypoints_placeholder(self) -> List[Dict]:
        """Generate placeholder keypoints for gesture"""
        # In real implementation, this would be actual hand keypoints
        keypoints = []
        for i in range(21):  # 21 hand keypoints
            keypoints.append({
                'id': i,
                'x': 100 + i * 10,
                'y': 200 + i * 5,
                'confidence': 0.9
            })
        return keypoints
    
    async def _send_to_android_xr(self, result: ROIAnalysisResult):
        """Send analysis result to Android XR system"""
        if self.bridge:
            a2a_message = result.to_a2a_format()
            self.bridge.send_to_android('roi_analysis', a2a_message)
            logger.info(f"Sent ROI analysis to Android XR: {result.labels[0] if result.labels else 'unknown'}")
    
    def _generate_cache_key(self, roi: ROICoordinates, mode: ROIAnalysisMode) -> str:
        """Generate cache key for ROI analysis"""
        return f"{roi.x}_{roi.y}_{roi.width}_{roi.height}_{mode.value}"
    
    def _get_cached_result(self, cache_key: str) -> Optional[ROIAnalysisResult]:
        """Get cached result if still valid"""
        if cache_key in self.analysis_cache:
            cached_time, result = self.analysis_cache[cache_key]
            if time.time() - cached_time < self.cache_ttl:
                return result
            else:
                del self.analysis_cache[cache_key]
        return None
    
    def _cache_result(self, cache_key: str, result: ROIAnalysisResult):
        """Cache analysis result"""
        self.analysis_cache[cache_key] = (time.time(), result)
        
        # Limit cache size
        if len(self.analysis_cache) > 100:
            # Remove oldest entries
            sorted_items = sorted(self.analysis_cache.items(), 
                                key=lambda x: x[1][0])
            for key, _ in sorted_items[:20]:
                del self.analysis_cache[key]
    
    def _update_metrics(self, processing_time: float):
        """Update performance metrics"""
        self.performance_metrics['total_processed'] += 1
        
        # Update average processing time
        n = self.performance_metrics['total_processed']
        avg = self.performance_metrics['average_time']
        self.performance_metrics['average_time'] = (avg * (n - 1) + processing_time) / n
        
        # Estimate FPS
        if processing_time > 0:
            self.performance_metrics['last_fps'] = 1.0 / processing_time
    
    def get_metrics(self) -> Dict:
        """Get performance metrics"""
        return self.performance_metrics.copy()
    
    async def optimize_roi_for_performance(self, 
                                          roi: ROICoordinates,
                                          target_size: Tuple[int, int] = (640, 480)) -> ROICoordinates:
        """Optimize ROI size for better performance"""
        
        # If ROI is too large, scale it down
        if roi.width > target_size[0] or roi.height > target_size[1]:
            scale_x = target_size[0] / roi.width
            scale_y = target_size[1] / roi.height
            scale = min(scale_x, scale_y)
            
            return ROICoordinates(
                x=roi.x,
                y=roi.y,
                width=int(roi.width * scale),
                height=int(roi.height * scale)
            )
        
        return roi
    
    def validate_roi(self, roi: ROICoordinates, 
                    image_size: Tuple[int, int]) -> bool:
        """Validate ROI coordinates against image size"""
        w, h = image_size
        
        if roi.x < 0 or roi.y < 0:
            return False
        if roi.x + roi.width > w or roi.y + roi.height > h:
            return False
        if roi.width <= 0 or roi.height <= 0:
            return False
        
        return True


class ROITracker:
    """Track ROIs across frames for temporal consistency"""
    
    def __init__(self, max_distance: float = 50.0):
        self.tracked_rois = {}
        self.next_id = 0
        self.max_distance = max_distance
        
    def update(self, rois: List[ROICoordinates]) -> List[Tuple[int, ROICoordinates]]:
        """Update tracked ROIs and return (track_id, roi) pairs"""
        tracked_results = []
        unmatched_rois = rois.copy()
        
        # Match with existing tracks
        for track_id, tracked_roi in list(self.tracked_rois.items()):
            best_match = None
            best_distance = self.max_distance
            
            for roi in unmatched_rois:
                distance = self._calculate_distance(tracked_roi, roi)
                if distance < best_distance:
                    best_distance = distance
                    best_match = roi
            
            if best_match:
                self.tracked_rois[track_id] = best_match
                tracked_results.append((track_id, best_match))
                unmatched_rois.remove(best_match)
            else:
                # Track lost
                del self.tracked_rois[track_id]
        
        # Create new tracks for unmatched ROIs
        for roi in unmatched_rois:
            track_id = self.next_id
            self.next_id += 1
            self.tracked_rois[track_id] = roi
            tracked_results.append((track_id, roi))
        
        return tracked_results
    
    def _calculate_distance(self, roi1: ROICoordinates, roi2: ROICoordinates) -> float:
        """Calculate distance between two ROIs"""
        c1 = roi1.get_center()
        c2 = roi2.get_center()
        return np.sqrt((c1[0] - c2[0])**2 + (c1[1] - c2[1])**2)
    
    def get_track_history(self, track_id: int) -> Optional[ROICoordinates]:
        """Get current ROI for track"""
        return self.tracked_rois.get(track_id)


async def test_roi_processing():
    """Test ROI processing functionality"""
    
    # Initialize processor
    processor = ROIProcessor()
    
    # Test ROI coordinates
    roi = ROICoordinates(x=100, y=200, width=300, height=400)
    
    print(f"Processing ROI: {roi.to_dict()}")
    print(f"ROI center: {roi.get_center()}")
    print(f"ROI area: {roi.get_area()}")
    
    # Create dummy image data (normally this would be real image data)
    dummy_image = np.zeros((800, 600, 3), dtype=np.uint8)
    
    # Process ROI with different modes
    modes = [
        ROIAnalysisMode.OBJECT_DETECTION,
        ROIAnalysisMode.TEXT_RECOGNITION,
        ROIAnalysisMode.SCENE_UNDERSTANDING
    ]
    
    for mode in modes:
        print(f"\nProcessing with mode: {mode.value}")
        result = await processor.process_roi(dummy_image, roi, mode)
        
        print(f"  Confidence: {result.confidence:.2f}")
        print(f"  Labels: {result.labels}")
        print(f"  Processing time: {result.processing_time:.3f}s")
        
        # Convert to A2A format
        a2a_data = result.to_a2a_format()
        print(f"  A2A format: {json.dumps(a2a_data, indent=2)[:200]}...")
    
    # Test multiple ROIs
    roi_list = [
        ROICoordinates(x=50, y=50, width=100, height=100),
        ROICoordinates(x=200, y=200, width=150, height=150),
        ROICoordinates(x=400, y=300, width=200, height=200)
    ]
    
    print("\n\nProcessing multiple ROIs in parallel...")
    results = await processor.process_multiple_rois(
        dummy_image, 
        roi_list, 
        ROIAnalysisMode.OBJECT_DETECTION,
        parallel=True
    )
    
    for i, result in enumerate(results):
        print(f"  ROI {i+1}: {result.labels[0] if result.labels else 'unknown'}")
    
    # Show performance metrics
    metrics = processor.get_metrics()
    print(f"\nPerformance Metrics:")
    print(f"  Total processed: {metrics['total_processed']}")
    print(f"  Average time: {metrics['average_time']:.3f}s")
    print(f"  Last FPS: {metrics['last_fps']:.1f}")
    
    # Test ROI tracker
    print("\n\nTesting ROI Tracker...")
    tracker = ROITracker()
    
    # Simulate tracking across frames
    for frame in range(3):
        # Simulate slight movement
        moving_rois = [
            ROICoordinates(x=100 + frame*5, y=200 + frame*3, width=300, height=400),
            ROICoordinates(x=50 + frame*2, y=50 + frame*2, width=100, height=100)
        ]
        
        tracked = tracker.update(moving_rois)
        print(f"  Frame {frame}: {len(tracked)} ROIs tracked")
        for track_id, roi in tracked:
            print(f"    Track {track_id}: x={roi.x}, y={roi.y}")


if __name__ == "__main__":
    # Run test
    asyncio.run(test_roi_processing())
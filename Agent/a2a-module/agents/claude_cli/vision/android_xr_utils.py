"""
Android XR Utilities for GPT-4V Integration
"""

import json
import struct
import logging
from typing import Dict, List, Optional, Tuple, Any
from dataclasses import dataclass, asdict
import numpy as np
from enum import Enum
import asyncio
import time
from collections import deque
from threading import Lock

logger = logging.getLogger(__name__)


class CoordinateSystem(Enum):
    """Android XR coordinate systems"""
    WORLD = "world"
    CAMERA = "camera"
    SCREEN = "screen"
    DEVICE = "device"


class InteractionType(Enum):
    """XR interaction types"""
    TAP = "tap"
    PINCH = "pinch"
    GRAB = "grab"
    SWIPE = "swipe"
    GAZE = "gaze"
    VOICE = "voice"
    CONTROLLER = "controller"


@dataclass
class Pose3D:
    """3D pose representation for XR objects"""
    position: Tuple[float, float, float]
    rotation: Tuple[float, float, float, float]
    scale: Tuple[float, float, float] = (1.0, 1.0, 1.0)
    
    def to_matrix(self) -> np.ndarray:
        """Convert pose to 4x4 transformation matrix"""
        matrix = np.eye(4)
        
        x, y, z = self.position
        matrix[0, 3] = x
        matrix[1, 3] = y
        matrix[2, 3] = z
        
        qx, qy, qz, qw = self.rotation
        matrix[:3, :3] = self._quaternion_to_rotation_matrix(qx, qy, qz, qw)
        
        sx, sy, sz = self.scale
        matrix[0, 0] *= sx
        matrix[1, 1] *= sy
        matrix[2, 2] *= sz
        
        return matrix
    
    @staticmethod
    def _quaternion_to_rotation_matrix(x, y, z, w):
        """Convert quaternion to rotation matrix"""
        return np.array([
            [1 - 2*(y*y + z*z), 2*(x*y - w*z), 2*(x*z + w*y)],
            [2*(x*y + w*z), 1 - 2*(x*x + z*z), 2*(y*z - w*x)],
            [2*(x*z - w*y), 2*(y*z + w*x), 1 - 2*(x*x + y*y)]
        ])


@dataclass
class BoundingBox3D:
    """3D bounding box for XR objects"""
    center: Tuple[float, float, float]
    size: Tuple[float, float, float]
    orientation: Optional[Tuple[float, float, float, float]] = None
    
    def get_corners(self) -> List[Tuple[float, float, float]]:
        """Get 8 corners of the bounding box"""
        cx, cy, cz = self.center
        sx, sy, sz = self.size
        
        half_size = (sx/2, sy/2, sz/2)
        
        corners = [
            (cx - half_size[0], cy - half_size[1], cz - half_size[2]),
            (cx + half_size[0], cy - half_size[1], cz - half_size[2]),
            (cx - half_size[0], cy + half_size[1], cz - half_size[2]),
            (cx + half_size[0], cy + half_size[1], cz - half_size[2]),
            (cx - half_size[0], cy - half_size[1], cz + half_size[2]),
            (cx + half_size[0], cy - half_size[1], cz + half_size[2]),
            (cx - half_size[0], cy + half_size[1], cz + half_size[2]),
            (cx + half_size[0], cy + half_size[1], cz + half_size[2])
        ]
        
        if self.orientation:
            pose = Pose3D(self.center, self.orientation)
            matrix = pose.to_matrix()
            rotated_corners = []
            for corner in corners:
                point = np.array([corner[0] - cx, corner[1] - cy, corner[2] - cz, 1])
                rotated = matrix @ point
                rotated_corners.append((rotated[0], rotated[1], rotated[2]))
            return rotated_corners
        
        return corners
    
    def contains_point(self, point: Tuple[float, float, float]) -> bool:
        """Check if point is inside bounding box"""
        px, py, pz = point
        cx, cy, cz = self.center
        sx, sy, sz = self.size
        
        return (abs(px - cx) <= sx/2 and 
                abs(py - cy) <= sy/2 and 
                abs(pz - cz) <= sz/2)


class AndroidXRBridge:
    """Bridge for Android XR system communication"""
    
    def __init__(self):
        self.message_queue = deque(maxlen=100)
        self.callbacks = {}
        self.lock = Lock()
        
    def send_to_android(self, message_type: str, data: Dict) -> None:
        """Send message to Android XR system"""
        message = {
            'type': message_type,
            'timestamp': time.time(),
            'data': data
        }
        
        message_json = json.dumps(message)
        logger.info(f"Sending to Android: {message_type}")
        
        with self.lock:
            self.message_queue.append(message)
    
    def register_callback(self, message_type: str, callback) -> None:
        """Register callback for specific message type"""
        self.callbacks[message_type] = callback
    
    def process_android_message(self, message: str) -> Optional[Dict]:
        """Process incoming message from Android"""
        try:
            data = json.loads(message)
            message_type = data.get('type')
            
            if message_type in self.callbacks:
                return self.callbacks[message_type](data.get('data'))
            
            return data
        except Exception as e:
            logger.error(f"Failed to process Android message: {e}")
            return None


class XRObjectTracker:
    """Tracks XR objects across frames"""
    
    def __init__(self, max_track_age: int = 30):
        self.tracks = {}
        self.next_id = 0
        self.max_track_age = max_track_age
        
    def update(self, detections: List[Dict]) -> List[Dict]:
        """Update tracks with new detections"""
        tracked_objects = []
        
        for detection in detections:
            track_id = self._find_matching_track(detection)
            
            if track_id is None:
                track_id = self._create_new_track(detection)
            else:
                self._update_track(track_id, detection)
            
            detection['track_id'] = track_id
            tracked_objects.append(detection)
        
        self._cleanup_old_tracks()
        
        return tracked_objects
    
    def _find_matching_track(self, detection: Dict) -> Optional[int]:
        """Find matching track for detection"""
        best_match = None
        best_distance = float('inf')
        
        detection_pos = detection.get('position', detection.get('center', [0, 0, 0]))
        
        for track_id, track in self.tracks.items():
            if track['age'] > self.max_track_age:
                continue
            
            track_pos = track['last_position']
            distance = np.linalg.norm(np.array(detection_pos) - np.array(track_pos))
            
            if distance < best_distance and distance < 0.5:
                best_distance = distance
                best_match = track_id
        
        return best_match
    
    def _create_new_track(self, detection: Dict) -> int:
        """Create new track for detection"""
        track_id = self.next_id
        self.next_id += 1
        
        self.tracks[track_id] = {
            'id': track_id,
            'first_detection': detection,
            'last_detection': detection,
            'last_position': detection.get('position', detection.get('center', [0, 0, 0])),
            'age': 0,
            'detection_count': 1
        }
        
        return track_id
    
    def _update_track(self, track_id: int, detection: Dict) -> None:
        """Update existing track"""
        track = self.tracks[track_id]
        track['last_detection'] = detection
        track['last_position'] = detection.get('position', detection.get('center', [0, 0, 0]))
        track['age'] = 0
        track['detection_count'] += 1
    
    def _cleanup_old_tracks(self) -> None:
        """Remove old tracks"""
        for track_id in list(self.tracks.keys()):
            self.tracks[track_id]['age'] += 1
            if self.tracks[track_id]['age'] > self.max_track_age:
                del self.tracks[track_id]
    
    def get_track_history(self, track_id: int) -> Optional[Dict]:
        """Get history of specific track"""
        return self.tracks.get(track_id)


class SpatialAnchorManager:
    """Manages spatial anchors for AR content"""
    
    def __init__(self):
        self.anchors = {}
        self.anchor_id_counter = 0
        
    def create_anchor(self, 
                     position: Tuple[float, float, float],
                     rotation: Optional[Tuple[float, float, float, float]] = None,
                     metadata: Optional[Dict] = None) -> str:
        """Create new spatial anchor"""
        anchor_id = f"anchor_{self.anchor_id_counter}"
        self.anchor_id_counter += 1
        
        self.anchors[anchor_id] = {
            'id': anchor_id,
            'pose': Pose3D(position, rotation or (0, 0, 0, 1)),
            'metadata': metadata or {},
            'created_at': time.time(),
            'last_updated': time.time()
        }
        
        logger.info(f"Created anchor {anchor_id} at {position}")
        return anchor_id
    
    def update_anchor(self, anchor_id: str, 
                     position: Optional[Tuple[float, float, float]] = None,
                     rotation: Optional[Tuple[float, float, float, float]] = None) -> bool:
        """Update existing anchor"""
        if anchor_id not in self.anchors:
            return False
        
        anchor = self.anchors[anchor_id]
        
        if position:
            anchor['pose'].position = position
        if rotation:
            anchor['pose'].rotation = rotation
        
        anchor['last_updated'] = time.time()
        return True
    
    def delete_anchor(self, anchor_id: str) -> bool:
        """Delete anchor"""
        if anchor_id in self.anchors:
            del self.anchors[anchor_id]
            return True
        return False
    
    def find_nearest_anchor(self, position: Tuple[float, float, float], 
                           max_distance: float = 5.0) -> Optional[str]:
        """Find nearest anchor to given position"""
        nearest_id = None
        nearest_distance = max_distance
        
        pos_array = np.array(position)
        
        for anchor_id, anchor in self.anchors.items():
            anchor_pos = np.array(anchor['pose'].position)
            distance = np.linalg.norm(pos_array - anchor_pos)
            
            if distance < nearest_distance:
                nearest_distance = distance
                nearest_id = anchor_id
        
        return nearest_id
    
    def get_anchors_in_radius(self, position: Tuple[float, float, float], 
                             radius: float) -> List[Dict]:
        """Get all anchors within radius of position"""
        anchors_in_radius = []
        pos_array = np.array(position)
        
        for anchor in self.anchors.values():
            anchor_pos = np.array(anchor['pose'].position)
            distance = np.linalg.norm(pos_array - anchor_pos)
            
            if distance <= radius:
                anchors_in_radius.append({
                    'anchor': anchor,
                    'distance': distance
                })
        
        return sorted(anchors_in_radius, key=lambda x: x['distance'])


class PerformanceMonitor:
    """Monitor performance metrics for XR processing"""
    
    def __init__(self, window_size: int = 100):
        self.window_size = window_size
        self.frame_times = deque(maxlen=window_size)
        self.processing_times = deque(maxlen=window_size)
        self.confidence_scores = deque(maxlen=window_size)
        self.start_time = time.time()
        
    def record_frame(self, processing_time: float, confidence: float = 1.0):
        """Record frame processing metrics"""
        current_time = time.time()
        self.frame_times.append(current_time)
        self.processing_times.append(processing_time)
        self.confidence_scores.append(confidence)
    
    def get_fps(self) -> float:
        """Calculate current FPS"""
        if len(self.frame_times) < 2:
            return 0.0
        
        time_span = self.frame_times[-1] - self.frame_times[0]
        if time_span > 0:
            return len(self.frame_times) / time_span
        return 0.0
    
    def get_average_processing_time(self) -> float:
        """Get average processing time"""
        if not self.processing_times:
            return 0.0
        return np.mean(self.processing_times)
    
    def get_average_confidence(self) -> float:
        """Get average confidence score"""
        if not self.confidence_scores:
            return 0.0
        return np.mean(self.confidence_scores)
    
    def get_metrics(self) -> Dict:
        """Get all performance metrics"""
        return {
            'fps': self.get_fps(),
            'avg_processing_time': self.get_average_processing_time(),
            'avg_confidence': self.get_average_confidence(),
            'total_frames': len(self.frame_times),
            'uptime': time.time() - self.start_time
        }


class XRSceneOptimizer:
    """Optimizes scene analysis for performance"""
    
    def __init__(self):
        self.roi_cache = {}
        self.analysis_cache = {}
        self.cache_ttl = 1.0
        
    def should_analyze_region(self, region_id: str, 
                             importance: float = 0.5) -> bool:
        """Determine if region should be analyzed"""
        if region_id in self.analysis_cache:
            cached_time, cached_importance = self.analysis_cache[region_id]
            
            if time.time() - cached_time < self.cache_ttl:
                return importance > cached_importance * 1.2
        
        return True
    
    def cache_analysis(self, region_id: str, result: Any, importance: float):
        """Cache analysis result"""
        self.analysis_cache[region_id] = (time.time(), importance)
        self.roi_cache[region_id] = result
    
    def get_cached_result(self, region_id: str) -> Optional[Any]:
        """Get cached result if valid"""
        if region_id in self.analysis_cache:
            cached_time, _ = self.analysis_cache[region_id]
            
            if time.time() - cached_time < self.cache_ttl:
                return self.roi_cache.get(region_id)
        
        return None
    
    def calculate_roi_importance(self, roi: Dict) -> float:
        """Calculate importance score for ROI"""
        importance = 0.5
        
        if roi.get('contains_face'):
            importance += 0.3
        if roi.get('contains_text'):
            importance += 0.2
        if roi.get('motion_detected'):
            importance += 0.2
        if roi.get('user_gaze'):
            importance += 0.4
        
        size_factor = roi.get('size_ratio', 0.1)
        importance *= (1 + size_factor)
        
        return min(importance, 1.0)


class AndroidXRDataFormatter:
    """Formats data for Android XR consumption"""
    
    @staticmethod
    def format_detection(detection: Dict) -> bytes:
        """Format detection for Android binary protocol"""
        data = {
            'id': detection.get('track_id', -1),
            'type': detection.get('type', 'unknown'),
            'confidence': detection.get('confidence', 0.0),
            'position': detection.get('position', [0, 0, 0]),
            'rotation': detection.get('rotation', [0, 0, 0, 1]),
            'size': detection.get('size', [1, 1, 1])
        }
        
        binary_data = struct.pack(
            'if10s3f4f3f',
            data['id'],
            data['confidence'],
            data['type'].encode('utf-8')[:10].ljust(10, b'\x00'),
            *data['position'],
            *data['rotation'],
            *data['size']
        )
        
        return binary_data
    
    @staticmethod
    def format_for_arcore(analysis_results: List[Dict]) -> Dict:
        """Format results for ARCore integration"""
        arcore_data = {
            'timestamp': time.time() * 1000,
            'trackables': [],
            'planes': [],
            'anchors': []
        }
        
        for result in analysis_results:
            if result.get('type') == 'plane':
                arcore_data['planes'].append({
                    'center': result.get('center', [0, 0, 0]),
                    'extent': result.get('extent', [1, 1]),
                    'normal': result.get('normal', [0, 1, 0]),
                    'polygon': result.get('polygon', [])
                })
            elif result.get('type') == 'anchor':
                arcore_data['anchors'].append({
                    'id': result.get('id'),
                    'pose': result.get('pose', {}),
                    'cloudId': result.get('cloud_id')
                })
            else:
                arcore_data['trackables'].append({
                    'id': result.get('track_id'),
                    'state': 'TRACKING',
                    'pose': {
                        'translation': result.get('position', [0, 0, 0]),
                        'rotation': result.get('rotation', [0, 0, 0, 1])
                    }
                })
        
        return arcore_data
    
    @staticmethod
    def format_for_unity_xr(analysis_results: List[Dict]) -> str:
        """Format results for Unity XR Toolkit"""
        unity_data = {
            'version': '1.0',
            'frameId': int(time.time() * 1000),
            'objects': []
        }
        
        for result in analysis_results:
            unity_object = {
                'name': result.get('label', 'Unknown'),
                'transform': {
                    'position': {
                        'x': result.get('position', [0, 0, 0])[0],
                        'y': result.get('position', [0, 0, 0])[1],
                        'z': result.get('position', [0, 0, 0])[2]
                    },
                    'rotation': {
                        'x': result.get('rotation', [0, 0, 0, 1])[0],
                        'y': result.get('rotation', [0, 0, 0, 1])[1],
                        'z': result.get('rotation', [0, 0, 0, 1])[2],
                        'w': result.get('rotation', [0, 0, 0, 1])[3]
                    },
                    'scale': {
                        'x': result.get('scale', [1, 1, 1])[0],
                        'y': result.get('scale', [1, 1, 1])[1],
                        'z': result.get('scale', [1, 1, 1])[2]
                    }
                },
                'metadata': result.get('metadata', {})
            }
            unity_data['objects'].append(unity_object)
        
        return json.dumps(unity_data)


async def coordinate_transform_example():
    """Example of coordinate transformations"""
    
    world_pose = Pose3D(
        position=(1.0, 2.0, 3.0),
        rotation=(0, 0, 0, 1),
        scale=(1, 1, 1)
    )
    
    matrix = world_pose.to_matrix()
    print(f"Transformation matrix:\n{matrix}")
    
    bbox = BoundingBox3D(
        center=(0, 0, 0),
        size=(2, 2, 2)
    )
    
    corners = bbox.get_corners()
    print(f"Bounding box corners: {corners}")
    
    point = (0.5, 0.5, 0.5)
    contains = bbox.contains_point(point)
    print(f"Point {point} inside box: {contains}")


if __name__ == "__main__":
    asyncio.run(coordinate_transform_example())
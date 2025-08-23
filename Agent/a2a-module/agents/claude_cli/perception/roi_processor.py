import numpy as np
import cv2
from typing import List, Tuple, Dict, Optional, Callable
from dataclasses import dataclass
from enum import Enum
import threading
from concurrent.futures import ThreadPoolExecutor
import time


class ROIType(Enum):
    FIXED = "fixed"
    DYNAMIC = "dynamic"
    ATTENTION = "attention"
    OBJECT_TRACKING = "object_tracking"
    GAZE_BASED = "gaze_based"


@dataclass
class ROIConfig:
    x: int
    y: int
    width: int
    height: int
    roi_type: ROIType
    priority: int = 1
    scale_factor: float = 1.0
    tracking_id: Optional[str] = None
    confidence_threshold: float = 0.5


class ROIProcessor:
    def __init__(self, max_workers: int = 4):
        self.roi_configs: Dict[str, ROIConfig] = {}
        self.processing_executor = ThreadPoolExecutor(max_workers=max_workers)
        self.roi_cache = {}
        self.cache_lock = threading.Lock()
        self.frame_counter = 0
        self.processing_times = []
        self.adaptive_threshold = 0.016
        
    def add_roi(self, roi_id: str, config: ROIConfig):
        self.roi_configs[roi_id] = config
        
    def remove_roi(self, roi_id: str):
        if roi_id in self.roi_configs:
            del self.roi_configs[roi_id]
            with self.cache_lock:
                if roi_id in self.roi_cache:
                    del self.roi_cache[roi_id]
    
    def update_roi(self, roi_id: str, x: int = None, y: int = None, 
                   width: int = None, height: int = None):
        if roi_id in self.roi_configs:
            config = self.roi_configs[roi_id]
            if x is not None:
                config.x = x
            if y is not None:
                config.y = y
            if width is not None:
                config.width = width
            if height is not None:
                config.height = height
    
    def process_frame_with_rois(self, frame: np.ndarray, 
                                process_func: Optional[Callable] = None) -> Dict[str, np.ndarray]:
        start_time = time.time()
        self.frame_counter += 1
        
        sorted_rois = sorted(self.roi_configs.items(), 
                           key=lambda x: x[1].priority, reverse=True)
        
        results = {}
        futures = []
        
        for roi_id, config in sorted_rois:
            if self._should_process_roi(roi_id, config):
                future = self.processing_executor.submit(
                    self._process_single_roi, frame, roi_id, config, process_func
                )
                futures.append((roi_id, future))
        
        for roi_id, future in futures:
            try:
                result = future.result(timeout=self.adaptive_threshold)
                results[roi_id] = result
            except Exception as e:
                print(f"ROI {roi_id} processing failed: {e}")
                results[roi_id] = self._get_cached_roi(roi_id)
        
        processing_time = time.time() - start_time
        self._update_adaptive_threshold(processing_time)
        
        return results
    
    def _should_process_roi(self, roi_id: str, config: ROIConfig) -> bool:
        if config.roi_type == ROIType.FIXED:
            return self.frame_counter % max(1, 4 - config.priority) == 0
        elif config.roi_type == ROIType.DYNAMIC:
            return True
        elif config.roi_type == ROIType.ATTENTION:
            return config.priority >= 2
        elif config.roi_type == ROIType.OBJECT_TRACKING:
            return True
        elif config.roi_type == ROIType.GAZE_BASED:
            return True
        return True
    
    def _process_single_roi(self, frame: np.ndarray, roi_id: str, 
                           config: ROIConfig, process_func: Optional[Callable]) -> np.ndarray:
        x, y, w, h = config.x, config.y, config.width, config.height
        
        h_frame, w_frame = frame.shape[:2]
        x = max(0, min(x, w_frame - 1))
        y = max(0, min(y, h_frame - 1))
        w = min(w, w_frame - x)
        h = min(h, h_frame - y)
        
        roi = frame[y:y+h, x:x+w]
        
        if config.scale_factor != 1.0:
            new_width = int(w * config.scale_factor)
            new_height = int(h * config.scale_factor)
            roi = cv2.resize(roi, (new_width, new_height), interpolation=cv2.INTER_LINEAR)
        
        if process_func:
            roi = process_func(roi, config)
        
        with self.cache_lock:
            self.roi_cache[roi_id] = roi.copy()
        
        return roi
    
    def _get_cached_roi(self, roi_id: str) -> Optional[np.ndarray]:
        with self.cache_lock:
            return self.roi_cache.get(roi_id, None)
    
    def _update_adaptive_threshold(self, processing_time: float):
        self.processing_times.append(processing_time)
        if len(self.processing_times) > 30:
            self.processing_times.pop(0)
        
        if len(self.processing_times) >= 10:
            avg_time = np.mean(self.processing_times)
            if avg_time > 0.020:
                self.adaptive_threshold = min(0.033, self.adaptive_threshold * 1.1)
            elif avg_time < 0.012:
                self.adaptive_threshold = max(0.008, self.adaptive_threshold * 0.9)
    
    def extract_yuv_roi(self, y_data: np.ndarray, u_data: np.ndarray, 
                       v_data: np.ndarray, roi_config: ROIConfig) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
        x, y, w, h = roi_config.x, roi_config.y, roi_config.width, roi_config.height
        
        roi_y = y_data[y:y+h, x:x+w]
        
        u_x, u_y = x // 2, y // 2
        u_w, u_h = w // 2, h // 2
        roi_u = u_data[u_y:u_y+u_h, u_x:u_x+u_w]
        roi_v = v_data[u_y:u_y+u_h, u_x:u_x+u_w]
        
        return roi_y, roi_u, roi_v
    
    def yuv_to_rgb_optimized(self, y: np.ndarray, u: np.ndarray, v: np.ndarray) -> np.ndarray:
        h, w = y.shape
        
        u_upsampled = cv2.resize(u, (w, h), interpolation=cv2.INTER_LINEAR)
        v_upsampled = cv2.resize(v, (w, h), interpolation=cv2.INTER_LINEAR)
        
        yuv = np.dstack((y, u_upsampled, v_upsampled))
        
        rgb = cv2.cvtColor(yuv, cv2.COLOR_YUV2RGB_I420)
        
        return rgb
    
    def apply_edge_detection(self, roi: np.ndarray, threshold1: int = 50, 
                           threshold2: int = 150) -> np.ndarray:
        if len(roi.shape) == 3:
            gray = cv2.cvtColor(roi, cv2.COLOR_RGB2GRAY)
        else:
            gray = roi
        
        edges = cv2.Canny(gray, threshold1, threshold2)
        return edges
    
    def apply_blur(self, roi: np.ndarray, kernel_size: int = 5) -> np.ndarray:
        return cv2.GaussianBlur(roi, (kernel_size, kernel_size), 0)
    
    def apply_sharpening(self, roi: np.ndarray) -> np.ndarray:
        kernel = np.array([[-1,-1,-1],
                          [-1, 9,-1],
                          [-1,-1,-1]], dtype=np.float32)
        return cv2.filter2D(roi, -1, kernel)
    
    def detect_motion(self, roi_current: np.ndarray, roi_previous: np.ndarray, 
                     threshold: int = 25) -> np.ndarray:
        if roi_previous is None:
            return np.zeros_like(roi_current)
        
        if len(roi_current.shape) == 3:
            current_gray = cv2.cvtColor(roi_current, cv2.COLOR_RGB2GRAY)
        else:
            current_gray = roi_current
            
        if len(roi_previous.shape) == 3:
            previous_gray = cv2.cvtColor(roi_previous, cv2.COLOR_RGB2GRAY)
        else:
            previous_gray = roi_previous
        
        diff = cv2.absdiff(current_gray, previous_gray)
        _, motion_mask = cv2.threshold(diff, threshold, 255, cv2.THRESH_BINARY)
        
        return motion_mask
    
    def cleanup(self):
        self.processing_executor.shutdown(wait=True)
        self.roi_cache.clear()


class AdaptiveROITracker:
    def __init__(self):
        self.trackers = {}
        self.tracker_types = {
            'csrt': cv2.TrackerCSRT_create,
            'kcf': cv2.TrackerKCF_create,
            'mosse': cv2.TrackerMOSSE_create
        }
        self.active_tracker_type = 'csrt'
        
    def initialize_tracker(self, frame: np.ndarray, roi_id: str, 
                          bbox: Tuple[int, int, int, int]):
        tracker = self.tracker_types[self.active_tracker_type]()
        tracker.init(frame, bbox)
        self.trackers[roi_id] = {
            'tracker': tracker,
            'bbox': bbox,
            'confidence': 1.0
        }
    
    def update_tracker(self, frame: np.ndarray, roi_id: str) -> Optional[Tuple[int, int, int, int]]:
        if roi_id not in self.trackers:
            return None
        
        tracker_info = self.trackers[roi_id]
        success, bbox = tracker_info['tracker'].update(frame)
        
        if success:
            tracker_info['bbox'] = tuple(map(int, bbox))
            tracker_info['confidence'] = min(1.0, tracker_info['confidence'] * 1.02)
            return tracker_info['bbox']
        else:
            tracker_info['confidence'] *= 0.9
            if tracker_info['confidence'] < 0.3:
                del self.trackers[roi_id]
            return None
    
    def remove_tracker(self, roi_id: str):
        if roi_id in self.trackers:
            del self.trackers[roi_id]
    
    def get_all_tracked_rois(self, frame: np.ndarray) -> Dict[str, Tuple[int, int, int, int]]:
        tracked_rois = {}
        for roi_id in list(self.trackers.keys()):
            bbox = self.update_tracker(frame, roi_id)
            if bbox:
                tracked_rois[roi_id] = bbox
        return tracked_rois
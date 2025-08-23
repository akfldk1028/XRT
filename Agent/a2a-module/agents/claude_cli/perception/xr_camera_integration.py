import numpy as np
from typing import Dict, List, Tuple, Optional, Callable
from camera2_manager import Camera2XRManager
from roi_processor import ROIProcessor, ROIConfig, ROIType, AdaptiveROITracker
from xr_frame_processor import RealTimeFrameProcessor, XRFrameMetadata, XRCameraOptimizer
import threading
import time
from dataclasses import dataclass
from enum import Enum


class XRCameraMode(Enum):
    PASSTHROUGH = "passthrough"
    OBJECT_DETECTION = "object_detection"
    HAND_TRACKING = "hand_tracking"
    SCENE_UNDERSTANDING = "scene_understanding"
    SLAM = "slam"
    MIXED_REALITY = "mixed_reality"


@dataclass
class XRCameraConfig:
    mode: XRCameraMode
    target_fps: int = 30
    resolution: Tuple[int, int] = (1920, 1080)
    enable_auto_exposure: bool = True
    enable_auto_focus: bool = True
    enable_hdr: bool = False
    enable_stabilization: bool = True
    roi_tracking_enabled: bool = True
    max_roi_count: int = 5
    processing_threads: int = 4


class XRCameraSystem:
    def __init__(self, context, config: XRCameraConfig):
        self.context = context
        self.config = config
        
        self.camera_manager = Camera2XRManager(context, config.target_fps)
        self.roi_processor = ROIProcessor(max_workers=config.processing_threads)
        self.frame_processor = RealTimeFrameProcessor(
            buffer_size=5, 
            num_workers=config.processing_threads
        )
        self.roi_tracker = AdaptiveROITracker()
        self.optimizer = XRCameraOptimizer()
        
        self.callbacks: Dict[str, List[Callable]] = {
            'frame': [],
            'roi': [],
            'tracking': [],
            'performance': []
        }
        
        self.is_running = False
        self.stats_thread = None
        self.frame_count = 0
        self.last_fps_time = time.time()
        self.current_fps = 0
        
        self._configure_for_mode()
        
    def _configure_for_mode(self):
        if self.config.mode == XRCameraMode.PASSTHROUGH:
            self._setup_passthrough_mode()
        elif self.config.mode == XRCameraMode.OBJECT_DETECTION:
            self._setup_object_detection_mode()
        elif self.config.mode == XRCameraMode.HAND_TRACKING:
            self._setup_hand_tracking_mode()
        elif self.config.mode == XRCameraMode.SCENE_UNDERSTANDING:
            self._setup_scene_understanding_mode()
        elif self.config.mode == XRCameraMode.SLAM:
            self._setup_slam_mode()
        elif self.config.mode == XRCameraMode.MIXED_REALITY:
            self._setup_mixed_reality_mode()
    
    def _setup_passthrough_mode(self):
        self.frame_processor.add_preprocessing_step(self._passthrough_preprocessing)
        self.frame_processor.add_postprocessing_step(self._passthrough_postprocessing)
        
        self.roi_processor.add_roi("center", ROIConfig(
            x=self.config.resolution[0]//4,
            y=self.config.resolution[1]//4,
            width=self.config.resolution[0]//2,
            height=self.config.resolution[1]//2,
            roi_type=ROIType.FIXED,
            priority=3
        ))
    
    def _setup_object_detection_mode(self):
        self.frame_processor.add_preprocessing_step(self._object_detection_preprocessing)
        
        for i in range(3):
            self.roi_processor.add_roi(f"detection_{i}", ROIConfig(
                x=0, y=0, width=320, height=320,
                roi_type=ROIType.DYNAMIC,
                priority=2,
                scale_factor=0.5
            ))
    
    def _setup_hand_tracking_mode(self):
        self.frame_processor.add_preprocessing_step(self._hand_tracking_preprocessing)
        
        self.roi_processor.add_roi("left_hand", ROIConfig(
            x=0, y=self.config.resolution[1]//2,
            width=self.config.resolution[0]//2,
            height=self.config.resolution[1]//2,
            roi_type=ROIType.OBJECT_TRACKING,
            priority=3,
            tracking_id="left_hand"
        ))
        
        self.roi_processor.add_roi("right_hand", ROIConfig(
            x=self.config.resolution[0]//2, y=self.config.resolution[1]//2,
            width=self.config.resolution[0]//2,
            height=self.config.resolution[1]//2,
            roi_type=ROIType.OBJECT_TRACKING,
            priority=3,
            tracking_id="right_hand"
        ))
    
    def _setup_scene_understanding_mode(self):
        self.frame_processor.add_preprocessing_step(self._scene_understanding_preprocessing)
        
        grid_size = 3
        cell_w = self.config.resolution[0] // grid_size
        cell_h = self.config.resolution[1] // grid_size
        
        for i in range(grid_size):
            for j in range(grid_size):
                self.roi_processor.add_roi(f"grid_{i}_{j}", ROIConfig(
                    x=j * cell_w,
                    y=i * cell_h,
                    width=cell_w,
                    height=cell_h,
                    roi_type=ROIType.FIXED,
                    priority=1
                ))
    
    def _setup_slam_mode(self):
        self.frame_processor.add_preprocessing_step(self._slam_preprocessing)
        self.frame_processor.add_postprocessing_step(self._slam_feature_extraction)
        
        self.roi_processor.add_roi("slam_center", ROIConfig(
            x=self.config.resolution[0]//4,
            y=self.config.resolution[1]//4,
            width=self.config.resolution[0]//2,
            height=self.config.resolution[1]//2,
            roi_type=ROIType.FIXED,
            priority=3
        ))
    
    def _setup_mixed_reality_mode(self):
        self.frame_processor.add_preprocessing_step(self._mixed_reality_preprocessing)
        self.frame_processor.add_postprocessing_step(self._mixed_reality_compositing)
        
        self.roi_processor.add_roi("ar_overlay", ROIConfig(
            x=0, y=0,
            width=self.config.resolution[0],
            height=self.config.resolution[1],
            roi_type=ROIType.ATTENTION,
            priority=2
        ))
    
    def start(self) -> bool:
        if not self.camera_manager.initialize_for_xr(self.config.resolution):
            return False
        
        self.camera_manager.set_frame_callback(self._on_camera_frame)
        self.frame_processor.start_processing()
        
        self.camera_manager.open_camera()
        
        self.is_running = True
        self.stats_thread = threading.Thread(target=self._stats_monitor)
        self.stats_thread.daemon = True
        self.stats_thread.start()
        
        return True
    
    def stop(self):
        self.is_running = False
        
        if self.stats_thread:
            self.stats_thread.join(timeout=1.0)
        
        self.frame_processor.stop_processing()
        self.camera_manager.release()
        self.roi_processor.cleanup()
    
    def _on_camera_frame(self, frame_data: Dict, regions: List[Tuple]):
        self.frame_count += 1
        
        current_quality = self.optimizer.current_quality
        quality_settings = self.optimizer.get_quality_settings()
        
        if self.optimizer.should_skip_frame(self.frame_count):
            return
        
        for roi_name, (y, u, v) in frame_data.items():
            metadata = XRFrameMetadata(
                timestamp=time.time(),
                frame_id=self.frame_count,
                sensor_timestamp=int(time.time() * 1000000),
                exposure_time=0.016,
                iso=100,
                focal_length=4.0
            )
            
            if quality_settings['scale'] < 1.0:
                y = self.optimizer.apply_frame_scaling(y, current_quality)
                u = self.optimizer.apply_frame_scaling(u, current_quality)
                v = self.optimizer.apply_frame_scaling(v, current_quality)
            
            self.frame_processor.add_frame(y, u, v, metadata)
        
        processed = self.frame_processor.get_processed_frame(timeout=0.01)
        if processed:
            self._handle_processed_frame(processed)
    
    def _handle_processed_frame(self, processed: Dict):
        rgb_frame = processed['data']['rgb']
        metadata = processed['metadata']
        
        if self.config.roi_tracking_enabled:
            roi_results = self.roi_processor.process_frame_with_rois(
                rgb_frame, self._roi_process_function
            )
            
            for roi_id, roi_frame in roi_results.items():
                self._trigger_callbacks('roi', {
                    'roi_id': roi_id,
                    'frame': roi_frame,
                    'metadata': metadata
                })
        
        self._trigger_callbacks('frame', {
            'frame': rgb_frame,
            'metadata': metadata,
            'processing_time': processed['processing_time']
        })
        
        self._update_fps()
    
    def _roi_process_function(self, roi: np.ndarray, config: ROIConfig) -> np.ndarray:
        if config.roi_type == ROIType.OBJECT_TRACKING:
            roi = self.roi_processor.apply_edge_detection(roi)
        elif config.roi_type == ROIType.ATTENTION:
            roi = self.roi_processor.apply_sharpening(roi)
        return roi
    
    def _update_fps(self):
        current_time = time.time()
        if current_time - self.last_fps_time >= 1.0:
            self.current_fps = self.frame_count / (current_time - self.last_fps_time)
            self.frame_count = 0
            self.last_fps_time = current_time
    
    def _stats_monitor(self):
        while self.is_running:
            stats = self.frame_processor.get_processing_stats()
            
            current_latency = stats['avg']
            self.optimizer.auto_adjust_quality(self.current_fps, current_latency)
            
            self._trigger_callbacks('performance', {
                'fps': self.current_fps,
                'latency': current_latency,
                'quality': self.optimizer.current_quality,
                'stats': stats
            })
            
            time.sleep(1.0)
    
    def add_callback(self, event_type: str, callback: Callable):
        if event_type in self.callbacks:
            self.callbacks[event_type].append(callback)
    
    def remove_callback(self, event_type: str, callback: Callable):
        if event_type in self.callbacks and callback in self.callbacks[event_type]:
            self.callbacks[event_type].remove(callback)
    
    def _trigger_callbacks(self, event_type: str, data: Dict):
        for callback in self.callbacks.get(event_type, []):
            try:
                callback(data)
            except Exception as e:
                print(f"Callback error for {event_type}: {e}")
    
    def _passthrough_preprocessing(self, y, u, v, metadata):
        return y, u, v
    
    def _passthrough_postprocessing(self, frame, metadata):
        return frame
    
    def _object_detection_preprocessing(self, y, u, v, metadata):
        return y, u, v
    
    def _hand_tracking_preprocessing(self, y, u, v, metadata):
        return y, u, v
    
    def _scene_understanding_preprocessing(self, y, u, v, metadata):
        return y, u, v
    
    def _slam_preprocessing(self, y, u, v, metadata):
        return y, u, v
    
    def _slam_feature_extraction(self, frame, metadata):
        return frame
    
    def _mixed_reality_preprocessing(self, y, u, v, metadata):
        return y, u, v
    
    def _mixed_reality_compositing(self, frame, metadata):
        return frame
    
    def update_roi(self, roi_id: str, x: int, y: int, width: int, height: int):
        self.roi_processor.update_roi(roi_id, x, y, width, height)
    
    def add_roi(self, roi_id: str, config: ROIConfig):
        if len(self.roi_processor.roi_configs) >= self.config.max_roi_count:
            return False
        self.roi_processor.add_roi(roi_id, config)
        return True
    
    def remove_roi(self, roi_id: str):
        self.roi_processor.remove_roi(roi_id)
    
    def get_current_performance(self) -> Dict:
        return {
            'fps': self.current_fps,
            'quality': self.optimizer.current_quality,
            'processing_stats': self.frame_processor.get_processing_stats(),
            'roi_count': len(self.roi_processor.roi_configs)
        }
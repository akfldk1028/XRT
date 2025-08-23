import numpy as np
import cv2
from typing import Dict, List, Tuple, Optional, Callable, Any
from dataclasses import dataclass
import threading
import queue
import time
from collections import deque
import struct


@dataclass
class XRFrameMetadata:
    timestamp: float
    frame_id: int
    sensor_timestamp: int
    exposure_time: float
    iso: int
    focal_length: float
    lens_distortion: Optional[np.ndarray] = None
    pose_matrix: Optional[np.ndarray] = None
    confidence: float = 1.0


class RealTimeFrameProcessor:
    def __init__(self, buffer_size: int = 5, num_workers: int = 2):
        self.frame_queue = queue.Queue(maxsize=buffer_size)
        self.result_queue = queue.Queue(maxsize=buffer_size * 2)
        self.workers = []
        self.num_workers = num_workers
        self.running = False
        self.frame_id_counter = 0
        self.processing_stats = deque(maxlen=100)
        self.skip_frame_threshold = 2
        self.consecutive_skips = 0
        self.target_latency = 0.033
        
        self.preprocessing_pipeline = []
        self.postprocessing_pipeline = []
        
        self.frame_buffer_pool = []
        self.buffer_lock = threading.Lock()
        self._initialize_buffer_pool(buffer_size * 2)
        
    def _initialize_buffer_pool(self, pool_size: int):
        for _ in range(pool_size):
            self.frame_buffer_pool.append({
                'y': None,
                'u': None,
                'v': None,
                'metadata': None,
                'in_use': False
            })
    
    def _get_buffer_from_pool(self) -> Optional[Dict]:
        with self.buffer_lock:
            for buffer in self.frame_buffer_pool:
                if not buffer['in_use']:
                    buffer['in_use'] = True
                    return buffer
        return None
    
    def _return_buffer_to_pool(self, buffer: Dict):
        with self.buffer_lock:
            buffer['in_use'] = False
    
    def start_processing(self):
        self.running = True
        for i in range(self.num_workers):
            worker = threading.Thread(target=self._worker_thread, args=(i,))
            worker.daemon = True
            worker.start()
            self.workers.append(worker)
    
    def stop_processing(self):
        self.running = False
        for worker in self.workers:
            worker.join(timeout=1.0)
        self.workers.clear()
    
    def _worker_thread(self, worker_id: int):
        while self.running:
            try:
                frame_data = self.frame_queue.get(timeout=0.1)
                if frame_data is None:
                    continue
                
                start_time = time.time()
                processed = self._process_frame_internal(frame_data)
                processing_time = time.time() - start_time
                
                self.result_queue.put({
                    'frame_id': frame_data['metadata'].frame_id,
                    'data': processed,
                    'metadata': frame_data['metadata'],
                    'processing_time': processing_time
                })
                
                self._return_buffer_to_pool(frame_data['buffer'])
                
                self.processing_stats.append(processing_time)
                
            except queue.Empty:
                continue
            except Exception as e:
                print(f"Worker {worker_id} error: {e}")
    
    def _process_frame_internal(self, frame_data: Dict) -> Dict:
        y_data = frame_data['y']
        u_data = frame_data['u']
        v_data = frame_data['v']
        metadata = frame_data['metadata']
        
        for preprocess_func in self.preprocessing_pipeline:
            y_data, u_data, v_data = preprocess_func(y_data, u_data, v_data, metadata)
        
        rgb_frame = self._yuv_to_rgb_simd_optimized(y_data, u_data, v_data)
        
        if metadata.lens_distortion is not None:
            rgb_frame = self._correct_lens_distortion(rgb_frame, metadata.lens_distortion)
        
        for postprocess_func in self.postprocessing_pipeline:
            rgb_frame = postprocess_func(rgb_frame, metadata)
        
        return {
            'rgb': rgb_frame,
            'yuv': (y_data, u_data, v_data),
            'metadata': metadata
        }
    
    def _yuv_to_rgb_simd_optimized(self, y: np.ndarray, u: np.ndarray, v: np.ndarray) -> np.ndarray:
        h, w = y.shape
        
        u_upsampled = np.repeat(np.repeat(u, 2, axis=0), 2, axis=1)[:h, :w]
        v_upsampled = np.repeat(np.repeat(v, 2, axis=0), 2, axis=1)[:h, :w]
        
        y = y.astype(np.float32)
        u_upsampled = u_upsampled.astype(np.float32) - 128
        v_upsampled = v_upsampled.astype(np.float32) - 128
        
        r = y + 1.402 * v_upsampled
        g = y - 0.344 * u_upsampled - 0.714 * v_upsampled
        b = y + 1.772 * u_upsampled
        
        r = np.clip(r, 0, 255).astype(np.uint8)
        g = np.clip(g, 0, 255).astype(np.uint8)
        b = np.clip(b, 0, 255).astype(np.uint8)
        
        return np.dstack((r, g, b))
    
    def _correct_lens_distortion(self, frame: np.ndarray, distortion_coeffs: np.ndarray) -> np.ndarray:
        h, w = frame.shape[:2]
        
        camera_matrix = np.array([[w, 0, w/2],
                                 [0, w, h/2],
                                 [0, 0, 1]], dtype=np.float32)
        
        new_camera_matrix, roi = cv2.getOptimalNewCameraMatrix(
            camera_matrix, distortion_coeffs, (w, h), 1, (w, h)
        )
        
        map1, map2 = cv2.initUndistortRectifyMap(
            camera_matrix, distortion_coeffs, None, new_camera_matrix, (w, h), cv2.CV_16SC2
        )
        
        undistorted = cv2.remap(frame, map1, map2, cv2.INTER_LINEAR)
        
        return undistorted
    
    def add_frame(self, y_data: np.ndarray, u_data: np.ndarray, v_data: np.ndarray,
                  metadata: Optional[XRFrameMetadata] = None) -> bool:
        if self.frame_queue.full():
            self.consecutive_skips += 1
            if self.consecutive_skips > self.skip_frame_threshold:
                try:
                    self.frame_queue.get_nowait()
                except:
                    pass
            return False
        
        self.consecutive_skips = 0
        
        if metadata is None:
            metadata = XRFrameMetadata(
                timestamp=time.time(),
                frame_id=self.frame_id_counter,
                sensor_timestamp=int(time.time() * 1000000),
                exposure_time=0.016,
                iso=100,
                focal_length=4.0
            )
        
        buffer = self._get_buffer_from_pool()
        if buffer is None:
            return False
        
        buffer['y'] = y_data
        buffer['u'] = u_data
        buffer['v'] = v_data
        
        self.frame_queue.put({
            'y': y_data,
            'u': u_data,
            'v': v_data,
            'metadata': metadata,
            'buffer': buffer
        })
        
        self.frame_id_counter += 1
        return True
    
    def get_processed_frame(self, timeout: float = 0.05) -> Optional[Dict]:
        try:
            return self.result_queue.get(timeout=timeout)
        except queue.Empty:
            return None
    
    def add_preprocessing_step(self, func: Callable):
        self.preprocessing_pipeline.append(func)
    
    def add_postprocessing_step(self, func: Callable):
        self.postprocessing_pipeline.append(func)
    
    def get_processing_stats(self) -> Dict[str, float]:
        if not self.processing_stats:
            return {'avg': 0, 'min': 0, 'max': 0, 'fps': 0}
        
        stats = list(self.processing_stats)
        return {
            'avg': np.mean(stats),
            'min': np.min(stats),
            'max': np.max(stats),
            'fps': 1.0 / np.mean(stats) if np.mean(stats) > 0 else 0
        }


class XRCameraOptimizer:
    def __init__(self):
        self.performance_history = deque(maxlen=60)
        self.quality_settings = {
            'ultra_low': {'scale': 0.25, 'skip_frames': 3, 'compression': 80},
            'low': {'scale': 0.5, 'skip_frames': 2, 'compression': 85},
            'medium': {'scale': 0.75, 'skip_frames': 1, 'compression': 90},
            'high': {'scale': 1.0, 'skip_frames': 0, 'compression': 95},
        }
        self.current_quality = 'high'
        self.target_fps = 30
        self.target_latency = 0.033
        
    def auto_adjust_quality(self, current_fps: float, current_latency: float):
        self.performance_history.append({
            'fps': current_fps,
            'latency': current_latency,
            'timestamp': time.time()
        })
        
        if len(self.performance_history) < 10:
            return self.current_quality
        
        avg_fps = np.mean([p['fps'] for p in self.performance_history])
        avg_latency = np.mean([p['latency'] for p in self.performance_history])
        
        if avg_fps < self.target_fps * 0.8 or avg_latency > self.target_latency * 1.5:
            if self.current_quality == 'high':
                self.current_quality = 'medium'
            elif self.current_quality == 'medium':
                self.current_quality = 'low'
            elif self.current_quality == 'low':
                self.current_quality = 'ultra_low'
        elif avg_fps > self.target_fps * 0.95 and avg_latency < self.target_latency * 0.8:
            if self.current_quality == 'ultra_low':
                self.current_quality = 'low'
            elif self.current_quality == 'low':
                self.current_quality = 'medium'
            elif self.current_quality == 'medium':
                self.current_quality = 'high'
        
        return self.current_quality
    
    def get_quality_settings(self) -> Dict[str, Any]:
        return self.quality_settings[self.current_quality]
    
    def apply_frame_scaling(self, frame: np.ndarray, quality: str = None) -> np.ndarray:
        if quality is None:
            quality = self.current_quality
        
        scale = self.quality_settings[quality]['scale']
        if scale == 1.0:
            return frame
        
        h, w = frame.shape[:2]
        new_h, new_w = int(h * scale), int(w * scale)
        return cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_AREA)
    
    def should_skip_frame(self, frame_count: int) -> bool:
        skip_frames = self.quality_settings[self.current_quality]['skip_frames']
        if skip_frames == 0:
            return False
        return frame_count % (skip_frames + 1) != 0


def denoise_temporal(frames: List[np.ndarray], strength: float = 0.3) -> np.ndarray:
    if len(frames) < 3:
        return frames[-1] if frames else None
    
    weights = np.array([0.2, 0.3, 0.5]) * strength
    weights = weights / weights.sum()
    
    result = np.zeros_like(frames[-1], dtype=np.float32)
    for i, (frame, weight) in enumerate(zip(frames[-3:], weights)):
        result += frame.astype(np.float32) * weight
    
    return result.astype(np.uint8)


def apply_hdr_tone_mapping(frame: np.ndarray, gamma: float = 2.2) -> np.ndarray:
    normalized = frame.astype(np.float32) / 255.0
    
    tone_mapped = normalized / (normalized + 1)
    
    gamma_corrected = np.power(tone_mapped, 1.0 / gamma)
    
    return (gamma_corrected * 255).astype(np.uint8)
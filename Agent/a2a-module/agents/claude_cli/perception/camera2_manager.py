import android
from android.hardware.camera2 import CameraManager, CameraDevice, CameraCaptureSession
from android.hardware.camera2 import CameraCharacteristics, CaptureRequest, CameraMetadata
from android.media import ImageReader, Image
from android.graphics import ImageFormat, SurfaceTexture
from android.view import Surface
from android.os import Handler, HandlerThread
from android.util import Size, Range
import numpy as np
from typing import Optional, Callable, List, Tuple
import threading
from collections import deque
import time


class Camera2XRManager:
    def __init__(self, context, target_fps: int = 30):
        self.context = context
        self.camera_manager = context.getSystemService(android.content.Context.CAMERA_SERVICE)
        self.camera_device: Optional[CameraDevice] = None
        self.capture_session: Optional[CameraCaptureSession] = None
        self.image_reader: Optional[ImageReader] = None
        self.background_thread: Optional[HandlerThread] = None
        self.background_handler: Optional[Handler] = None
        self.target_fps = target_fps
        self.frame_callback: Optional[Callable] = None
        self.roi_regions: List[Tuple[int, int, int, int]] = []
        self.frame_buffer = deque(maxlen=3)
        self.processing_lock = threading.Lock()
        self.is_processing = False
        self.last_frame_time = 0
        self.frame_skip_threshold = 1.0 / target_fps
        
        self.optimal_preview_size: Optional[Size] = None
        self.sensor_orientation: int = 0
        self.camera_id: Optional[str] = None
        
    def initialize_for_xr(self, preferred_resolution: Tuple[int, int] = (1920, 1080)) -> bool:
        try:
            self.camera_id = self._select_optimal_camera()
            if not self.camera_id:
                return False
                
            characteristics = self.camera_manager.getCameraCharacteristics(self.camera_id)
            
            self.sensor_orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            
            self.optimal_preview_size = self._get_optimal_preview_size(
                characteristics, preferred_resolution
            )
            
            self._start_background_thread()
            
            self._configure_image_reader()
            
            return True
        except Exception as e:
            print(f"XR Camera initialization failed: {e}")
            return False
    
    def _select_optimal_camera(self) -> Optional[str]:
        camera_ids = self.camera_manager.getCameraIdList()
        best_camera_id = None
        best_score = 0
        
        for camera_id in camera_ids:
            characteristics = self.camera_manager.getCameraCharacteristics(camera_id)
            
            facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if facing != CameraCharacteristics.LENS_FACING_BACK:
                continue
            
            hardware_level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            
            score = 0
            if hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                score += 100
            elif hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                score += 50
            elif hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                score += 150
                
            if CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities:
                score += 50
            if CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities:
                score += 30
            if CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE in capabilities:
                score += 20
                
            if score > best_score:
                best_score = score
                best_camera_id = camera_id
                
        return best_camera_id
    
    def _get_optimal_preview_size(self, characteristics, target_resolution: Tuple[int, int]) -> Size:
        stream_config_map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        sizes = stream_config_map.getOutputSizes(ImageFormat.YUV_420_888)
        
        target_ratio = target_resolution[0] / target_resolution[1]
        target_area = target_resolution[0] * target_resolution[1]
        
        best_size = None
        min_diff = float('inf')
        
        for size in sizes:
            width = size.getWidth()
            height = size.getHeight()
            ratio = width / height
            area = width * height
            
            if area > target_area * 1.5:
                continue
                
            ratio_diff = abs(ratio - target_ratio)
            area_diff = abs(area - target_area) / target_area
            
            combined_diff = ratio_diff + area_diff * 0.5
            
            if combined_diff < min_diff:
                min_diff = combined_diff
                best_size = size
                
        return best_size or sizes[0]
    
    def _configure_image_reader(self):
        self.image_reader = ImageReader.newInstance(
            self.optimal_preview_size.getWidth(),
            self.optimal_preview_size.getHeight(),
            ImageFormat.YUV_420_888,
            3
        )
        
        self.image_reader.setOnImageAvailableListener(
            self._on_image_available,
            self.background_handler
        )
    
    def _on_image_available(self, reader: ImageReader):
        current_time = time.time()
        if current_time - self.last_frame_time < self.frame_skip_threshold:
            image = reader.acquireLatestImage()
            if image:
                image.close()
            return
            
        self.last_frame_time = current_time
        
        try:
            image = reader.acquireLatestImage()
            if not image:
                return
                
            if not self.is_processing:
                with self.processing_lock:
                    self.is_processing = True
                    self._process_frame_optimized(image)
                    self.is_processing = False
            else:
                self.frame_buffer.append(image)
                
        except Exception as e:
            print(f"Frame processing error: {e}")
            if image:
                image.close()
    
    def _process_frame_optimized(self, image: Image):
        try:
            planes = image.getPlanes()
            y_plane = planes[0]
            u_plane = planes[1]
            v_plane = planes[2]
            
            y_buffer = y_plane.getBuffer()
            u_buffer = u_plane.getBuffer()
            v_buffer = v_plane.getBuffer()
            
            y_data = np.frombuffer(y_buffer, dtype=np.uint8)
            u_data = np.frombuffer(u_buffer, dtype=np.uint8)
            v_data = np.frombuffer(v_buffer, dtype=np.uint8)
            
            width = image.getWidth()
            height = image.getHeight()
            
            y_data = y_data[:width * height].reshape((height, width))
            
            uv_pixel_stride = u_plane.getPixelStride()
            if uv_pixel_stride == 2:
                u_data = u_data[::2]
                v_data = v_data[::2]
            
            u_data = u_data[:width * height // 4].reshape((height // 2, width // 2))
            v_data = v_data[:width * height // 4].reshape((height // 2, width // 2))
            
            if self.roi_regions:
                roi_frames = self._extract_rois_optimized(y_data, u_data, v_data)
                if self.frame_callback:
                    self.frame_callback(roi_frames, self.roi_regions)
            else:
                if self.frame_callback:
                    self.frame_callback({'full': (y_data, u_data, v_data)}, [(0, 0, width, height)])
                    
        finally:
            image.close()
    
    def _extract_rois_optimized(self, y_data: np.ndarray, u_data: np.ndarray, v_data: np.ndarray) -> dict:
        roi_frames = {}
        
        for idx, (x, y, w, h) in enumerate(self.roi_regions):
            roi_y = y_data[y:y+h, x:x+w].copy()
            
            u_x, u_y = x // 2, y // 2
            u_w, u_h = w // 2, h // 2
            roi_u = u_data[u_y:u_y+u_h, u_x:u_x+u_w].copy()
            roi_v = v_data[u_y:u_y+u_h, u_x:u_x+u_w].copy()
            
            roi_frames[f'roi_{idx}'] = (roi_y, roi_u, roi_v)
            
        return roi_frames
    
    def open_camera(self):
        if not self.camera_id:
            raise RuntimeError("Camera not initialized. Call initialize_for_xr() first.")
            
        self.camera_manager.openCamera(
            self.camera_id,
            self._camera_device_callback(),
            self.background_handler
        )
    
    def _camera_device_callback(self):
        class CameraDeviceCallback(CameraDevice.StateCallback):
            def __init__(self, manager):
                self.manager = manager
                
            def onOpened(self, camera: CameraDevice):
                self.manager.camera_device = camera
                self.manager._create_capture_session()
                
            def onDisconnected(self, camera: CameraDevice):
                camera.close()
                self.manager.camera_device = None
                
            def onError(self, camera: CameraDevice, error: int):
                camera.close()
                self.manager.camera_device = None
                print(f"Camera error: {error}")
                
        return CameraDeviceCallback(self)
    
    def _create_capture_session(self):
        if not self.camera_device or not self.image_reader:
            return
            
        surfaces = [self.image_reader.getSurface()]
        
        self.camera_device.createCaptureSession(
            surfaces,
            self._capture_session_callback(),
            self.background_handler
        )
    
    def _capture_session_callback(self):
        class CaptureSessionCallback(CameraCaptureSession.StateCallback):
            def __init__(self, manager):
                self.manager = manager
                
            def onConfigured(self, session: CameraCaptureSession):
                self.manager.capture_session = session
                self.manager._start_preview()
                
            def onConfigureFailed(self, session: CameraCaptureSession):
                print("Capture session configuration failed")
                
        return CaptureSessionCallback(self)
    
    def _start_preview(self):
        if not self.camera_device or not self.capture_session:
            return
            
        preview_request_builder = self.camera_device.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        )
        preview_request_builder.addTarget(self.image_reader.getSurface())
        
        preview_request_builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        preview_request_builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        preview_request_builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        
        characteristics = self.camera_manager.getCameraCharacteristics(self.camera_id)
        fps_ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        
        optimal_range = None
        for fps_range in fps_ranges:
            if fps_range.getLower() <= self.target_fps <= fps_range.getUpper():
                if not optimal_range or (fps_range.getUpper() - fps_range.getLower()) < (optimal_range.getUpper() - optimal_range.getLower()):
                    optimal_range = fps_range
                    
        if optimal_range:
            preview_request_builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, optimal_range)
        
        preview_request_builder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST)
        preview_request_builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
        
        preview_request = preview_request_builder.build()
        self.capture_session.setRepeatingRequest(preview_request, None, self.background_handler)
    
    def set_roi_regions(self, regions: List[Tuple[int, int, int, int]]):
        self.roi_regions = regions
    
    def set_frame_callback(self, callback: Callable):
        self.frame_callback = callback
    
    def _start_background_thread(self):
        self.background_thread = HandlerThread("CameraBackground")
        self.background_thread.start()
        self.background_handler = Handler(self.background_thread.getLooper())
    
    def _stop_background_thread(self):
        if self.background_thread:
            self.background_thread.quitSafely()
            try:
                self.background_thread.join()
            except:
                pass
            self.background_thread = None
            self.background_handler = None
    
    def release(self):
        if self.capture_session:
            self.capture_session.close()
            self.capture_session = None
            
        if self.camera_device:
            self.camera_device.close()
            self.camera_device = None
            
        if self.image_reader:
            self.image_reader.close()
            self.image_reader = None
            
        self._stop_background_thread()
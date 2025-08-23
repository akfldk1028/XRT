package com.example.XRTEST.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera2Manager for AR Glass Q&A System
 * Handles camera initialization, frame processing, and ROI extraction for crosshair targeting
 */
class Camera2Manager(private val context: Context) {
    
    companion object {
        private const val TAG = "Camera2Manager"
        private const val IMAGE_FORMAT = ImageFormat.YUV_420_888
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080
        private const val ROI_SIZE = 320 // 320x320 ROI around crosshair center
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageReader: ImageReader? = null
    private var previewSize: Size? = null
    private var previewSurface: Surface? = null  // 프리뷰 화면용 Surface (UI display only)
    private var latestRawImage: Image? = null  // Store latest raw image from ImageReader

    // State flows for UI
    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    private val _frameProcessed = MutableStateFlow<ByteArray?>(null)
    val frameProcessed: StateFlow<ByteArray?> = _frameProcessed.asStateFlow()

    // Camera state callback
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            // Log.d(TAG, "Camera opened successfully") // Reduced logging
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            // Log.d(TAG, "Camera disconnected") // Reduced logging
            camera.close()
            cameraDevice = null
            _isCameraReady.value = false
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            camera.close()
            cameraDevice = null
            _isCameraReady.value = false
        }
    }

    /**
     * Check if camera permission is granted
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Initialize camera system
     */
    fun initialize() {
        if (!hasCameraPermission()) {
            Log.e(TAG, "Camera permission not granted")
            return
        }
        startBackgroundThread()
        setupCamera()
        
        // Debug: List all available cameras
        listAvailableCameras()
    }
    
    /**
     * List all available cameras for debugging
     */
    fun listAvailableCameras() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Log.d(TAG, "=== Available Cameras Debug Info ===") // Reduced logging
        // Log.d(TAG, "Total cameras found: ${manager.cameraIdList.size}")
        
        if (manager.cameraIdList.isEmpty()) {
            Log.e(TAG, "⚠️ NO CAMERAS DETECTED!")
            Log.e(TAG, "To fix in AVD Manager:")
            Log.e(TAG, "1. Edit your AVD")
            Log.e(TAG, "2. Advanced Settings")
            Log.e(TAG, "3. Set Front Camera: Webcam0")
            Log.e(TAG, "4. Set Back Camera: Webcam0")
            return
        }
        
        for (cameraId in manager.cameraIdList) {
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val outputSizes = streamMap?.getOutputSizes(ImageFormat.YUV_420_888)
                
                // Log.d(TAG, "Camera ID: $cameraId") // Reduced logging
                // Log.d(TAG, "  Facing: ${getFacingString(facing)}")
                // Log.d(TAG, "  Available sizes: ${outputSizes?.take(3)?.joinToString { "${it.width}x${it.height}" }}")
                
                // Check if external/webcam
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    // Log.d(TAG, "  >>> This is a WEBCAM/USB camera <<<") // Reduced logging
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting info for camera $cameraId: ${e.message}")
            }
        }
        // Log.d(TAG, "=====================================") // Reduced logging
    }
    
    private fun getFacingString(facing: Int?): String {
        return when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL (Webcam/USB)"
            else -> "UNKNOWN ($facing)"
        }
    }

    /**
     * Start camera capture
     */
    fun startCamera() {
        if (!hasCameraPermission()) {
            Log.e(TAG, "Cannot start camera: permission not granted")
            return
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            val cameraId = getCameraId(manager)
            // Log.d(TAG, "Attempting to open camera: $cameraId") // Reduced logging
            
            // 카메라 특성 가져오기 시도
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = streamMap?.getOutputSizes(ImageFormat.YUV_420_888)
                // Log.d(TAG, "Camera $cameraId supports ${sizes?.size ?: 0} YUV sizes") // Reduced logging
                
                // Log first 3 supported sizes
                sizes?.take(3)?.forEach { size ->
                    // Log.d(TAG, "  - ${size.width}x${size.height}") // Reduced logging
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot get camera characteristics for ID $cameraId, but continuing: ${e.message}")
            }
            
            // 카메라 열기
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    // Log.d(TAG, "Opening camera with ID: $cameraId") // Reduced logging
                    manager.openCamera(cameraId, stateCallback, backgroundHandler)
                } else {
                    Log.e(TAG, "Camera permission not granted")
                }
            } else {
                @Suppress("MissingPermission")
                manager.openCamera(cameraId, stateCallback, backgroundHandler)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Camera not available: ${e.message}")
            Log.e(TAG, "")
            Log.e(TAG, "🔧 CAMERA SETUP REQUIRED:")
            Log.e(TAG, "1. Check if host computer webcam is working")
            Log.e(TAG, "2. In AVD Manager → Edit AVD → Advanced Settings")
            Log.e(TAG, "3. Set Front Camera: Webcam0")
            Log.e(TAG, "4. Set Back Camera: Webcam0")
            Log.e(TAG, "5. Save and restart emulator")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera: ${e.message}")
            Log.e(TAG, "Error reason: ${getCameraAccessExceptionReason(e.reason)}")
            
            if (isEmulator()) {
                Log.e(TAG, "")
                Log.e(TAG, "🔧 EMULATOR CAMERA TROUBLESHOOTING:")
                Log.e(TAG, "1. Host computer webcam must be working")
                Log.e(TAG, "2. Restart emulator after changing AVD settings")
                Log.e(TAG, "3. Try: emulator -avd <your_avd> -camera-back webcam0")
                Log.e(TAG, "4. Check Windows Camera app can access webcam")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
        }
    }
    
    private fun getCameraAccessExceptionReason(reason: Int): String {
        return when (reason) {
            CameraAccessException.CAMERA_DISABLED -> "CAMERA_DISABLED"
            CameraAccessException.CAMERA_DISCONNECTED -> "CAMERA_DISCONNECTED"
            CameraAccessException.CAMERA_ERROR -> "CAMERA_ERROR"
            CameraAccessException.CAMERA_IN_USE -> "CAMERA_IN_USE (Another app using camera)"
            CameraAccessException.MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
            else -> "UNKNOWN ($reason)"
        }
    }

    /**
     * Set preview surface for displaying camera feed
     */
    fun setPreviewSurface(surface: Surface) {
        // Log.d(TAG, "Setting preview surface") // Reduced logging
        previewSurface = surface
        
        // 이미 카메라가 열려있으면 세션 재생성
        if (cameraDevice != null) {
            createCaptureSession()
        }
    }
    
    /**
     * Stop camera and cleanup resources
     */
    fun stopCamera() {
        captureSession?.close()
        captureSession = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        // Clean up raw image reference
        latestRawImage?.close()
        latestRawImage = null
        
        imageReader?.close()
        imageReader = null
        
        previewSurface = null
        
        stopBackgroundThread()
        _isCameraReady.value = false
        
        // Log.d(TAG, "Camera stopped and resources cleaned up") // Reduced logging
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { 
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun getCameraId(manager: CameraManager): String {
        // Log.d(TAG, "Available cameras: ${manager.cameraIdList.joinToString()}") // Reduced logging
        
        // 에뮬레이터 감지
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                        android.os.Build.MODEL.contains("Emulator") ||
                        android.os.Build.MODEL.contains("Android SDK") ||
                        android.os.Build.MODEL.contains("sdk_gphone")
        
        // Log.d(TAG, "Is Emulator: $isEmulator") // Reduced logging
        
        // 카메라 리스트가 비어있는 경우 처리
        if (manager.cameraIdList.isEmpty()) {
            Log.e(TAG, "⚠️ WARNING: No cameras detected!")
            Log.e(TAG, "📌 If camera fails:")
            Log.e(TAG, "   1. Check if webcam is enabled on host computer")
            Log.e(TAG, "   2. In AVD Manager → Edit → Advanced Settings")
            Log.e(TAG, "   3. Set Front Camera: Webcam0")
            Log.e(TAG, "   4. Set Back Camera: Webcam0")
            Log.e(TAG, "   5. Restart emulator")
            
            // 에뮬레이터에서는 기본값 시도
            if (isEmulator) {
                Log.e(TAG, "Emulator: Will try common camera IDs anyway...")
                // CRITICAL: Try webcam0 (ID "0") FIRST for Logitech webcam
                val commonIds = listOf("0", "1", "10")  // Changed order: 0 first for webcam
                for (id in commonIds) {
                    try {
                        manager.getCameraCharacteristics(id)
                        // Log.d(TAG, "✅ Found working camera ID: $id") // Reduced logging
                        return id
                    } catch (e: Exception) {
                        // Log.d(TAG, "Camera ID $id not available") // Reduced logging
                    }
                }
            }
            
            throw IllegalStateException("No cameras available")
        }
        
        // 카메라가 있는 경우: 사용 가능한 카메라 정보 출력
        // Log.d(TAG, "Found ${manager.cameraIdList.size} cameras") // Reduced logging
        
        // CRITICAL FIX: Try webcam ID "0" FIRST before listing all cameras
        // This ensures Logitech webcam (webcam0) is prioritized
        if (!manager.cameraIdList.contains("0")) {
            // If ID "0" is not in the list, try it anyway (emulator might not report it)
            try {
                manager.getCameraCharacteristics("0")
                // Log.d(TAG, "🎯 WEBCAM FOUND: ID 0 (not in list but accessible)") // Reduced logging
                return "0"
            } catch (e: Exception) {
                // Log.d(TAG, "Camera ID 0 (webcam) not accessible: ${e.message}") // Reduced logging
            }
        } else if (manager.cameraIdList.contains("0")) {
            // ID "0" is in the list, use it immediately
            // Log.d(TAG, "🎯 WEBCAM FOUND: ID 0 (Logitech webcam)") // Reduced logging
            return "0"
        }
        
        // List all available cameras for debugging
        for (cameraId in manager.cameraIdList) {
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                // Log.d(TAG, "Camera ID $cameraId: ${getFacingString(facing)}") // Reduced logging
            } catch (e: Exception) {
                Log.e(TAG, "Error checking camera $cameraId: ${e.message}")
            }
        }
        
        // 실제 사용 가능한 카메라 선택 (우선순위: 0 > External > Back > Front > Any)
        
        // 1. 외부 카메라 (웹캠) 우선
        for (cameraId in manager.cameraIdList) {
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    // Log.d(TAG, "✅ Using EXTERNAL camera: $cameraId") // Reduced logging
                    return cameraId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing camera $cameraId: ${e.message}")
            }
        }
        
        // 2. ID "1" 시도 (에뮬레이터 전면 카메라)
        if (manager.cameraIdList.contains("1")) {
            try {
                val characteristics = manager.getCameraCharacteristics("1")
                // Log.d(TAG, "✅ Using camera ID 1 (emulator front camera)") // Reduced logging
                return "1"
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing camera 1: ${e.message}")
            }
        }
        
        // 3. 후면 카메라
        for (cameraId in manager.cameraIdList) {
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    // Log.d(TAG, "✅ Using BACK camera: $cameraId") // Reduced logging
                    return cameraId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing camera $cameraId: ${e.message}")
            }
        }
        
        // 4. 전면 카메라
        for (cameraId in manager.cameraIdList) {
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    // Log.d(TAG, "✅ Using FRONT camera: $cameraId") // Reduced logging
                    return cameraId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing camera $cameraId: ${e.message}")
            }
        }
        
        // 5. Fallback: 첫 번째 사용 가능한 카메라
        val fallbackId = manager.cameraIdList.firstOrNull()
        if (fallbackId != null) {
            // Log.d(TAG, "✅ Using first available camera: $fallbackId") // Reduced logging
            return fallbackId
        }
        
        // 이 시점에 도달하면 안됨 (카메라 리스트가 비어있지 않다고 확인했으므로)
        throw IllegalStateException("Unable to select camera despite having ${manager.cameraIdList.size} cameras")
    }

    private fun setupCamera() {
        // Log.d(TAG, "Camera setup initiated") // Reduced logging
        
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            // Get camera ID and characteristics
            val cameraId = getCameraId(manager)
            // Log.d(TAG, "Setting up camera with ID: $cameraId") // Reduced logging
            
            // Try to get camera characteristics
            val characteristics = try {
                manager.getCameraCharacteristics(cameraId)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot get characteristics for camera $cameraId: ${e.message}")
                
                // Use default configuration if we can't get characteristics
                Log.w(TAG, "Using default 640x480 configuration")
                previewSize = Size(640, 480)
                imageReader = ImageReader.newInstance(
                    640,
                    480,
                    IMAGE_FORMAT,
                    2
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        processImage(reader.acquireLatestImage())
                    }, backgroundHandler)
                }
                return
            }
            
            // Configure ImageReader for frame capture
            val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = streamMap?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
            
            if (outputSizes.isEmpty()) {
                Log.w(TAG, "No YUV output sizes available, using default 640x480")
                previewSize = Size(640, 480)
            } else {
                // Select appropriate preview size
                previewSize = chooseOptimalSize(outputSizes)
                // Log.d(TAG, "Selected preview size: ${previewSize?.width}x${previewSize?.height}") // Reduced logging
            }
            
            // Create ImageReader for RAW frame capture (no UI overlays)
            // This captures directly from camera sensor, crosshair is NOT included
            imageReader = ImageReader.newInstance(
                previewSize?.width ?: 640,
                previewSize?.height ?: 480,
                IMAGE_FORMAT,
                2 // Max 2 images in buffer
            ).apply {
                setOnImageAvailableListener({ reader ->
                    // Process RAW camera image (no UI elements)
                    val rawImage = reader.acquireLatestImage()
                    latestRawImage?.close() // Close previous image
                    latestRawImage = rawImage // Store for capture
                    processImage(rawImage)
                }, backgroundHandler)
            }
            
            // Log.d(TAG, "ImageReader created successfully") // Reduced logging
            
        } catch (e: IllegalStateException) {
            Log.e(TAG, "No cameras available: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in camera setup: ${e.message}", e)
            
            // Fallback to default configuration
            Log.w(TAG, "Falling back to default 640x480 configuration")
            previewSize = Size(640, 480)
            imageReader = ImageReader.newInstance(640, 480, IMAGE_FORMAT, 2).apply {
                setOnImageAvailableListener({ reader ->
                    processImage(reader.acquireLatestImage())
                }, backgroundHandler)
            }
        }
    }
    
    private fun chooseOptimalSize(choices: Array<Size>): Size {
        // For emulator, prefer smaller sizes for performance
        val preferredSizes = listOf(
            Size(640, 480),   // VGA
            Size(1280, 720),  // HD
            Size(800, 600),   // SVGA
            Size(1920, 1080)  // Full HD
        )
        
        for (preferred in preferredSizes) {
            if (choices.contains(preferred)) {
                // Log.d(TAG, "Using preferred size: ${preferred.width}x${preferred.height}") // Reduced logging
                return preferred
            }
        }
        
        // Filter sizes that are too large
        val suitable = choices.filter { 
            it.width <= MAX_PREVIEW_WIDTH && it.height <= MAX_PREVIEW_HEIGHT 
        }
        
        // Return the largest suitable size, or first available
        return suitable.maxByOrNull { it.width * it.height } ?: choices.firstOrNull() ?: Size(640, 480)
    }
    
    private fun processImage(image: Image?) {
        image?.let {
            try {
                // Process the image frame - capture ALL YUV planes for proper conversion
                // YUV_420_888 has 3 planes: Y, U, V
                val planes = it.planes
                val ySize = planes[0].buffer.remaining()
                val uSize = planes[1].buffer.remaining()
                val vSize = planes[2].buffer.remaining()
                
                // Create byte array for complete YUV data
                val nv21 = ByteArray(ySize + uSize + vSize)
                
                // Copy Y plane
                planes[0].buffer.get(nv21, 0, ySize)
                
                // Interleave U and V planes to create NV21 format
                val pixelStride = planes[2].pixelStride
                if (pixelStride == 2) {
                    // UV planes are already interleaved
                    planes[1].buffer.get(nv21, ySize, uSize)
                } else {
                    // Need to interleave U and V
                    var pos = ySize
                    val uvBuffer1 = planes[1].buffer
                    val uvBuffer2 = planes[2].buffer
                    for (i in 0 until uSize) {
                        nv21[pos++] = uvBuffer1.get()
                        if (i < vSize) {
                            nv21[pos++] = uvBuffer2.get()
                        }
                    }
                }
                
                // Store complete YUV data for capture
                _frameProcessed.value = nv21
                
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}")
                it.close()
            }
        }
    }

    private fun createCaptureSession() {
        try {
            // Log.d(TAG, "Creating capture session") // Reduced logging
            
            val imageReaderSurface = imageReader?.surface
            if (imageReaderSurface == null) {
                Log.e(TAG, "ImageReader surface is null")
                return
            }
            
            // Create surface list:
            // 1. ImageReader surface - for RAW camera capture (NO UI overlays)
            // 2. Preview surface - for display with crosshair overlay
            val surfaces = mutableListOf(imageReaderSurface)
            
            previewSurface?.let { displaySurface ->
                surfaces.add(displaySurface)
                Log.d(TAG, "Session configured: ImageReader (raw capture) + TextureView (display)")
            }
            
            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        // Log.d(TAG, "✅ Capture session configured successfully") // Reduced logging
                        captureSession = session
                        _isCameraReady.value = true
                        startPreview()
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "❌ Failed to configure capture session")
                        _isCameraReady.value = false
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
        }
    }
    
    private fun startPreview() {
        try {
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            
            // CRITICAL: Add ImageReader surface for RAW capture (no UI overlays)
            requestBuilder?.addTarget(imageReader?.surface!!)
            
            // Add preview surface for display (this shows to user with crosshair overlay)
            previewSurface?.let {
                requestBuilder?.addTarget(it)
                // Both surfaces get same camera feed, but:
                // - ImageReader: RAW data for AI processing (NO crosshair)
                // - TextureView: Display with UI overlays (WITH crosshair)
            }
            
            // Set auto-focus and auto-exposure
            requestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            requestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            
            captureSession?.setRepeatingRequest(
                requestBuilder?.build()!!,
                null,
                backgroundHandler
            )
            
            Log.d(TAG, "✅ Preview started: ImageReader captures RAW, TextureView shows UI")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview: ${e.message}", e)
        }
    }

    /**
     * Get current camera status
     */
    fun getCameraStatus(): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraCount = manager.cameraIdList.size
        
        return when {
            !hasCameraPermission() -> "PERMISSION_DENIED"
            cameraCount == 0 -> "NO_CAMERAS_FOUND (Check AVD settings)"
            cameraDevice == null -> "CAMERA_CLOSED (${cameraCount} cameras available)"
            captureSession == null -> "SESSION_NOT_READY"
            _isCameraReady.value -> "READY"
            else -> "INITIALIZING"
        }
    }
    
    /**
     * Debug function to get detailed camera info
     */
    fun debugCameraInfo(): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraCount = manager.cameraIdList.size
        
        // Return minimal info for clean display
        return when {
            cameraCount == 0 -> {
                // Only show detailed instructions when no camera is detected
                """⚠️ NO CAMERAS DETECTED!
                
TO FIX:
1. Close emulator
2. AVD Manager → Edit AVD
3. Show Advanced Settings
4. Camera:
   Front: Webcam0
   Back: Webcam0
5. Save & restart"""
            }
            else -> {
                // Simple status when camera is working
                "Camera: ${cameraCount} device(s) found"
            }
        }
    }
    
    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.contains("generic") ||
               android.os.Build.MODEL.contains("Emulator") ||
               android.os.Build.MODEL.contains("Android SDK") ||
               android.os.Build.MODEL.contains("sdk_gphone")
    }
    
    /**
     * Enable test pattern mode for debugging when webcam is not available
     * This generates a synthetic test image instead of real camera feed
     */
    fun useTestPattern(enable: Boolean) {
        if (enable && isEmulator()) {
            // Log.d(TAG, "📷 TEST PATTERN MODE ENABLED") // Reduced logging
            // Log.d(TAG, "Generating synthetic test images for debugging")
            
            // Create a test pattern generator
            startTestPatternGenerator()
        } else {
            stopTestPatternGenerator()
        }
    }
    
    private var testPatternTimer: java.util.Timer? = null
    private var frameCounter = 0
    
    private fun startTestPatternGenerator() {
        stopTestPatternGenerator() // Stop any existing timer
        
        testPatternTimer = java.util.Timer()
        testPatternTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                generateTestFrame()
            }
        }, 0, 33) // ~30 FPS
    }
    
    private fun stopTestPatternGenerator() {
        testPatternTimer?.cancel()
        testPatternTimer = null
    }
    
    private fun generateTestFrame() {
        frameCounter++
        
        // Generate a simple test pattern (gradient with moving bar)
        val width = 640
        val height = 480
        val testData = ByteArray(width * height * 3 / 2) // YUV420 format
        
        // Fill Y plane with gradient and moving bar
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                
                // Create a gradient background
                var value = ((x.toFloat() / width) * 255).toInt()
                
                // Add a moving vertical bar
                val barPosition = (frameCounter * 5) % width
                if (Math.abs(x - barPosition) < 20) {
                    value = 255
                }
                
                // Add crosshair at center
                val centerX = width / 2
                val centerY = height / 2
                if ((Math.abs(x - centerX) < 2 && Math.abs(y - centerY) < 30) ||
                    (Math.abs(y - centerY) < 2 && Math.abs(x - centerX) < 30)) {
                    value = 0 // Black crosshair
                }
                
                testData[index] = value.toByte()
            }
        }
        
        // Fill U and V planes with neutral values
        val uvSize = width * height / 4
        for (i in 0 until uvSize * 2) {
            testData[width * height + i] = 128.toByte()
        }
        
        // Add frame info overlay
        // Log.v(TAG, "Generated test frame #$frameCounter") // REMOVED - Too spammy!
        
        // Send to processing pipeline
        _frameProcessed.value = testData
    }
    
    /**
     * Capture current frame as JPEG and return it
     * This method captures the RAW camera feed from ImageReader WITHOUT any UI overlays
     * The crosshair is ONLY a visual guide and will NOT appear in captured images
     */
    fun captureCurrentFrameAsJpeg(): ByteArray? {
        // Get the latest raw frame from ImageReader (no UI overlays)
        val currentFrame = _frameProcessed.value ?: run {
            Log.w(TAG, "No frame available to capture")
            return null
        }
        
        return try {
            // Convert raw YUV camera data to JPEG
            // This is pure camera feed - crosshair is NOT included
            val jpegData = convertYuvToJpeg(currentFrame)
            Log.d(TAG, "Captured RAW camera frame as JPEG: ${jpegData.size} bytes (no UI overlays)")
            jpegData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert frame to JPEG: ${e.message}")
            null
        }
    }
    
    /**
     * Convert YUV frame data to JPEG
     */
    private fun convertYuvToJpeg(yuvData: ByteArray): ByteArray {
        val width = previewSize?.width ?: 640
        val height = previewSize?.height ?: 480
        
        // Create YuvImage from the data
        val yuvImage = android.graphics.YuvImage(
            yuvData,
            android.graphics.ImageFormat.NV21, // YUV format
            width,
            height,
            null
        )
        
        // Compress to JPEG
        val outputStream = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, width, height),
            85, // JPEG quality (0-100)
            outputStream
        )
        
        return outputStream.toByteArray()
    }
    
    /**
     * Manual webcam troubleshooting steps
     */
    fun getWebcamTroubleshootingGuide(): String {
        return """
        🔧 EMULATOR WEBCAM TROUBLESHOOTING GUIDE
        
        STEP 1: Verify Host Webcam
        ├─ Windows: Camera app → Should see your webcam
        ├─ Mac: Photo Booth → Should see your webcam  
        └─ Linux: cheese/guvcview → Should see your webcam
        
        STEP 2: Configure AVD
        ├─ Close emulator completely
        ├─ Android Studio → Tools → AVD Manager
        ├─ Edit AVD → Show Advanced Settings
        ├─ Camera section:
        │  ├─ Front Camera: Webcam0
        │  └─ Back Camera: Webcam0
        └─ Finish → Cold Boot Now (not Quick Boot)
        
        STEP 3: Launch with Command Line
        ├─ Find AVD name: emulator -list-avds
        └─ Launch: emulator -avd <name> -camera-back webcam0 -camera-front webcam0
        
        STEP 4: Windows Privacy Settings
        ├─ Settings → Privacy → Camera
        ├─ Allow apps to access camera: ON
        ├─ Allow desktop apps: ON
        └─ Check Android Studio has permission
        
        STEP 5: Alternative Solutions
        ├─ Use test pattern: camera2Manager.useTestPattern(true)
        ├─ Use static image: Load from assets
        └─ Use Genymotion emulator (better camera support)
        
        STEP 6: Verify in Emulator
        ├─ Open default Camera app in emulator
        ├─ Should see webcam feed (not black/white)
        └─ If Camera app works but your app doesn't → code issue
        
        COMMON ISSUES:
        ❌ White screen → Webcam not connected
        ❌ Black screen → Permission denied or camera in use
        ❌ No cameras found → AVD misconfigured
        ❌ Crash → Wrong camera ID or format
        """.trimIndent()
    }
}

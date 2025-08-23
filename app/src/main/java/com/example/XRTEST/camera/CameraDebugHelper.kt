package com.example.XRTEST.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.graphics.ImageFormat
import android.os.Build
import android.util.Log

/**
 * Camera Debug Helper for troubleshooting webcam issues in Android emulator
 * Provides comprehensive diagnostics and testing utilities
 */
class CameraDebugHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraDebugHelper"
        private const val DEBUG_ENABLED = false // Disable verbose logging
        private const val MINIMAL_OUTPUT = true // Enable minimal output mode
    }
    
    /**
     * Run full camera diagnostics
     */
    fun runFullDiagnostics(): String {
        if (MINIMAL_OUTPUT) {
            // Return minimal info for cleaner display
            return getQuickSummary()
        }
        
        val sb = StringBuilder()
        
        // Camera availability
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = manager.cameraIdList
        
        sb.appendLine("📸 CAMERA AVAILABILITY:")
        sb.appendLine("  Total Cameras: ${cameraIds.size}")
        sb.appendLine("  Camera IDs: ${cameraIds.joinToString(", ")}")
        sb.appendLine("")
        
        if (cameraIds.isEmpty()) {
            sb.appendLine("⚠️ NO CAMERAS DETECTED!")
            sb.appendLine("")
            sb.appendLine("🔧 TROUBLESHOOTING STEPS:")

        } else {
            // Detailed camera info
            sb.appendLine("📋 DETAILED CAMERA INFO:")
            
//            for (cameraId in cameraIds) {
//                sb.appendLine("")
//                sb.appendLine("Camera ID: $cameraId")
//                sb.appendLine("------------------------")
//
//                try {
//                    val characteristics = manager.getCameraCharacteristics(cameraId)
//
//                    // Facing
//                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
//                    sb.appendLine("  Facing: ${getFacingString(facing)}")
//
//                    // Capabilities
//                    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
//                    val hasBackwardCompat = capabilities?.contains(
//                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
//                    ) ?: false
//                    sb.appendLine("  Backward Compatible: $hasBackwardCompat")
//
//                    // Hardware level
//                    val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
//                    sb.appendLine("  Hardware Level: ${getHardwareLevelString(hardwareLevel)}")
//
//                    // Stream configurations
//                    val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//
//                    // YUV sizes
//                    val yuvSizes = streamMap?.getOutputSizes(ImageFormat.YUV_420_888)
//                    sb.appendLine("  YUV_420_888 Sizes: ${yuvSizes?.size ?: 0}")
//                    if (yuvSizes != null && yuvSizes.isNotEmpty()) {
//                        sb.appendLine("    Top 3: ${yuvSizes.take(3).joinToString { "${it.width}x${it.height}" }}")
//                    }
//
//                    // JPEG sizes
//                    val jpegSizes = streamMap?.getOutputSizes(ImageFormat.JPEG)
//                    sb.appendLine("  JPEG Sizes: ${jpegSizes?.size ?: 0}")
//                    if (jpegSizes != null && jpegSizes.isNotEmpty()) {
//                        sb.appendLine("    Top 3: ${jpegSizes.take(3).joinToString { "${it.width}x${it.height}" }}")
//                    }
//
//                    // Physical ID (for logical cameras)
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                        val physicalIds = characteristics.physicalCameraIds
//                        if (physicalIds.isNotEmpty()) {
//                            sb.appendLine("  Physical Camera IDs: ${physicalIds.joinToString(", ")}")
//                        }
//                    }
//
//                } catch (e: Exception) {
//                    sb.appendLine("  ❌ Error reading characteristics: ${e.message}")
//                }
//            }
        }
        val result = sb.toString()
        // Log.d(TAG, result) // Disabled verbose logging
        return result
    }
    
    /**
     * Test if we can force-open camera ID "0"
     */
    fun testForceCameraZero(): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        return try {
            // Log.d(TAG, "Testing forced camera ID '0'...") // Disabled verbose logging
            val characteristics = manager.getCameraCharacteristics("0")
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            "✅ Camera '0' accessible! Type: ${getFacingString(facing)}"
        } catch (e: Exception) {
            "❌ Cannot access camera '0': ${e.message}"
        }
    }
    
    /**
     * Check if running on emulator
     */
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK") ||
               Build.MODEL.contains("sdk_gphone") ||
               Build.HARDWARE == "ranchu" ||
               Build.HARDWARE == "goldfish" ||
               Build.PRODUCT.contains("sdk") ||
               Build.BOARD == "goldfish" ||
               Build.BRAND == "generic" ||
               Build.DEVICE == "generic" ||
               Build.MANUFACTURER == "Google"
    }
    
    /**
     * Get emulator type
     */
    private fun getEmulatorType(): String {
        return when {
            Build.HARDWARE == "ranchu" -> "Android Studio Emulator (ranchu)"
            Build.HARDWARE == "goldfish" -> "Android Studio Emulator (goldfish)"
            Build.MODEL.contains("sdk_gphone") -> "Google Phone SDK"
            Build.MODEL.contains("Emulator") -> "Generic Emulator"
            else -> "Unknown Emulator Type"
        }
    }
    
    /**
     * Get facing string
     */
    private fun getFacingString(facing: Int?): String {
        return when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL (Webcam/USB)"
            else -> "UNKNOWN ($facing)"
        }
    }
    
    /**
     * Get hardware level string
     */
    private fun getHardwareLevelString(level: Int?): String {
        return when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN ($level)"
        }
    }
    
    /**
     * Generate AVD launch command
     */
    fun generateAVDLaunchCommand(avdName: String): String {
        return "emulator -avd $avdName -camera-back webcam0 -camera-front webcam0 -gpu host"
    }
    
    /**
     * Quick test summary
     */
    fun getQuickSummary(): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraCount = manager.cameraIdList.size
        val isEmulator = isEmulator()
        
        return when {
            cameraCount > 0 -> "✅ $cameraCount camera(s) detected"
            isEmulator -> "⚠️ Emulator with no cameras - Check AVD settings"
            else -> "❌ No cameras on physical device"
        }
    }
}
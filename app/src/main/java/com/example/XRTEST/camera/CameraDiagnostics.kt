package com.example.XRTEST.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

/**
 * Camera Diagnostics utility for debugging webcam issues in emulator
 */
class CameraDiagnostics(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraDiagnostics"
    }
    
    /**
     * Run complete camera diagnostics
     */
    fun runFullDiagnostics(): DiagnosticReport {
        val report = DiagnosticReport()
        
        // Check environment
        report.isEmulator = checkIfEmulator()
        report.androidVersion = Build.VERSION.SDK_INT
        report.deviceModel = Build.MODEL
        
        // Get camera manager
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // Check cameras
        report.totalCameras = manager.cameraIdList.size
        report.cameraIds = manager.cameraIdList.toList()
        
        // Analyze each camera
        for (cameraId in manager.cameraIdList) {
            try {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val info = CameraInfo(cameraId)
                
                // Get facing
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                info.facing = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL (WEBCAM)"
                    else -> "UNKNOWN"
                }
                
                // Check if it's likely a webcam
                info.isWebcam = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) ||
                               (report.isEmulator && cameraId == "0")
                
                // Get capabilities
                val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                info.hasBackwardCompatible = capabilities?.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                ) ?: false
                
                // Check supported formats
                val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                info.supportedFormats = streamMap?.outputFormats?.size ?: 0
                
                report.cameras.add(info)
                
            } catch (e: Exception) {
                // Log.e(TAG, "Error analyzing camera $cameraId: ${e.message}") // Reduced logging
            }
        }
        
        // Generate recommendations
        report.recommendations = generateRecommendations(report)
        
        return report
    }
    
    /**
     * Quick webcam check
     */
    fun quickWebcamCheck(): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sb = StringBuilder()
        
        sb.appendLine("🔍 QUICK WEBCAM CHECK")
        sb.appendLine("=" .repeat(30))
        
        if (manager.cameraIdList.isEmpty()) {
            sb.appendLine("❌ NO CAMERAS FOUND!")
            sb.appendLine("")
            sb.appendLine("ACTION REQUIRED:")
            sb.appendLine("1. Close this emulator")
            sb.appendLine("2. In Android Studio:")
            sb.appendLine("   Tools → AVD Manager")
            sb.appendLine("3. Edit your AVD (pencil icon)")
            sb.appendLine("4. Show Advanced Settings")
            sb.appendLine("5. Camera section:")
            sb.appendLine("   • Front Camera: Webcam0")
            sb.appendLine("   • Back Camera: Webcam0")
            sb.appendLine("6. Finish → Restart emulator")
            sb.appendLine("")
            sb.appendLine("ALSO CHECK:")
            sb.appendLine("• Your PC webcam is not in use")
            sb.appendLine("• No other apps using webcam")
            sb.appendLine("• Webcam drivers are installed")
        } else {
            sb.appendLine("✅ Found ${manager.cameraIdList.size} camera(s)")
            
            // Check for webcam
            var webcamFound = false
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL || 
                    (checkIfEmulator() && cameraId == "0")) {
                    webcamFound = true
                    sb.appendLine("✅ WEBCAM FOUND: Camera ID '$cameraId'")
                }
            }
            
            if (!webcamFound && checkIfEmulator()) {
                sb.appendLine("⚠️ No webcam detected")
                sb.appendLine("Using fallback camera: ${manager.cameraIdList.first()}")
            }
        }
        
        return sb.toString()
    }
    
    private fun checkIfEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK") ||
               Build.MODEL.contains("sdk_gphone") ||
               Build.BRAND == "google" && Build.DEVICE == "generic"
    }
    
    private fun generateRecommendations(report: DiagnosticReport): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (report.totalCameras == 0) {
            recommendations.add("No cameras detected - Configure AVD camera settings")
            recommendations.add("Set both Front and Back cameras to 'Webcam0' in AVD")
        } else if (report.isEmulator) {
            val hasWebcam = report.cameras.any { it.isWebcam }
            if (!hasWebcam) {
                recommendations.add("Emulator detected but no webcam found")
                recommendations.add("Verify AVD camera configuration")
            } else {
                recommendations.add("Webcam detected and ready for use")
            }
        }
        
        // Check for camera "0"
        if (report.isEmulator && !report.cameraIds.contains("0")) {
            recommendations.add("Camera ID '0' not found (common webcam ID)")
            recommendations.add("Using fallback: ${report.cameraIds.firstOrNull()}")
        }
        
        return recommendations
    }
    
    /**
     * Print diagnostics to log
     */
    fun logDiagnostics() {
        val report = runFullDiagnostics()
        
        // Reduced logging - only log critical info
        if (report.totalCameras == 0) {
            Log.e(TAG, "⚠️ NO CAMERAS DETECTED - Check AVD settings")
        } else {
            // Log.d(TAG, "✅ ${report.totalCameras} camera(s) found: ${report.cameraIds.joinToString()}") // Reduced logging
        }
        
        // Only log critical errors, not warnings
        if (report.recommendations.isNotEmpty() && report.recommendations.any { it.contains("ERROR") }) {
            Log.e(TAG, "Camera error: ${report.recommendations.filter { it.contains("ERROR") }.firstOrNull()}")
        }
    }
    
    data class DiagnosticReport(
        var isEmulator: Boolean = false,
        var androidVersion: Int = 0,
        var deviceModel: String = "",
        var totalCameras: Int = 0,
        var cameraIds: List<String> = emptyList(),
        val cameras: MutableList<CameraInfo> = mutableListOf(),
        var recommendations: List<String> = emptyList()
    )
    
    data class CameraInfo(
        val id: String,
        var facing: String = "",
        var isWebcam: Boolean = false,
        var hasBackwardCompatible: Boolean = false,
        var supportedFormats: Int = 0
    )
}
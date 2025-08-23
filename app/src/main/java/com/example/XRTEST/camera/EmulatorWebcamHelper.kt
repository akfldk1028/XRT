package com.example.XRTEST.camera

import android.content.Context
import android.graphics.*
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Helper class for emulator webcam issues and test image generation
 * Provides fallback solutions when real webcam feed is not available
 */
class EmulatorWebcamHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "EmulatorWebcamHelper"
        const val TEST_IMAGE_WIDTH = 640
        const val TEST_IMAGE_HEIGHT = 480
    }
    
    /**
     * Generate a test image with AR Glass UI overlay
     * Used when webcam feed is unavailable (white screen issue)
     */
    fun generateARTestImage(frameNumber: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        // Background gradient (simulating real scene)
        val gradient = LinearGradient(
            0f, 0f, TEST_IMAGE_WIDTH.toFloat(), TEST_IMAGE_HEIGHT.toFloat(),
            intArrayOf(
                Color.rgb(100, 120, 140),
                Color.rgb(150, 170, 190),
                Color.rgb(200, 210, 220)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, TEST_IMAGE_WIDTH.toFloat(), TEST_IMAGE_HEIGHT.toFloat(), paint)
        paint.shader = null
        
        // Add some "objects" to simulate a real scene
        drawTestObjects(canvas, paint, frameNumber)
        
        // Draw AR crosshair
        drawCrosshair(canvas, paint)
        
        // Draw test info overlay
        drawTestInfo(canvas, paint, frameNumber)
        
        // Convert bitmap to byte array
        return bitmapToYUV420(bitmap)
    }
    
    private fun drawTestObjects(canvas: Canvas, paint: Paint, frameNumber: Int) {
        // Moving circle (simulates object tracking)
        val circleX = (TEST_IMAGE_WIDTH / 2 + Math.sin(frameNumber * 0.05) * 150).toFloat()
        val circleY = (TEST_IMAGE_HEIGHT / 2 + Math.cos(frameNumber * 0.03) * 100).toFloat()
        
        paint.color = Color.argb(180, 255, 200, 100)
        canvas.drawCircle(circleX, circleY, 50f, paint)
        
        // Static rectangles (simulates furniture/walls)
        paint.color = Color.argb(150, 100, 150, 200)
        canvas.drawRect(50f, 100f, 150f, 300f, paint)
        canvas.drawRect(490f, 150f, 590f, 350f, paint)
        
        // Text labels (simulates recognized objects)
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.style = Paint.Style.FILL
        canvas.drawText("Object A", circleX - 30, circleY - 60, paint)
        canvas.drawText("Wall", 60f, 90f, paint)
        canvas.drawText("Table", 500f, 140f, paint)
    }
    
    private fun drawCrosshair(canvas: Canvas, paint: Paint) {
        val centerX = TEST_IMAGE_WIDTH / 2f
        val centerY = TEST_IMAGE_HEIGHT / 2f
        
        paint.color = Color.GREEN
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        // Crosshair lines
        canvas.drawLine(centerX - 30, centerY, centerX - 10, centerY, paint)
        canvas.drawLine(centerX + 10, centerY, centerX + 30, centerY, paint)
        canvas.drawLine(centerX, centerY - 30, centerX, centerY - 10, paint)
        canvas.drawLine(centerX, centerY + 10, centerX, centerY + 30, paint)
        
        // Center circle
        canvas.drawCircle(centerX, centerY, 5f, paint)
        
        // ROI box
        paint.color = Color.argb(100, 0, 255, 0)
        paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        canvas.drawRect(
            centerX - 160, centerY - 160,
            centerX + 160, centerY + 160,
            paint
        )
        paint.pathEffect = null
    }
    
    private fun drawTestInfo(canvas: Canvas, paint: Paint, frameNumber: Int) {
        paint.color = Color.YELLOW
        paint.textSize = 16f
        paint.style = Paint.Style.FILL
        
        // Test mode indicator
        canvas.drawText("📷 TEST MODE - Webcam Unavailable", 10f, 30f, paint)
        canvas.drawText("Frame: #$frameNumber", 10f, 50f, paint)
        canvas.drawText("Use real device or fix webcam settings", 10f, 70f, paint)
        
        // Instructions
        paint.color = Color.WHITE
        paint.textSize = 14f
        canvas.drawText("Tap to simulate Q&A interaction", 10f, TEST_IMAGE_HEIGHT - 40f, paint)
        canvas.drawText("ROI: 320x320 at crosshair", 10f, TEST_IMAGE_HEIGHT - 20f, paint)
    }
    
    /**
     * Convert Bitmap to YUV420 format (Camera2 output format)
     */
    private fun bitmapToYUV420(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        
        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int
        
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                r = (pixel shr 16) and 0xff
                g = (pixel shr 8) and 0xff
                b = pixel and 0xff
                
                // RGB to YUV conversion
                y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                
                // UV subsampling (4:2:0)
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        
        return yuv
    }
    
    /**
     * Get emulator webcam setup instructions as formatted string
     */
    fun getSetupInstructions(): String {
        return """
        🔧 EMULATOR WEBCAM SETUP GUIDE
        ================================
        
        QUICK FIX:
        1. Restart emulator with: 
           emulator -avd <name> -camera-back webcam0
        
        DETAILED STEPS:
        
        1️⃣ Check Host Webcam:
           • Windows: Open Camera app
           • Mac: Open Photo Booth
           • Linux: Run 'cheese'
           → Webcam must work here first!
        
        2️⃣ AVD Configuration:
           • Android Studio → Tools → AVD Manager
           • Click pencil icon to edit AVD
           • Show Advanced Settings
           • Camera section:
             - Front Camera: Webcam0
             - Back Camera: Webcam0
           • Finish → Cold Boot Now
        
        3️⃣ Command Line Launch:
           • List AVDs: emulator -list-avds
           • Launch: emulator -avd Pixel_XL_API_34 -camera-back webcam0
        
        4️⃣ Windows Specific:
           • Settings → Privacy → Camera
           • Allow apps to access camera: ON
           • Check Android Studio has permission
        
        5️⃣ Test in Emulator:
           • Open Camera app in emulator
           • Should see webcam feed
           • If not → AVD issue
           • If yes but app shows white → code issue
        
        ALTERNATIVE SOLUTIONS:
        • Use test pattern mode
        • Use Genymotion (better camera)
        • Test on real device
        """.trimIndent()
    }
    
    /**
     * Check if running in emulator
     */
    fun isRunningInEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK")
                || android.os.Build.MODEL.contains("sdk_gphone"))
    }
    
    /**
     * Generate diagnostic info for debugging
     */
    fun getDiagnosticInfo(): String {
        val info = StringBuilder()
        info.appendLine("📊 WEBCAM DIAGNOSTIC INFO")
        info.appendLine("========================")
        info.appendLine("Device: ${android.os.Build.MODEL}")
        info.appendLine("SDK: ${android.os.Build.VERSION.SDK_INT}")
        info.appendLine("Emulator: ${isRunningInEmulator()}")
        info.appendLine("Fingerprint: ${android.os.Build.FINGERPRINT}")
        
        if (isRunningInEmulator()) {
            info.appendLine("\n⚠️ EMULATOR DETECTED")
            info.appendLine("Webcam issues are common in emulators.")
            info.appendLine("Using test pattern is recommended.")
        }
        
        return info.toString()
    }
}
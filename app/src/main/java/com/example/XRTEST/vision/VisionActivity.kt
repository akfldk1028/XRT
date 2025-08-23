package com.example.XRTEST.vision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.XRTEST.camera.Camera2Manager
import com.example.XRTEST.ui.theme.XRTESTTheme
import com.example.XRTEST.voice.VoiceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * VisionActivity - Example activity demonstrating OpenAI Realtime API integration
 */
class VisionActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "VisionActivity"
        // Replace with your actual OpenAI API key or load from secure storage
        private const val OPENAI_API_KEY = "YOUR_OPENAI_API_KEY"
    }
    
    private lateinit var camera2Manager: Camera2Manager
    private lateinit var voiceManager: VoiceManager
    private lateinit var visionIntegration: VisionIntegration
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET
    )
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initializeSystem()
        } else {
            Log.e(TAG, "Required permissions not granted")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize managers
        camera2Manager = Camera2Manager(this)
        voiceManager = VoiceManager(this)
        
        // Check permissions
        if (hasAllPermissions()) {
            initializeSystem()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
        
        setContent {
            XRTESTTheme {
                VisionScreen()
            }
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun initializeSystem() {
        lifecycleScope.launch {
            try {
                // Initialize voice manager
                voiceManager.initialize()
                
                // Initialize vision integration
                visionIntegration = VisionIntegration(
                    context = this@VisionActivity,
                    apiKey = OPENAI_API_KEY,
                    camera2Manager = camera2Manager,
                    voiceManager = voiceManager
                )
                
                // Initialize the integration
                visionIntegration.initialize()
                
                // Start camera
                camera2Manager.startCamera()
                
                Log.d(TAG, "System initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "System initialization failed: ${e.message}")
            }
        }
    }
    
    @Composable
    fun VisionScreen() {
        var integrationState by remember { mutableStateOf(VisionIntegration.IntegrationState.IDLE) }
        var lastResponseText by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
        var queryText by remember { mutableStateOf("") }
        
        // Observe states
        LaunchedEffect(Unit) {
            if (::visionIntegration.isInitialized) {
                launch {
                    visionIntegration.state.collectLatest { state ->
                        integrationState = state
                    }
                }
                
                launch {
                    visionIntegration.lastResponse.collectLatest { response ->
                        response?.let {
                            lastResponseText = it.text
                        }
                    }
                }
                
                launch {
                    visionIntegration.isProcessing.collectLatest { processing ->
                        isProcessing = processing
                    }
                }
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (integrationState) {
                            VisionIntegration.IntegrationState.READY -> Color(0xFF4CAF50)
                            VisionIntegration.IntegrationState.LISTENING -> Color(0xFF2196F3)
                            VisionIntegration.IntegrationState.PROCESSING -> Color(0xFFFF9800)
                            VisionIntegration.IntegrationState.RESPONDING -> Color(0xFF9C27B0)
                            VisionIntegration.IntegrationState.ERROR -> Color(0xFFF44336)
                            else -> Color(0xFF757575)
                        }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (integrationState) {
                                VisionIntegration.IntegrationState.IDLE -> "System Idle"
                                VisionIntegration.IntegrationState.CONNECTING -> "Connecting to OpenAI..."
                                VisionIntegration.IntegrationState.READY -> "Ready for Q&A"
                                VisionIntegration.IntegrationState.LISTENING -> "Listening..."
                                VisionIntegration.IntegrationState.PROCESSING -> "Processing..."
                                VisionIntegration.IntegrationState.RESPONDING -> "Responding..."
                                VisionIntegration.IntegrationState.ERROR -> "Error Occurred"
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Response Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator()
                        } else if (lastResponseText.isNotEmpty()) {
                            Text(
                                text = lastResponseText,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Ask a question about what you see",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Input Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Text input
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        label = { Text("Ask a question") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = integrationState == VisionIntegration.IntegrationState.READY ||
                                integrationState == VisionIntegration.IntegrationState.LISTENING,
                        singleLine = true
                    )
                    
                    // Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Start/Stop Session Button
                        Button(
                            onClick = {
                                if (::visionIntegration.isInitialized) {
                                    when (integrationState) {
                                        VisionIntegration.IntegrationState.READY -> {
                                            visionIntegration.startSession()
                                        }
                                        VisionIntegration.IntegrationState.LISTENING -> {
                                            visionIntegration.stopSession()
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = integrationState == VisionIntegration.IntegrationState.READY ||
                                    integrationState == VisionIntegration.IntegrationState.LISTENING,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (integrationState == VisionIntegration.IntegrationState.LISTENING)
                                    Color(0xFFF44336) else Color(0xFF4CAF50)
                            )
                        ) {
                            Text(
                                text = if (integrationState == VisionIntegration.IntegrationState.LISTENING)
                                    "Stop Session" else "Start Session",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Send Query Button
                        Button(
                            onClick = {
                                if (::visionIntegration.isInitialized && queryText.isNotEmpty()) {
                                    visionIntegration.sendQuery(queryText)
                                    queryText = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = (integrationState == VisionIntegration.IntegrationState.READY ||
                                    integrationState == VisionIntegration.IntegrationState.LISTENING) &&
                                    queryText.isNotEmpty() && !isProcessing
                        ) {
                            Text(
                                text = "Send Query",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Voice Command Button
                    Button(
                        onClick = {
                            if (::visionIntegration.isInitialized) {
                                visionIntegration.sendVoiceCommand()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = integrationState == VisionIntegration.IntegrationState.LISTENING &&
                                !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0)
                        )
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            contentDescription = "Voice Command",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send Voice Command",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun Icon(
        modifier: Modifier = Modifier,
        contentDescription: String?,
        tint: Color = Color.Unspecified
    ) {
        // Placeholder for microphone icon
        Box(
            modifier = modifier
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎤",
                fontSize = 20.sp
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::visionIntegration.isInitialized) {
            visionIntegration.release()
        }
        if (::camera2Manager.isInitialized) {
            camera2Manager.stopCamera()
        }
        if (::voiceManager.isInitialized) {
            voiceManager.stopListening()
        }
    }
}
package com.example.XRTEST.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX Preview Composable for AR Glass Q&A System
 * 
 * 실시간 카메라 피드를 화면에 표시하는 컴포저블
 * CameraX를 사용하여 카메라 프리뷰를 렌더링
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // CameraX 실행자
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // PreviewView를 통해 카메라 프리뷰 표시
    val previewView = remember { PreviewView(context) }
    
    // AndroidView를 사용하여 PreviewView를 Compose에 통합
    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    ) { view ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Preview use case 생성
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
                
                // 이전 바인딩 해제
                cameraProvider.unbindAll()
                
                // 카메라 바인딩
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
                
                Log.d("CameraPreview", "Camera preview started successfully")
                
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error starting camera preview", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    // 리소스 정리
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            Log.d("CameraPreview", "Camera preview disposed")
        }
    }
}

/**
 * Camera2 API를 사용한 대체 프리뷰 (Camera2Manager와 연동)
 * 
 * 기존 Camera2Manager와 통합하여 카메라 프리뷰 표시
 * TextureView를 사용하여 더 세밀한 제어 가능
 */
@Composable
fun Camera2Preview(
    modifier: Modifier = Modifier,
    onSurfaceReady: (android.view.Surface) -> Unit = {}
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val previewSurface = android.view.Surface(surface)
                        onSurfaceReady(previewSurface)
                        Log.d("Camera2Preview", "Surface ready: ${width}x${height}")
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        Log.d("Camera2Preview", "Surface size changed: ${width}x${height}")
                    }

                    override fun onSurfaceTextureDestroyed(
                        surface: android.graphics.SurfaceTexture
                    ): Boolean {
                        Log.d("Camera2Preview", "Surface destroyed")
                        return true
                    }

                    override fun onSurfaceTextureUpdated(
                        surface: android.graphics.SurfaceTexture
                    ) {
                        // 프레임 업데이트됨 (너무 자주 호출되므로 로그 생략)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
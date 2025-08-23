package com.example.XRTEST.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceSettingsButton(
    onClick: () -> Unit,
    currentVoice: String = "alloy",
    isKoreanMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "background"
    )
    
    // 현재 선택된 음성의 이모지 가져오기
    val voiceEmoji = when(currentVoice) {
        "alloy" -> "🎯"
        "echo" -> "🎭"
        "fable" -> "🎩"
        "onyx" -> "🎸"
        "nova" -> "✨"
        "shimmer" -> "🌟"
        else -> "🎙️"
    }
    
    Card(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier
            .height(56.dp)
            .scale(scale),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 음성 이모지
            Text(
                text = voiceEmoji,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            // 텍스트
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "음성 설정",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 현재 설정 표시 (작은 텍스트)
                Text(
                    text = "${currentVoice.uppercase()} • ${if (isKoreanMode) "한국어" else "English"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            // 설정 아이콘
            Text(
                text = "⚙️",
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
    
    // 버튼 릴리즈 효과
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

@Composable
fun CompactVoiceSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "🎙️",
            fontSize = 20.sp
        )
    }
}
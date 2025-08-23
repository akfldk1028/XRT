package com.example.XRTEST.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.XRTEST.vision.VoiceSettingsManager
import com.example.XRTEST.vision.VisionIntegration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsDialog(
    onDismiss: () -> Unit,
    voiceSettingsManager: VoiceSettingsManager,
    visionIntegration: VisionIntegration
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 현재 설정 상태
    var selectedVoice by remember { mutableStateOf(voiceSettingsManager.getSavedVoice()) }
    var isKoreanMode by remember { mutableStateOf(voiceSettingsManager.isKoreanMode()) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // 음성 옵션 정의
    val voiceOptions = listOf(
        VoiceOption("alloy", "Alloy", "중성적", "균형잡힌 중성적인 음성", "🎯"),
        VoiceOption("echo", "Echo", "남성", "깊고 차분한 남성 음성", "🎭"),
        VoiceOption("fable", "Fable", "영국식", "영국 억양의 우아한 음성", "🎩"),
        VoiceOption("onyx", "Onyx", "저음", "깊고 풍부한 저음 음성", "🎸"),
        VoiceOption("nova", "Nova", "여성", "밝고 친근한 여성 음성", "✨"),
        VoiceOption("shimmer", "Shimmer", "활발한", "에너지 넘치는 활발한 음성", "🌟")
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 16.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "음성 설정",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AI 응답 음성을 선택하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 언어 선택 스위치
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "언어 모드",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = if (isKoreanMode) "한국어로 응답합니다" else "영어로 응답합니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        
                        // 커스텀 언어 토글 스위치
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "EN",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!isKoreanMode) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontWeight = if (!isKoreanMode) FontWeight.Bold else FontWeight.Normal
                            )
                            
                            Switch(
                                checked = isKoreanMode,
                                onCheckedChange = { isKoreanMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                            
                            Text(
                                text = "한",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isKoreanMode) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontWeight = if (isKoreanMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 음성 선택 그리드
                Text(
                    text = "음성 선택",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    voiceOptions.chunked(2).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowOptions.forEach { voice ->
                                VoiceCard(
                                    voice = voice,
                                    isSelected = selectedVoice == voice.id,
                                    onClick = { selectedVoice = voice.id },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 홀수 개일 때 빈 공간 채우기
                            if (rowOptions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 미리듣기 및 저장 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 미리듣기 버튼
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isPlaying = true
                                visionIntegration.setVoice(selectedVoice)
                                visionIntegration.setLanguageMode(isKoreanMode)
                                val testText = if (isKoreanMode) {
                                    "안녕하세요! ${voiceOptions.find { it.id == selectedVoice }?.nameKo ?: ""} 음성입니다."
                                } else {
                                    "Hello! This is the ${selectedVoice} voice."
                                }
                                visionIntegration.sendQuery(testText)
                                isPlaying = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isPlaying,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = ButtonDefaults.outlinedButtonBorder,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPlaying) "재생 중..." else "미리듣기",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    
                    // 저장 버튼
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // 설정 저장
                                voiceSettingsManager.saveVoice(selectedVoice)
                                voiceSettingsManager.saveLanguageMode(isKoreanMode)
                                
                                // VisionIntegration에 즉시 적용
                                visionIntegration.setVoice(selectedVoice)
                                visionIntegration.setLanguageMode(isKoreanMode)
                                
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "저장",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice: VoiceOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.8f,
        label = "alpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        label = "scale"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "background"
    )
    
    Card(
        modifier = modifier
            .height(110.dp)
            .scale(animatedScale)
            .alpha(animatedAlpha)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 이모지 아이콘
            Text(
                text = voice.emoji,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // 음성 이름
            Text(
                text = voice.nameKo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            
            // 음성 설명
            Text(
                text = voice.type,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

data class VoiceOption(
    val id: String,
    val nameKo: String,
    val type: String,
    val description: String,
    val emoji: String
)
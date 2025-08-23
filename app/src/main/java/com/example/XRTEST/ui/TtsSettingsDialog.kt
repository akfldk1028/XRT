package com.example.XRTEST.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.XRTEST.vision.TtsConfiguration

/**
 * TTS Settings Dialog for AR Glass Q&A System
 * Allows users to configure Text-to-Speech preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsDialog(
    ttsConfiguration: TtsConfiguration,
    onDismiss: () -> Unit
) {
    val ttsMode by ttsConfiguration.ttsMode.collectAsState()
    val speechRate by ttsConfiguration.speechRate.collectAsState()
    val useAndroidForKorean by ttsConfiguration.useAndroidForKorean.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "TTS 설정 (음성 출력)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TTS Mode Selection
                Text(
                    text = "TTS 모드",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = ttsMode == TtsConfiguration.TTS_MODE_AUTO,
                            onClick = { ttsConfiguration.setTtsMode(TtsConfiguration.TTS_MODE_AUTO) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("자동 선택 (추천)")
                            Text(
                                "한국어: Android TTS, 영어: OpenAI",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = ttsMode == TtsConfiguration.TTS_MODE_ANDROID,
                            onClick = { ttsConfiguration.setTtsMode(TtsConfiguration.TTS_MODE_ANDROID) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Android TTS")
                            Text(
                                "모든 언어에 Android TTS 사용",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = ttsMode == TtsConfiguration.TTS_MODE_OPENAI,
                            onClick = { ttsConfiguration.setTtsMode(TtsConfiguration.TTS_MODE_OPENAI) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("OpenAI 음성")
                            Text(
                                "모든 언어에 OpenAI 음성 사용",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Divider()
                
                // Speech Rate Control
                Text(
                    text = "음성 속도",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("느림", fontSize = 12.sp)
                    Slider(
                        value = speechRate,
                        onValueChange = { ttsConfiguration.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("빠름", fontSize = 12.sp)
                }
                
                Text(
                    text = "현재 속도: ${String.format("%.1fx", speechRate)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                // Recommendation Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("💡", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "한국어 음성 개선 팁",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• 한국어는 Android TTS가 더 자연스럽습니다\n" +
                                "• '자동 선택' 모드를 추천합니다\n" +
                                "• 음성 속도는 0.9x가 최적입니다",
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    ttsConfiguration.resetToDefaults()
                    onDismiss()
                }
            ) {
                Text("기본값 복원")
            }
        }
    )
}
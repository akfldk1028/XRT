package com.example.XRTEST.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import com.example.XRTEST.vision.VoiceSettingsManager
import com.example.XRTEST.vision.VisionIntegration
import com.example.XRTEST.vision.RealtimeVisionClient

// Note: VoiceSettingsDialog is now in VoiceSettingsDialog.kt
// This file contains supporting UI components

/**
 * Floating Action Button for Voice Settings
 */
@Composable
fun VoiceSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "🎙️",
            fontSize = 24.sp
        )
    }
}

/**
 * Compact Voice Settings Chip for displaying current settings
 */
@Composable
fun VoiceSettingsChip(
    voiceSettingsManager: VoiceSettingsManager,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVoice = voiceSettingsManager.getSavedVoice()
    val voiceInfo = voiceSettingsManager.getVoiceInfo(currentVoice)
    val language = if (voiceSettingsManager.isKoreanMode()) "KR" else "EN"
    
    AssistChip(
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${voiceInfo.emoji} $language",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        modifier = modifier
    )
}
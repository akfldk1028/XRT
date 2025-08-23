package com.example.XRTEST.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Text Input Field for AR Glass Q&A System
 * Fallback input method when microphone is not available
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputField(
    enabled: Boolean = true,
    onSendQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input field
            OutlinedTextField(
                value = textInput,
                onValueChange = { 
                    textInput = it
                    isTyping = it.isNotEmpty()
                },
                placeholder = { 
                    Text(
                        "🎤 마이크 없음 - 여기에 질문을 입력하세요",
                        color = Color.Gray
                    )
                },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            onSendQuery(textInput.trim())
                            textInput = ""
                            isTyping = false
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                )
            )
            
            // Send button
            FilledTonalButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendQuery(textInput.trim())
                        textInput = ""
                        isTyping = false
                    }
                },
                enabled = enabled && textInput.isNotBlank(),
                modifier = Modifier.height(56.dp)
            ) {
                Text("전송")
            }
        }
        
        // Helper text
        if (!isTyping) {
            Text(
                text = "💡 예시: \"이게 뭐야?\", \"이거 어떻게 써?\", \"자세히 설명해줘\"",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
package com.meshlit.feature.ghosty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Full-screen chat surface for Ghosty. When the user taps the
 * floating bubble, this screen takes over the foreground and
 * routes prompts through the existing `AgentSession` (wired in
 * by `:app` via the [onSend] callback — Ghosty itself doesn't
 * know about the inference path).
 */
@Composable
fun GhostyExpandedScreen(
    accent: Color,
    accentDim: Color,
    onClose: () -> Unit,
    onSend: (String) -> String = { "Echo: $it" },
) {
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ghosty",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                Text(
                    "Ask anything.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            messages.forEach { (role, content) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (role == "user") accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                ) {
                    Text(content, modifier = Modifier.padding(12.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") },
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add("user" to input)
                        val reply = onSend(input)
                        messages.add("assistant" to reply)
                        input = ""
                    }
                },
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = accent)
            }
        }
    }
}
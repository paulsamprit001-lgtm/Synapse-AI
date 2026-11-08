package com.synapseai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Paper = Color(0xFFF7F5F0)
private val Ink = Color(0xFF282722)
private val Muted = Color(0xFF77746C)
private val Accent = Color(0xFFB66B4D)
private val Card = Color(0xFFEDEAE3)

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    private val engine by lazy { LocalEngine() }
    private lateinit var store: ChatStore
    private var modelPath by mutableStateOf<String?>(null)
    private var modelStatus by mutableStateOf("No model loaded")
    private var busy by mutableStateOf(false)
    private var history by mutableStateOf(listOf<ChatMessage>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)
        history = store.load()
        if (history.isEmpty()) history = listOf(
            ChatMessage("Hello. I'm Synapse AI. Import a compatible GGUF model to start private, offline chat.", false)
        )
        setContent {
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> if (uri != null) importModel(uri) }

            SynapseScreen(
                messages = history,
                modelStatus = modelStatus,
                busy = busy,
                onChooseModel = { picker.launch(arrayOf("*/*")) },
                onClear = {
                    history = emptyList()
                    store.clear()
                },
                onSend = { text -> send(text) }
            )
        }
    }

    private fun importModel(uri: Uri) {
        modelStatus = "Importing model…"
        lifecycleScope.launch {
            try {
                val destination = withContext(Dispatchers.IO) {
                    val out = File(filesDir, "synapse-model.gguf")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Cannot open selected file" }
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    out
                }
                modelPath = destination.absolutePath
                modelStatus = "Model copied • loading…"
                val loaded = withContext(Dispatchers.Default) {
                    engine.loadModel(destination.absolutePath, 768, 2)
                }
                modelStatus = if (loaded) "Model ready • offline" else "Model load failed • check GGUF compatibility/RAM"
            } catch (e: UnsatisfiedLinkError) {
                modelStatus = "Native engine unavailable • build with NDK/CMake"
            } catch (e: Exception) {
                modelStatus = "Import/load error: ${e.message ?: "unknown error"}"
            }
        }
    }

    private fun send(text: String) {
        if (busy) return
        history = history + ChatMessage(text, true)
        store.save(history)
        if (modelPath == null || !runCatching { engine.isLoaded() }.getOrDefault(false)) {
            history = history + ChatMessage("Load a compatible GGUF model first. No cloud service is used.", false)
            store.save(history)
            return
        }
        busy = true
        lifecycleScope.launch {
            val answer = withContext(Dispatchers.Default) {
                try {
                    val recent = history.takeLast(10).joinToString("\n") {
                        if (it.fromUser) "User: ${it.text}" else "Assistant: ${it.text}"
                    }
                    engine.generate(
                        "You are Synapse AI, a helpful assistant. Be clear and concise.\n$recent\nAssistant:",
                        180
                    )
                } catch (e: Exception) {
                    "Generation failed: ${e.message ?: "unknown error"}"
                }
            }
            history = history + ChatMessage(answer.ifBlank { "The model returned an empty response." }, false)
            store.save(history)
            busy = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { engine.unloadModel() }
    }
}

@Composable
private fun SynapseScreen(
    messages: List<ChatMessage>,
    modelStatus: String,
    busy: Boolean,
    onChooseModel: () -> Unit,
    onClear: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = lightColorScheme(
        background = Paper, surface = Paper, primary = Accent, onBackground = Ink, onSurface = Ink
    )) {
        Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Synapse", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("A private, on-device assistant", fontSize = 12.sp, color = Muted)
                }
                TextButton(onClick = onClear) { Text("Clear") }
                Box(Modifier.size(36.dp).background(Color(0xFFE9D8CD), CircleShape), contentAlignment = Alignment.Center) {
                    Text("S", color = Accent, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
            }
            HorizontalDivider(color = Color(0xFFE5E1D9))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LOCAL MODEL", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(modelStatus, fontSize = 11.sp, color = Muted)
                }
                OutlinedButton(onClick = onChooseModel, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Import GGUF", fontSize = 12.sp)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(11.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max = 310.dp)
                                .background(if (msg.fromUser) Color(0xFFECE3D9) else Card, RoundedCornerShape(18.dp))
                                .padding(horizontal = 15.dp, vertical = 12.dp)
                        ) {
                            Text(if (msg.fromUser) "You" else "Synapse", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
                        }
                    }
                }
                if (busy) item { Text("Synapse is thinking…", color = Muted, fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Synapse…", color = Muted) },
                    shape = RoundedCornerShape(22.dp), maxLines = 4, enabled = !busy
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { val msg = input.trim(); if (msg.isNotEmpty()) { onSend(msg); input = "" } },
                    enabled = !busy && input.isNotBlank(), shape = CircleShape,
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(52.dp)
                ) { Text("↑", fontSize = 22.sp) }
            }
        }
    }
}

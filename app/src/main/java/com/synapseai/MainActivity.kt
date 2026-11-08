package com.synapseai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val LightPaper = Color(0xFFF7F5F0)
private val LightInk = Color(0xFF282722)
private val LightMuted = Color(0xFF77746C)
private val LightAccent = Color(0xFF9A6048)
private val LightCard = Color(0xFFEDEAE3)
private val DarkPaper = Color(0xFF151515)
private val DarkInk = Color(0xFFF1EFEA)
private val DarkMuted = Color(0xFFAAA69E)
private val DarkAccent = Color(0xFFC58A70)
private val DarkCard = Color(0xFF252522)

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    private val engine by lazy { LocalEngine() }
    private lateinit var store: ChatStore
    private var modelPath by mutableStateOf<String?>(null)
    private var modelStatus by mutableStateOf("No model loaded")
    private var busy by mutableStateOf(false)
    private var history by mutableStateOf(listOf<ChatMessage>())
    private var themeMode by mutableStateOf("system")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)
        history = store.load()
        themeMode = getSharedPreferences("synapse_settings", MODE_PRIVATE)
            .getString("theme_mode", "system") ?: "system"
        if (history.isEmpty()) history = listOf(
            ChatMessage("Hello. I'm Synapse AI. Import a compatible GGUF model to start private, offline chat.", false)
        )
        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) importModel(uri)
            }
            val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) runCatching { store.exportBackup(uri) }
                    .onSuccess { Toast.makeText(this, "Chat backup exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, "Backup failed: " + it.message, Toast.LENGTH_LONG).show() }
            }
            val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runCatching { store.importBackup(uri) }
                    .onSuccess { restored -> history = restored; Toast.makeText(this, "Chat backup restored", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, "Restore failed: " + it.message, Toast.LENGTH_LONG).show() }
            }
            SynapseApp(
                messages = history,
                modelStatus = modelStatus,
                busy = busy,
                themeMode = themeMode,
                onThemeChange = { mode ->
                    themeMode = mode
                    getSharedPreferences("synapse_settings", MODE_PRIVATE).edit()
                        .putString("theme_mode", mode).apply()
                },
                onChooseModel = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                onBackup = { backupPicker.launch("synapse-chat-backup.json") },
                onRestore = { restorePicker.launch(arrayOf("application/json", "text/*", "*/*")) },
                onClear = {
                    history = emptyList()
                    store.clear()
                },
                onSend = { send(it) }
            )
        }
        val savedModel = File(filesDir, "synapse-model.gguf")
        if (savedModel.isFile && savedModel.length() > 0L) {
            modelPath = savedModel.absolutePath
            modelStatus = "Saved model found • loading…"
            lifecycleScope.launch {
                val loaded = withContext(Dispatchers.Default) {
                    runCatching { engine.loadModel(savedModel.absolutePath, 512, 3) }.getOrDefault(false)
                }
                modelStatus = if (loaded) "Model ready • offline" else "Saved model found • reload or check RAM"
            }
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
                    engine.loadModel(destination.absolutePath, 512, 3)
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
                    // Short prompts reduce repeated prefill work on low-memory phones.
                    val recent = history.takeLast(4).joinToString("\n") {
                        val content = if (it.text.length > 700) it.text.takeLast(700) else it.text
                        if (it.fromUser) "User: $content" else "Assistant: $content"
                    }
                    engine.generate(
                        "You are Synapse AI. Answer the latest question directly and concisely.\n$recent\nAssistant:",
                        96
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
private fun SynapseApp(
    messages: List<ChatMessage>,
    modelStatus: String,
    busy: Boolean,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onChooseModel: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClear: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val paper = if (dark) DarkPaper else LightPaper
    val ink = if (dark) DarkInk else LightInk
    val muted = if (dark) DarkMuted else LightMuted
    val accent = if (dark) DarkAccent else LightAccent
    val card = if (dark) DarkCard else LightCard
    val scheme = if (dark) darkColorScheme(
        background = paper, surface = paper, primary = accent, onBackground = ink, onSurface = ink
    ) else lightColorScheme(
        background = paper, surface = paper, primary = accent, onBackground = ink, onSurface = ink
    )

    MaterialTheme(colorScheme = scheme) {
        if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                paper = paper, ink = ink, muted = muted, card = card,
                onBack = { showSettings = false }
            )
        } else {
            Column(Modifier.fillMaxSize().background(paper).padding(horizontal = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("S", color = accent, fontWeight = FontWeight.Bold, fontSize = 23.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Synapse AI", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        Text("A private, on-device assistant", fontSize = 12.sp, color = muted)
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("⋮", fontSize = 24.sp) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Backup chats") }, onClick = { menuExpanded = false; onBackup() }, enabled = !busy)
                            DropdownMenuItem(text = { Text("Restore chats") }, onClick = { menuExpanded = false; onRestore() }, enabled = !busy)
                            DropdownMenuItem(text = { Text("Clear chat") }, onClick = { menuExpanded = false; onClear() }, enabled = !busy)
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; showSettings = true })
                        }
                    }
                }
                HorizontalDivider(color = muted.copy(alpha = 0.22f))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LOCAL MODEL", fontSize = 10.sp, color = muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(modelStatus, fontSize = 11.sp, color = muted)
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
                    itemsIndexed(messages) { _, msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                            Column(
                                Modifier.widthIn(max = 310.dp)
                                    .background(if (msg.fromUser) (if (dark) Color(0xFF34302C) else Color(0xFFECE3D9)) else card, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 15.dp, vertical = 12.dp)
                            ) {
                                Text(if (msg.fromUser) "You" else "Synapse", fontSize = 10.sp, color = muted, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(msg.text, fontSize = 14.sp, lineHeight = 21.sp, color = ink)
                                if (!msg.fromUser) {
                                    val context = LocalContext.current
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Synapse response", msg.text))
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("Copy", fontSize = 11.sp, color = accent) }
                                }
                            }
                        }
                    }
                    if (busy) item { Text("Synapse is thinking…", color = muted, fontSize = 12.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Synapse…", color = muted) },
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
}

@Composable
private fun SettingsScreen(
    themeMode: String,
    onThemeChange: (String) -> Unit,
    paper: Color,
    ink: Color,
    muted: Color,
    card: Color,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(paper).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
        Spacer(Modifier.height(26.dp))
        Text("APPEARANCE", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Choose how Synapse AI looks", color = muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                listOf("light" to "Light", "dark" to "Dark", "system" to "System default").forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onThemeChange(value) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == value, onClick = { onThemeChange(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = ink, fontSize = 15.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("ABOUT", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Synapse AI", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Version 0.5.0 • Offline Android assistant", color = muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("Your chats and settings stay on this device.", color = muted, fontSize = 12.sp)
            }
        }
    }
}

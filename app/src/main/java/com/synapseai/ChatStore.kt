package com.synapseai

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Private local chat history with portable JSON backup/restore.
 */
class ChatStore(private val context: Context) {
    private val file = File(context.filesDir, "chat_history.json")

    @Synchronized
    fun load(): List<ChatMessage> {
        return try {
            if (!file.exists()) emptyList() else parse(JSONArray(file.readText()))
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(150).forEach { msg ->
            array.put(JSONObject().put("text", msg.text).put("user", msg.fromUser))
        }
        val temp = File(context.filesDir, "chat_history.json.tmp")
        temp.writeText(array.toString())
        if (file.exists()) file.delete()
        check(temp.renameTo(file)) { "Could not safely save chat history" }
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    @Synchronized
    fun exportBackup(uri: Uri) {
        val content = JSONObject()
            .put("format", "synapse-chat-backup")
            .put("version", 1)
            .put("messages", JSONArray().also { array ->
                load().forEach { msg ->
                    array.put(JSONObject().put("text", msg.text).put("user", msg.fromUser))
                }
            }).toString(2)
        context.contentResolver.openOutputStream(uri, "wt").use { stream ->
            requireNotNull(stream) { "Cannot write to selected location" }
            stream.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    @Synchronized
    fun importBackup(uri: Uri): List<ChatMessage> {
        val raw = context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Cannot read selected file" }
            stream.bufferedReader().use { it.readText() }
        }
        val root = JSONObject(raw)
        require(root.optString("format") == "synapse-chat-backup") { "This is not a Synapse backup" }
        val restored = parse(root.getJSONArray("messages")).takeLast(150)
        save(restored)
        return restored
    }

    private fun parse(array: JSONArray): List<ChatMessage> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ChatMessage(item.getString("text"), item.getBoolean("user"))
        }
}

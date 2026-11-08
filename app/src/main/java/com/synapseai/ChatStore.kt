package com.synapseai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small local JSON history store. It is private to this app and never synced.
 */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chat_history.json")

    @Synchronized
    fun load(): List<ChatMessage> {
        return try {
            if (!file.exists()) {
                emptyList()
            } else {
                val array = JSONArray(file.readText())
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    ChatMessage(item.getString("text"), item.getBoolean("user"))
                }
            }
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
        file.writeText(array.toString())
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }
}

package com.example.ebook

import android.content.Context
import org.json.JSONObject

/* -------- TOPIC DETECTION -------- */
fun detectDiagramTopic(text: String): String? {
    val t = text.lowercase()
    return when {
        "triangle" in t -> "triangle"
        "mitochondria" in t -> "mitochondria"
        "cell" in t -> "cell"
        else -> null
    }
}

/* -------- LOAD IMAGES -------- */
fun getDiagramImages(
    context: Context,
    topic: String
): List<String> {

    val json = context.assets
        .open("diagram_index.json")
        .bufferedReader()
        .use { it.readText() }

    val root = JSONObject(json)
    if (!root.has(topic)) return emptyList()

    val arr = root.getJSONArray(topic)
    return List(arr.length()) { arr.getString(it) }
}

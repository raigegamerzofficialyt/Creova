package com.raido.videoeditor.data

import org.json.JSONArray
import org.json.JSONObject

enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    PORTRAIT_9_16("9:16", 9, 16),
    LANDSCAPE_16_9("16:9", 16, 9),
    SQUARE_1_1("1:1", 1, 1),
    PORTRAIT_4_5("4:5", 4, 5);

    val ratio: Float get() = w.toFloat() / h.toFloat()
}

/** One video clip on the timeline. [path] is a private copy inside the app's storage. */
data class Clip(
    val id: String,
    val path: String,
    val name: String,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
) {
    val trimmedMs: Long get() = trimEndMs - trimStartMs

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("path", path)
        put("name", name)
        put("durationMs", durationMs)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
    }

    companion object {
        fun fromJson(o: JSONObject) = Clip(
            id = o.getString("id"),
            path = o.getString("path"),
            name = o.optString("name", "Clip"),
            durationMs = o.getLong("durationMs"),
            trimStartMs = o.getLong("trimStartMs"),
            trimEndMs = o.getLong("trimEndMs"),
        )
    }
}

data class Project(
    val id: String,
    val name: String,
    val aspect: AspectRatio,
    val createdAt: Long,
    val modifiedAt: Long,
    val clips: List<Clip> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("aspect", aspect.name)
        put("createdAt", createdAt)
        put("modifiedAt", modifiedAt)
        put("clips", JSONArray().also { arr -> clips.forEach { arr.put(it.toJson()) } })
    }.toString()

    companion object {
        fun fromJson(text: String): Project? = try {
            val o = JSONObject(text)
            val arr = o.optJSONArray("clips")
            val clips = if (arr == null) emptyList() else (0 until arr.length()).map { Clip.fromJson(arr.getJSONObject(it)) }
            Project(
                id = o.getString("id"),
                name = o.getString("name"),
                aspect = AspectRatio.valueOf(o.getString("aspect")),
                createdAt = o.getLong("createdAt"),
                modifiedAt = o.getLong("modifiedAt"),
                clips = clips,
            )
        } catch (e: Exception) {
            null
        }
    }
}

package com.raido.videoeditor.data

import org.json.JSONObject

enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    PORTRAIT_9_16("9:16", 9, 16),
    LANDSCAPE_16_9("16:9", 16, 9),
    SQUARE_1_1("1:1", 1, 1),
    PORTRAIT_4_5("4:5", 4, 5);

    val ratio: Float get() = w.toFloat() / h.toFloat()
}

data class Project(
    val id: String,
    val name: String,
    val aspect: AspectRatio,
    val createdAt: Long,
    val modifiedAt: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("aspect", aspect.name)
        put("createdAt", createdAt)
        put("modifiedAt", modifiedAt)
    }.toString()

    companion object {
        fun fromJson(text: String): Project? = try {
            val o = JSONObject(text)
            Project(
                id = o.getString("id"),
                name = o.getString("name"),
                aspect = AspectRatio.valueOf(o.getString("aspect")),
                createdAt = o.getLong("createdAt"),
                modifiedAt = o.getLong("modifiedAt"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

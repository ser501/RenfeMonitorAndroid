package com.ondalibre.app.data

import android.content.Context
import com.ondalibre.app.BuildConfig
import org.json.JSONArray

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("ondalibre", Context.MODE_PRIVATE)

    var clientId: String
        get() = sp.getString("client_id", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.JAMENDO_CLIENT_ID
        set(value) = sp.edit().putString("client_id", value.trim()).apply()

    fun favorites(): List<Track> {
        val raw = sp.getString("favorites", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Track.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(list: List<Track>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("favorites", arr.toString()).apply()
    }
}

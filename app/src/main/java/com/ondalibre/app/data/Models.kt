package com.ondalibre.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class Source { JAMENDO, LOCAL }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val album: String = "",
    val imageUrl: String = "",
    val audioUrl: String,
    val durationSec: Int = 0,
    val source: Source = Source.JAMENDO,
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val speed: String = "",
    val vocal: String = "",
) {
    /** Etiquetas que usa la radio para buscar canciones parecidas. */
    val radioTags: List<String> get() = (genres + moods).distinct()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist); put("artistId", artistId)
        put("album", album); put("imageUrl", imageUrl); put("audioUrl", audioUrl)
        put("durationSec", durationSec); put("source", source.name)
        put("genres", JSONArray(genres)); put("moods", JSONArray(moods))
        put("speed", speed); put("vocal", vocal)
    }

    companion object {
        fun fromJson(o: JSONObject) = Track(
            id = o.optString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            artistId = o.optString("artistId"),
            album = o.optString("album"),
            imageUrl = o.optString("imageUrl"),
            audioUrl = o.optString("audioUrl"),
            durationSec = o.optInt("durationSec"),
            source = runCatching { Source.valueOf(o.optString("source")) }.getOrDefault(Source.JAMENDO),
            genres = o.optJSONArray("genres").toStringList(),
            moods = o.optJSONArray("moods").toStringList(),
            speed = o.optString("speed"),
            vocal = o.optString("vocal"),
        )
    }
}

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}

/** Caché en memoria de las pistas conocidas, para que la radio sepa sus etiquetas. */
object TrackCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, Track>()
    fun put(tracks: List<Track>) = tracks.forEach { map[it.id] = it }
    fun get(id: String?): Track? = id?.let { map[it] }
}

package com.ondalibre.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JamendoException(message: String) : Exception(message)

/**
 * Cliente mínimo de la API pública de Jamendo (música con licencia libre, sin anuncios).
 * Documentación: https://developer.jamendo.com/v3.0
 * Todas las funciones son bloqueantes: llamar desde Dispatchers.IO.
 */
class JamendoApi(private val prefs: Prefs) {

    data class Page(val tracks: List<Track>, val fullCount: Int)

    fun search(query: String, limit: Int = 40): List<Track> =
        tracks(mapOf("search" to query, "boost" to "popularity_total"), limit).tracks

    fun popular(limit: Int = 40): List<Track> =
        tracks(mapOf("order" to "popularity_week"), limit).tracks

    fun byTags(tags: List<String>, limit: Int = 30, offset: Int = 0, speed: String? = null): Page {
        val params = mutableMapOf(
            "fuzzytags" to tags.joinToString("+") { enc(it) },   // '+' = combinar etiquetas
            "boost" to "popularity_month",
            "offset" to offset.toString(),
            "fullcount" to "true",
        )
        if (!speed.isNullOrBlank()) params["speed"] = speed
        return tracks(params, limit, alreadyEncoded = setOf("fuzzytags", "speed"))
    }

    fun byArtist(artistId: String, limit: Int = 20): List<Track> =
        tracks(mapOf("artist_id" to artistId, "order" to "popularity_total"), limit).tracks

    private fun tracks(
        params: Map<String, String>,
        limit: Int,
        alreadyEncoded: Set<String> = emptySet(),
    ): Page {
        val clientId = prefs.clientId
        if (clientId.isBlank()) throw JamendoException("Falta el Client ID de Jamendo. Ponlo en Ajustes.")

        val sb = StringBuilder("https://api.jamendo.com/v3.0/tracks/?format=json")
        sb.append("&client_id=").append(enc(clientId))
        sb.append("&limit=").append(limit)
        sb.append("&include=musicinfo&audioformat=mp32&imagesize=300")
        params.forEach { (k, v) ->
            sb.append('&').append(k).append('=').append(if (k in alreadyEncoded) v else enc(v))
        }

        val conn = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) throw JamendoException("Error de red (${conn.responseCode})")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val headers = root.optJSONObject("headers")
            if (headers?.optString("status") != "success") {
                val msg = headers?.optString("error_message").orEmpty()
                throw JamendoException(
                    if (msg.contains("client", true)) "Client ID de Jamendo no válido. Revísalo en Ajustes."
                    else "Jamendo respondió con un error: $msg"
                )
            }
            val results = root.optJSONArray("results")
            val list = mutableListOf<Track>()
            if (results != null) {
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    val audio = o.optString("audio")
                    if (audio.isBlank()) continue
                    val info = o.optJSONObject("musicinfo")
                    val tags = info?.optJSONObject("tags")
                    list += Track(
                        id = "j" + o.optString("id"),
                        title = o.optString("name"),
                        artist = o.optString("artist_name"),
                        artistId = o.optString("artist_id"),
                        album = o.optString("album_name"),
                        imageUrl = o.optString("image").ifBlank { o.optString("album_image") },
                        audioUrl = audio,
                        durationSec = o.optInt("duration"),
                        source = Source.JAMENDO,
                        genres = tags?.optJSONArray("genres").toStringList(),
                        moods = tags?.optJSONArray("vartags").toStringList(),
                        speed = info?.optString("speed").orEmpty(),
                        vocal = info?.optString("vocalinstrumental").orEmpty(),
                    )
                }
            }
            TrackCache.put(list)
            val full = headers?.optInt("results_fullcount", list.size) ?: list.size
            return Page(list, full)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

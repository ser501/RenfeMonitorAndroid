package es.ondalibre.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object MediaCatalog {
    private fun readUrl(url: String, accept: String = "*/*"): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "OndaLibre/1.0 (Android audio app)")
            setRequestProperty("Accept", accept)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("El servidor respondió HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun searchPodcasts(query: String): List<AudioItem> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Escribe el nombre de un pódcast." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(readUrl(
            "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&term=$encoded",
            "application/json"
        ))
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val feed = obj.optString("feedUrl")
                val title = obj.optString("collectionName")
                if (feed.isBlank() || title.isBlank()) continue
                add(
                    AudioItem(
                        title = title,
                        subtitle = obj.optString("artistName", "Pódcast"),
                        source = "Apple Podcasts · RSS",
                        imageUrl = obj.optString("artworkUrl600", obj.optString("artworkUrl100")),
                        feedUrl = feed,
                        description = "Toca para cargar los episodios del feed RSS."
                    )
                )
            }
        }
    }

    data class FeedResult(val title: String, val items: List<AudioItem>)

    suspend fun loadPodcastFeed(feedUrl: String): FeedResult = withContext(Dispatchers.IO) {
        require(feedUrl.startsWith("https://") || feedUrl.startsWith("http://")) {
            "La dirección del feed no es válida."
        }
        val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "OndaLibre/1.0 podcast reader")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("No se ha podido leer el feed (HTTP ${connection.responseCode}).")
            }
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            connection.inputStream.buffered().use { input ->
                parser.setInput(input, null)
                var event = parser.eventType
                var channelTitle = "Pódcast"
                var insideItem = false
                var currentTag = ""
                var itemTitle = ""
                var itemDescription = ""
                var enclosure = ""
                var published = ""
                val output = mutableListOf<AudioItem>()
                while (event != XmlPullParser.END_DOCUMENT && output.size < 50) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val name = (parser.name ?: "").lowercase(Locale.ROOT)
                            currentTag = name
                            when (name) {
                                "item", "entry" -> {
                                    insideItem = true
                                    itemTitle = ""
                                    itemDescription = ""
                                    enclosure = ""
                                    published = ""
                                }
                                "enclosure" -> if (insideItem) {
                                    enclosure = parser.getAttributeValue(null, "url")
                                        ?: parser.getAttributeValue("", "url") ?: ""
                                }
                                "link" -> if (insideItem && enclosure.isBlank()) {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    val type = parser.getAttributeValue(null, "type")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank() && (rel == "enclosure" || type?.startsWith("audio/") == true)) {
                                        enclosure = href
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                            val text = parser.text?.trim().orEmpty()
                            if (text.isNotBlank()) {
                                // Current tag names are handled through a small parser state below.
                                when (currentTag) {
                                    "title" -> if (insideItem && itemTitle.isBlank()) itemTitle = text else if (!insideItem && channelTitle == "Pódcast") channelTitle = text
                                    "description", "summary", "encoded", "subtitle" -> if (insideItem && itemDescription.isBlank()) itemDescription = text
                                    "pubdate", "published", "updated" -> if (insideItem && published.isBlank()) published = text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val name = (parser.name ?: "").lowercase(Locale.ROOT)
                            if (name == "item" || name == "entry") {
                                if (enclosure.isNotBlank()) {
                                    output += AudioItem(
                                        title = itemTitle.ifBlank { "Episodio ${output.size + 1}" },
                                        subtitle = published,
                                        url = enclosure,
                                        source = channelTitle,
                                        downloadUrl = enclosure,
                                        canDownload = true,
                                        description = itemDescription.stripHtml().take(260)
                                    )
                                }
                                insideItem = false
                            }
                            currentTag = ""
                        }
                    }
                    event = parser.nextToken()
                }
                FeedResult(channelTitle, output)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun searchRadioStations(query: String): List<AudioItem> = withContext(Dispatchers.IO) {
        val params = if (query.isBlank()) {
            "country=Spain&limit=40&hidebroken=true&order=clickcount&reverse=true"
        } else {
            "name=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=40&hidebroken=true&order=clickcount&reverse=true"
        }
        val json = org.json.JSONArray(readUrl(
            "https://de1.api.radio-browser.info/json/stations/search?$params",
            "application/json"
        ))
        buildList {
            for (i in 0 until json.length()) {
                val obj = json.optJSONObject(i) ?: continue
                val stream = obj.optString("url_resolved").ifBlank { obj.optString("url") }
                val name = obj.optString("name").trim()
                if (stream.isBlank() || name.isBlank() || !stream.startsWith("http")) continue
                add(
                    AudioItem(
                        title = name,
                        subtitle = listOf(obj.optString("country"), obj.optString("codec"), obj.optString("bitrate").takeIf { it != "0" }?.plus(" kb/s"))
                            .filterNot { it.isNullOrBlank() }.joinToString(" · "),
                        url = stream,
                        source = "Radio Browser",
                        imageUrl = obj.optString("favicon"),
                        description = "Emisora en directo. La disponibilidad depende de la emisora."
                    )
                )
            }
        }
    }

    suspend fun searchJamendo(clientId: String, query: String): List<AudioItem> = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "Necesitas configurar tu Client ID gratuito de Jamendo." }
        require(query.isNotBlank()) { "Escribe un artista, canción o estilo." }
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${URLEncoder.encode(clientId.trim(), "UTF-8")}&format=json&limit=30&audioformat=mp31&search=$encodedQuery&include=musicinfo"
        val json = JSONObject(readUrl(url, "application/json"))
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val audio = obj.optString("audio")
                val name = obj.optString("name")
                if (audio.isBlank() || name.isBlank()) continue
                val downloadAllowed = obj.optBoolean("audiodownload_allowed", false)
                add(
                    AudioItem(
                        title = name,
                        subtitle = obj.optString("artist_name", "Artista independiente"),
                        url = audio,
                        source = "Jamendo",
                        imageUrl = obj.optString("image"),
                        downloadUrl = if (downloadAllowed) obj.optString("audiodownload") else "",
                        canDownload = downloadAllowed && obj.optString("audiodownload").startsWith("http"),
                        description = "Música independiente. Comprueba la licencia para el uso que quieras darle."
                    )
                )
            }
        }
    }

    private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim()
}

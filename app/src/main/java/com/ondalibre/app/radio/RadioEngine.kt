package com.ondalibre.app.radio

import com.ondalibre.app.data.JamendoApi
import com.ondalibre.app.data.Track
import kotlinx.coroutines.asCoroutineDispatcher
import kotlin.random.Random

/**
 * "Radio" estilo Spotify: a partir de una canción (o un género) va generando
 * canciones relacionadas por género, ambiente, ritmo y artista, sin repetir,
 * y deriva poco a poco según lo que va sonando.
 */
class RadioEngine(private val api: JamendoApi) {

    var title: String = ""
        private set

    private var seedTags: List<String> = emptyList()
    private var seedSpeed: String = ""
    private var seedArtistId: String = ""
    private val played = LinkedHashSet<String>()
    private val recentTracks = ArrayDeque<Track>()
    private val fullCounts = HashMap<String, Int>()

    fun startFromTrack(seed: Track) {
        reset()
        title = "Radio de «${seed.title}»"
        seedTags = seed.radioTags.ifEmpty { listOf("pop") }
        seedSpeed = seed.speed
        seedArtistId = seed.artistId
        played += seed.id
    }

    fun startFromGenre(label: String, tags: List<String>) {
        reset()
        title = "Radio $label"
        seedTags = tags
    }

    fun markPlayed(track: Track) {
        played += track.id
        recentTracks.addLast(track)
        while (recentTracks.size > 4) recentTracks.removeFirst()
    }

    fun isPlayed(id: String) = id in played

    /** Devuelve el siguiente lote de canciones relacionadas. Bloqueante (usar en IO). */
    fun next(count: Int = 15): List<Track> {
        val tags = pickTags()
        val speeds = neighbourSpeeds(seedSpeed)
        val candidates = mutableListOf<Track>()

        // 1) Canciones por etiquetas parecidas (con ritmo similar si lo conocemos)
        candidates += fetchByTags(tags, speeds)
        if (candidates.size < count) candidates += fetchByTags(tags, null)
        if (candidates.size < count && tags.size > 1) candidates += fetchByTags(tags.take(1), null)

        // 2) Algún tema más del artista de la semilla, como hace Spotify
        if (seedArtistId.isNotBlank() && Random.nextInt(3) != 0) {
            runCatching { api.byArtist(seedArtistId) }.getOrNull()
                ?.filter { it.id !in played }?.shuffled()?.take(2)
                ?.let { candidates += it }
        }

        // 3) Último recurso: lo más escuchado de la semana
        if (candidates.isEmpty()) candidates += runCatching { api.popular() }.getOrDefault(emptyList())

        // Filtrar repetidas y no saturar con el mismo artista
        val perArtist = HashMap<String, Int>()
        val seen = HashSet<String>()
        val result = candidates.shuffled().filter { t ->
            if (t.id in played || !seen.add(t.id)) return@filter false
            val n = perArtist.getOrDefault(t.artist, 0)
            if (n >= 2) false else { perArtist[t.artist] = n + 1; true }
        }.take(count)

        played += result.map { it.id }   // reservadas: no volverán a salir en esta radio
        return result
    }

    private fun fetchByTags(tags: List<String>, speed: String?): List<Track> {
        if (tags.isEmpty()) return emptyList()
        val key = tags.joinToString("+") + "|" + speed.orEmpty()
        val limit = 30
        return runCatching {
            val known = fullCounts[key]
            val offset = if (known == null) 0 else Random.nextInt(0, maxOf(1, minOf(known - limit, 400)))
            val page = api.byTags(tags, limit, offset, speed)
            fullCounts[key] = page.fullCount
            page.tracks
        }.getOrDefault(emptyList())
    }

    /** Mezcla las etiquetas de la semilla (peso doble) con las de lo último que ha sonado. */
    private fun pickTags(): List<String> {
        val score = HashMap<String, Int>()
        seedTags.forEach { score[it] = (score[it] ?: 0) + 2 }
        recentTracks.forEach { t -> t.radioTags.forEach { score[it] = (score[it] ?: 0) + 1 } }
        val ranked = score.entries.sortedByDescending { it.value }.map { it.key }
        if (ranked.size <= 3) return ranked
        // Las dos más fuertes + una al azar del resto para dar variedad
        return ranked.take(2) + ranked.drop(2).random()
    }

    private fun neighbourSpeeds(speed: String): String? {
        val order = listOf("verylow", "low", "medium", "high", "veryhigh")
        val i = order.indexOf(speed)
        if (i < 0) return null
        return order.subList(maxOf(0, i - 1), minOf(order.size, i + 2)).joinToString("+")
    }

    private fun reset() {
        played.clear(); recentTracks.clear(); fullCounts.clear()
        seedTags = emptyList(); seedSpeed = ""; seedArtistId = ""
    }
}

/** Estado de la radio compartido entre la pantalla y el servicio de reproducción. */
object RadioState {
    @Volatile var engine: RadioEngine? = null
    /** Un único hilo para todo lo de la radio: evita accesos simultáneos al motor. */
    val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor()
        .asCoroutineDispatcher()
    val active get() = engine != null
    fun stop() { engine = null }
}

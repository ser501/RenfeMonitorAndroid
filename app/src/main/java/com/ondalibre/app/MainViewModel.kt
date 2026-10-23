package com.ondalibre.app

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ondalibre.app.data.JamendoApi
import com.ondalibre.app.data.LocalMusic
import com.ondalibre.app.data.Prefs
import com.ondalibre.app.data.Source
import com.ondalibre.app.data.Track
import com.ondalibre.app.data.TrackCache
import com.ondalibre.app.player.toMediaItem
import com.ondalibre.app.radio.RadioEngine
import com.ondalibre.app.radio.RadioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NowPlaying(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val artwork: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffering: Boolean = false,
)

data class Genre(val label: String, val tags: List<String>)

val GENRES = listOf(
    Genre("Rock", listOf("rock")),
    Genre("Metal", listOf("metal")),
    Genre("Punk", listOf("punk")),
    Genre("Hip Hop", listOf("hiphop")),
    Genre("Electrónica", listOf("electronic")),
    Genre("Pop", listOf("pop")),
    Genre("Indie", listOf("indie")),
    Genre("Acústica", listOf("acoustic")),
    Genre("Chill / Lounge", listOf("lounge", "chillout")),
    Genre("Ambient", listOf("ambient")),
    Genre("Jazz", listOf("jazz")),
    Genre("Clásica", listOf("classical")),
    Genre("Banda sonora", listOf("soundtrack")),
    Genre("Reggae", listOf("reggae")),
    Genre("Latina", listOf("latin")),
    Genre("Épica", listOf("epic")),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val api = JamendoApi(prefs)

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>>(emptyList()); private set
    var popular by mutableStateOf<List<Track>>(emptyList()); private set
    var favorites by mutableStateOf(prefs.favorites()); private set
    var localTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var nowPlaying by mutableStateOf(NowPlaying()); private set
    var upNext by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var radioTitle by mutableStateOf<String?>(null); private set
    var radioLoading by mutableStateOf(false); private set
    var clientId by mutableStateOf(prefs.clientId); private set

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var searchJob: Job? = null

    init {
        TrackCache.put(favorites)
        connect()
        loadPopular()
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    nowPlaying = nowPlaying.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.takeIf { it > 0 } ?: 0,
                    )
                }
                delay(500)
            }
        }
    }

    // ---------- Conexión con el servicio ----------

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refreshFromPlayer()
            })
            refreshFromPlayer()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun refreshFromPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val md = item?.mediaMetadata
        nowPlaying = nowPlaying.copy(
            id = item?.mediaId.orEmpty(),
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            artwork = md?.artworkUri?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
        )
        val start = c.currentMediaItemIndex + 1
        val end = minOf(c.mediaItemCount, start + 25)
        upNext = if (start < end) (start until end).map { c.getMediaItemAt(it) } else emptyList()
        radioTitle = RadioState.engine?.title
    }

    // ---------- Catálogo ----------

    private fun <T> io(block: () -> T, onOk: (T) -> Unit) {
        viewModelScope.launch {
            loading = true
            val r = withContext(Dispatchers.IO) { runCatching(block) }
            loading = false
            r.onSuccess(onOk).onFailure { message = it.message ?: "Error de conexión" }
        }
    }

    fun loadPopular() {
        if (prefs.clientId.isBlank()) return
        io({ api.popular() }) { popular = it }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            loading = true
            val r = withContext(Dispatchers.IO) { runCatching { api.search(q) } }
            loading = false
            r.onSuccess {
                results = it
                if (it.isEmpty()) message = "No hay resultados para «$q»"
            }.onFailure { message = it.message }
        }
    }

    fun loadLocal() {
        io({ LocalMusic.load(getApplication()) }) {
            localTracks = it
            if (it.isEmpty()) message = "No se ha encontrado música en el móvil"
        }
    }

    fun saveClientId(id: String) {
        prefs.clientId = id
        clientId = prefs.clientId
        message = "Client ID guardado"
        loadPopular()
    }

    // ---------- Reproducción ----------

    fun playList(list: List<Track>, index: Int) {
        val c = controller ?: return
        RadioState.stop()
        TrackCache.put(list)
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0)
        c.prepare()
        c.play()
        refreshFromPlayer()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun playFromQueue(offset: Int) {
        val c = controller ?: return
        c.seekTo(c.currentMediaItemIndex + 1 + offset, 0)
        c.play()
    }

    fun currentTrack(): Track? = TrackCache.get(nowPlaying.id)

    // ---------- Radio (canciones relacionadas) ----------

    fun startRadio(seed: Track) {
        if (seed.source != Source.JAMENDO) { message = "La radio funciona con canciones del catálogo"; return }
        val engine = RadioEngine(api).also { it.startFromTrack(seed) }
        RadioState.engine = engine
        radioTitle = engine.title
        radioLoading = true
        viewModelScope.launch {
            val batch = withContext(RadioState.dispatcher) { runCatching { engine.next() } }
            radioLoading = false
            val tracks = batch.getOrElse { message = it.message; return@launch }
            if (RadioState.engine !== engine) return@launch
            val c = controller ?: return@launch
            if (c.currentMediaItem?.mediaId == seed.id) {
                // Ya está sonando: mantenerla y sustituir lo que viene detrás.
                val from = c.currentMediaItemIndex + 1
                if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
                c.addMediaItems(tracks.map { it.toMediaItem() })
            } else {
                c.setMediaItems((listOf(seed) + tracks).map { it.toMediaItem() }, 0, 0)
                c.prepare()
                c.play()
            }
            refreshFromPlayer()
        }
    }

    fun startGenreRadio(genre: Genre) {
        val engine = RadioEngine(api).also { it.startFromGenre(genre.label, genre.tags) }
        RadioState.engine = engine
        radioTitle = engine.title
        radioLoading = true
        viewModelScope.launch {
            val batch = withContext(RadioState.dispatcher) { runCatching { engine.next() } }
            radioLoading = false
            val tracks = batch.getOrElse { message = it.message; return@launch }
            if (tracks.isEmpty()) { message = "No se han encontrado canciones de ${genre.label}"; return@launch }
            if (RadioState.engine !== engine) return@launch
            val c = controller ?: return@launch
            c.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
            c.prepare()
            c.play()
            refreshFromPlayer()
        }
    }

    fun stopRadio() {
        RadioState.stop()
        radioTitle = null
    }

    // ---------- Favoritos ----------

    fun isFavorite(id: String) = favorites.any { it.id == id }

    fun toggleFavorite(track: Track) {
        favorites = if (isFavorite(track.id)) favorites.filterNot { it.id == track.id }
        else listOf(track) + favorites
        prefs.saveFavorites(favorites)
        TrackCache.put(favorites)
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

package es.ondalibre.app

import android.Manifest
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Locale

private enum class AppScreen(val label: String, val symbol: String) {
    HOME("Inicio", "⌂"), MUSIC("Música", "♫"), PODCASTS("Pódcast", "◉"), RADIO("Radio", "〰"), LIBRARY("Mi biblioteca", "▤")
}

class MainActivity : ComponentActivity() {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var currentAudio by mutableStateOf<AudioItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 401)
        }
        setContent { OndaLibreApp(this) }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, AudioPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                mediaController = future.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val activeUrl = mediaItem?.localConfiguration?.uri?.toString()
                        currentAudio = queueItems.firstOrNull { it.url == activeUrl } ?: currentAudio
                    }
                })
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        super.onStop()
    }

    private var queueItems: List<AudioItem> = emptyList()

    fun playAudio(item: AudioItem) = playQueue(listOf(item), 0, repeatAll = false)

    fun playQueue(items: List<AudioItem>, startIndex: Int = 0, repeatAll: Boolean = true) {
        if (items.isEmpty() || startIndex !in items.indices) {
            toast("No hay canciones disponibles para reproducir.")
            return
        }
        if (items.any { it.url.isBlank() }) {
            toast("Una canción no tiene un enlace de audio válido.")
            return
        }
        val controller = mediaController
        if (controller == null) {
            toast("El reproductor aún se está iniciando. Vuelve a tocar reproducir.")
            return
        }
        queueItems = items
        val mediaItems = items.map { item ->
            val metadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.subtitle.ifBlank { item.source })
                .setArtworkUri(item.imageUrl.takeIf { it.startsWith("http") }?.let(Uri::parse))
                .build()
            MediaItem.Builder().setUri(Uri.parse(item.url)).setMediaMetadata(metadata).build()
        }
        currentAudio = items[startIndex]
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.repeatMode = if (repeatAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        controller.prepare()
        controller.play()
        isPlaying = true
    }

    fun togglePlayback() {
        val player = mediaController ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun saveJamendoId(value: String) {
        getSharedPreferences("onda_settings", Context.MODE_PRIVATE).edit().putString("jamendo_client_id", value.trim()).apply()
        toast("Client ID guardado en este dispositivo.")
    }

    fun jamendoId(): String = getSharedPreferences("onda_settings", Context.MODE_PRIVATE).getString("jamendo_client_id", "") ?: ""

    fun downloadItem(item: AudioItem) {
        if (!item.canDownload || item.downloadUrl.isBlank()) {
            toast("El proveedor no ofrece descarga para este contenido.")
            return
        }
        try {
            val uri = Uri.parse(item.downloadUrl)
            val nameFromUrl = uri.lastPathSegment.orEmpty().substringAfterLast('/')
            val suffix = nameFromUrl.substringAfterLast('.', "mp3").takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) } ?: "mp3"
            val safeName = item.title.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9áéíóúñ]+"), "-").trim('-').take(70).ifBlank { "audio" }
            val request = DownloadManager.Request(uri)
                .setTitle(item.title)
                .setDescription("Descargando en OndaLibre")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MUSIC, "$safeName.$suffix")
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            toast("Descarga iniciada. Revisa Mi biblioteca cuando termine.")
        } catch (e: Exception) {
            toast("No se pudo iniciar la descarga: ${e.message ?: "error desconocido"}")
        }
    }

    fun localDownloads(): List<AudioItem> {
        val folder = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return emptyList()
        return folder.listFiles()?.filter { it.isFile && it.length() > 0L }?.sortedByDescending { it.lastModified() }?.map { file ->
            AudioItem(
                title = file.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() },
                subtitle = "Guardado en este dispositivo",
                url = Uri.fromFile(file).toString(),
                source = "Mi biblioteca"
            )
        } ?: emptyList()
    }

    fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("No se ha podido abrir el enlace.") }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

@Composable
private fun OndaLibreApp(activity: MainActivity) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var violet by remember { mutableStateOf(false) }
    val accent = if (violet) Color(0xFFC1A6FF) else Color(0xFFB7F36B)
    val palette = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF1A2012),
        secondary = Color(0xFF77DCC6),
        background = Color(0xFF101116),
        surface = Color(0xFF1A1B22),
        surfaceVariant = Color(0xFF252731),
        onBackground = Color(0xFFF4F4F7),
        onSurface = Color(0xFFF4F4F7),
        onSurfaceVariant = Color(0xFFB6B8C5)
    )
    MaterialTheme(colorScheme = palette) {
        Scaffold(
            containerColor = palette.background,
            bottomBar = {
                Column {
                    activity.currentAudio?.let { item -> MiniPlayer(item, activity, accent) }
                    NavigationBar(containerColor = Color(0xFF17181E), tonalElevation = 0.dp) {
                        AppScreen.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = screen == destination,
                                onClick = { screen = destination },
                                icon = { Text(destination.symbol, fontSize = 19.sp, color = if (screen == destination) accent else Color.LightGray) },
                                label = { Text(destination.label, fontSize = 10.sp, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    AppScreen.HOME -> HomeScreen(activity, accent, onNavigate = { screen = it }, onToggleTheme = { violet = !violet })
                    AppScreen.MUSIC -> MusicScreen(activity, accent)
                    AppScreen.PODCASTS -> PodcastScreen(activity, accent)
                    AppScreen.RADIO -> RadioScreen(activity, accent)
                    AppScreen.LIBRARY -> LibraryScreen(activity, accent)
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun OndaLogo(accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(accent, Color(0xFF65D9C2)))),
            contentAlignment = Alignment.Center
        ) {
            Text("∿", color = Color(0xFF111318), fontSize = 30.sp, fontWeight = FontWeight.Black)
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("ONDA LIBRE", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
            Text("TU MÚSICA, TU UNIVERSO", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
        }
    }
}

@Composable
private fun HomeScreen(activity: MainActivity, accent: Color, onNavigate: (AppScreen) -> Unit, onToggleTheme: () -> Unit) {
    PageColumn {
        OndaLogo(accent)
        Spacer(Modifier.height(2.dp))
        Text("Dale al play.", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
        Text("Música independiente, emisoras y pódcast en un solo lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF23282A)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E4531), Color(0xFF20212A)))).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("DESCUBRIMIENTO", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Text("Encuentra tu próxima obsesión sonora.", fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                    Text("Busca catálogos de música libre, escucha radio en directo o descubre nuevos pódcast.", color = Color(0xFFD0D5D1), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = { onNavigate(AppScreen.MUSIC) }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF15180F))) {
                            Text("Explorar música", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { onNavigate(AppScreen.RADIO) }) { Text("Radio en directo") }
                    }
                }
            }
        }
        Text("TUS FUENTES", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickCard("♫", "Música libre", "Jamendo", Modifier.weight(1f)) { onNavigate(AppScreen.MUSIC) }
            QuickCard("◉", "Historias", "Pódcast", Modifier.weight(1f)) { onNavigate(AppScreen.PODCASTS) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onNavigate(AppScreen.RADIO) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(accent, Color(0xFF536B9A)))), contentAlignment = Alignment.Center) {
                    Text("〰", color = Color(0xFF111318), fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tu radio musical", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Crea una emisora por estilo y escucha una cola continua sin salir de OndaLibre.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("→", color = accent, fontSize = 23.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Personaliza el ambiente", fontWeight = FontWeight.SemiBold)
                Text("Cambia el color de acento de la app.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onToggleTheme) { Text("Cambiar color") }
        }
        Text("Las fuentes gratuitas dependen de la disponibilidad de sus proveedores y de sus licencias.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun QuickCard(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(icon, fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MusicScreen(activity: MainActivity, accent: Color) {
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf(activity.jamendoId()) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<AudioItem>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("La música de Jamendo se reproduce desde su fuente oficial. Necesitas un Client ID gratuito para consultar el catálogo.") }
    PageColumn {
        OndaLogo(accent)
        Text("Música libre", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Descubre artistas independientes y escucha sus temas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Jamendo Client ID") },
            placeholder = { Text("Pega aquí tu Client ID") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { activity.saveJamendoId(clientId) }, modifier = Modifier.weight(1f)) { Text("Guardar ID") }
            OutlinedButton(onClick = { activity.openUrl("https://developer.jamendo.com/") }) { Text("Obtener ID") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Artista, tema o estilo") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        Button(onClick = {
            scope.launch {
                loading = true; message = "Buscando en Jamendo…"
                try {
                    items = MediaCatalog.searchJamendo(clientId, query)
                    message = if (items.isEmpty()) "No se han encontrado temas. Prueba otra búsqueda." else "${items.size} resultados encontrados."
                } catch (e: Exception) { message = e.message ?: "No se ha podido consultar el catálogo." }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Buscar música") }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        items.forEach { item -> AudioRow(item, activity, accent, showDownload = item.canDownload) }
    }
}

@Composable
private fun PodcastScreen(activity: MainActivity, accent: Color) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var shows by remember { mutableStateOf(listOf<AudioItem>()) }
    var episodes by remember { mutableStateOf(listOf<AudioItem>()) }
    var selectedShow by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Busca un programa; OndaLibre consultará el catálogo público y leerá sus episodios desde el feed RSS.") }
    PageColumn {
        OndaLogo(accent)
        Text("Pódcast", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Programas, entrevistas, historias y episodios.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar pódcast") }, singleLine = true, shape = RoundedCornerShape(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    loading = true; selectedShow = ""; episodes = emptyList(); message = "Buscando programas…"
                    try { shows = MediaCatalog.searchPodcasts(query); message = if (shows.isEmpty()) "No hay resultados para esa búsqueda." else "Elige un programa para ver sus episodios." }
                    catch (e: Exception) { message = e.message ?: "Error al buscar pódcast." }
                    loading = false
                }
            }, modifier = Modifier.weight(1f)) { Text("Buscar programas") }
            if (selectedShow.isNotBlank()) OutlinedButton(onClick = { selectedShow = ""; episodes = emptyList() }) { Text("Volver") }
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selectedShow.isBlank()) {
            shows.forEach { show ->
                PodcastShowRow(show, accent) {
                    scope.launch {
                        loading = true; selectedShow = show.title; episodes = emptyList(); message = "Cargando episodios de ${show.title}…"
                        try {
                            val feed = MediaCatalog.loadPodcastFeed(show.feedUrl)
                            episodes = feed.items
                            message = if (episodes.isEmpty()) "El feed no contiene episodios con archivo de audio accesible." else "${episodes.size} episodios disponibles. La descarga depende de los permisos del proveedor."
                        } catch (e: Exception) { message = e.message ?: "No se ha podido abrir el feed RSS." }
                        loading = false
                    }
                }
            }
        } else {
            Text(selectedShow, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            episodes.forEach { episode -> AudioRow(episode, activity, accent, showDownload = episode.canDownload) }
        }
    }
}

@Composable
private fun RadioScreen(activity: MainActivity, accent: Color) {
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf(activity.jamendoId()) }
    var query by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf(listOf<AudioItem>()) }
    var liveStations by remember { mutableStateOf(listOf<AudioItem>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Crea una radio musical por estilo. La música se reproduce aquí, con la cola en bucle.") }
    var liveQuery by remember { mutableStateOf("") }
    val presets = listOf("Rock", "Metal", "Pop", "Electrónica", "Hip hop", "Chill", "Lo-fi", "Jazz")
    PageColumn {
        OndaLogo(accent)
        Text("Radio", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Como una radio personalizada: elige un estilo y OndaLibre prepara una sesión continua de música.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CREA TU RADIO", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Jamendo Client ID") },
                    placeholder = { Text("Guarda tu ID gratuito una vez") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { activity.saveJamendoId(clientId) }, modifier = Modifier.weight(1f)) { Text("Guardar ID") }
                    OutlinedButton(onClick = { activity.openUrl("https://developer.jamendo.com/") }) { Text("Obtener ID") }
                }
                Text("Estilos", fontWeight = FontWeight.SemiBold)
                presets.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { genre ->
                            OutlinedButton(
                                onClick = {
                                    query = genre
                                    scope.launch {
                                        loading = true
                                        message = "Preparando tu radio de $genre…"
                                        try {
                                            val found = MediaCatalog.searchJamendo(clientId, genre)
                                            stations = found
                                            if (found.isEmpty()) message = "No hay temas para $genre. Prueba otro estilo."
                                            else {
                                                val shuffled = found.shuffled()
                                                activity.playQueue(shuffled, 0, repeatAll = true)
                                                message = "Radio $genre activa: ${shuffled.size} temas en reproducción continua."
                                            }
                                        } catch (e: Exception) {
                                            message = e.message ?: "No se ha podido crear la radio."
                                        }
                                        loading = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(genre) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("O crea una radio a partir de un artista o tema") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            message = "Creando tu radio…"
                            try {
                                val found = MediaCatalog.searchJamendo(clientId, query)
                                stations = found
                                if (found.isEmpty()) message = "No se han encontrado temas para esa búsqueda."
                                else {
                                    val shuffled = found.shuffled()
                                    activity.playQueue(shuffled, 0, repeatAll = true)
                                    message = "Tu radio está en marcha: ${shuffled.size} temas en reproducción continua."
                                }
                            } catch (e: Exception) {
                                message = e.message ?: "No se ha podido crear la radio."
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("▶ Crear y escuchar radio") }
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        if (stations.isNotEmpty()) {
            Text("EN TU RADIO", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            stations.forEachIndexed { index, item ->
                AudioRow(item, activity, accent, showDownload = item.canDownload, onPlay = {
                    if (activity.currentAudio?.url == item.url) activity.togglePlayback()
                    else activity.playQueue(stations, index, repeatAll = true)
                })
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text("Emisoras en directo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("También puedes escuchar radios FM/online reales desde el reproductor integrado.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        OutlinedTextField(value = liveQuery, onValueChange = { liveQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre de emisora") }, singleLine = true, shape = RoundedCornerShape(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    loading = true
                    try {
                        liveStations = MediaCatalog.searchRadioStations(liveQuery)
                        message = if (liveStations.isEmpty()) "No se han encontrado emisoras." else "${liveStations.size} emisoras disponibles."
                    } catch (e: Exception) { message = e.message ?: "No se ha podido buscar emisoras." }
                    loading = false
                }
            }, modifier = Modifier.weight(1f)) { Text("Buscar emisoras") }
            OutlinedButton(onClick = {
                liveQuery = ""
                scope.launch {
                    loading = true
                    try {
                        liveStations = MediaCatalog.searchRadioStations("")
                        message = "Emisoras populares de España."
                    } catch (e: Exception) { message = e.message ?: "Error de radio." }
                    loading = false
                }
            }) { Text("España") }
        }
        liveStations.forEach { AudioRow(it, activity, accent, showDownload = false) }
        Text("La radio musical usa pistas con streaming autorizado del catálogo Jamendo; las emisoras en directo dependen de sus propias señales.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryScreen(activity: MainActivity, accent: Color) {
    var downloads by remember { mutableStateOf(activity.localDownloads()) }
    PageColumn {
        OndaLogo(accent)
        Text("Mi biblioteca", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Episodios y pistas guardados localmente en este dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { downloads = activity.localDownloads() }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar descargas") }
        if (downloads.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⇩", fontSize = 32.sp, color = accent)
                    Text("Todavía no hay descargas", fontWeight = FontWeight.Bold)
                    Text("Descarga un episodio desde Pódcast o una pista de Jamendo que permita descargas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            Text("${downloads.size} archivos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            downloads.forEach { AudioRow(it, activity, accent, showDownload = false) }
        }
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text("Las descargas se guardan en el espacio privado de la app. Al desinstalar OndaLibre, Android puede eliminarlas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun PodcastShowRow(item: AudioItem, accent: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(item, accent, Modifier.size(62.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Ver episodios  →", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AudioRow(item: AudioItem, activity: MainActivity, accent: Color, showDownload: Boolean, onPlay: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Cover(item, accent, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle.ifBlank { item.source }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.source.isNotBlank()) Text(item.source, color = accent, fontSize = 10.sp)
                }
                FilledTonalButton(onClick = { onPlay?.invoke() ?: if (activity.currentAudio?.url == item.url) activity.togglePlayback() else activity.playAudio(item) }, shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(if (activity.currentAudio?.url == item.url && activity.isPlaying) "Ⅱ" else "▶", fontSize = 15.sp)
                }
            }
            if (showDownload || item.description.isNotBlank()) {
                if (item.description.isNotBlank()) Text(item.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (showDownload) TextButton(onClick = { activity.downloadItem(item) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text("⇩ Descargar", color = accent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Cover(item: AudioItem, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), Color(0xFF394B55)))), contentAlignment = Alignment.Center) {
        if (item.imageUrl.startsWith("http")) {
            AsyncImage(model = item.imageUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(item.title.firstOrNull()?.uppercase() ?: "♪", color = Color(0xFF111318), fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
        }
    }
}

@Composable
private fun MiniPlayer(item: AudioItem, activity: MainActivity, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF292B33)).clickable { activity.togglePlayback() }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Cover(item, accent, Modifier.size(39.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle.ifBlank { item.source }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (activity.isPlaying) "Ⅱ" else "▶", color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { activity.togglePlayback() })
        Text("»", color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { activity.skipNext() })
    }
}

package com.ondalibre.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ondalibre.app.data.Source
import com.ondalibre.app.data.Track

private val Accent = Color(0xFFFF5A36)
private val Bg = Color(0xFF0E0E12)
private val Surface1 = Color(0xFF17171E)
private val Surface2 = Color(0xFF22222B)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFFFFB199),
    background = Bg,
    onBackground = Color(0xFFF2F2F5),
    surface = Surface1,
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFA6A6B3),
    surfaceContainer = Surface1,
)

enum class Tab(val label: String) { HOME("Inicio"), RADIO("Radio"), FAVS("Favoritos"), LOCAL("Mi música"), SETTINGS("Ajustes") }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = AppColors) { App(vm) } }
    }
}

@Composable
fun App(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(if (vm.clientId.isBlank()) Tab.SETTINGS else Tab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Scaffold(
            containerColor = Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    if (vm.nowPlaying.id.isNotEmpty()) MiniPlayer(vm) { showPlayer = true }
                    NavigationBar(containerColor = Surface1) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon(), contentDescription = t.label) },
                                label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm) { tab = Tab.SETTINGS }
                    Tab.RADIO -> RadioScreen(vm)
                    Tab.FAVS -> TrackListScreen(
                        title = "Favoritos", tracks = vm.favorites, vm = vm,
                        empty = "Pulsa ♥ en cualquier canción para guardarla aquí."
                    )
                    Tab.LOCAL -> LocalScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PlayerScreen(vm) { showPlayer = false }
        }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }
}

private fun Tab.icon() = when (this) {
    Tab.HOME -> Icons.Rounded.Home
    Tab.RADIO -> Icons.Rounded.Radio
    Tab.FAVS -> Icons.Rounded.Favorite
    Tab.LOCAL -> Icons.Rounded.LibraryMusic
    Tab.SETTINGS -> Icons.Rounded.Settings
}

// ------------------------------------------------------------------ Inicio / búsqueda

@Composable
fun HomeScreen(vm: MainViewModel, goSettings: () -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Header("Onda Libre", "Música libre, sin anuncios")
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar canciones, artistas, géneros…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = ""; vm.search() }) {
                    Icon(Icons.Rounded.Close, "Borrar")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(); focus.clearFocus() }),
        )
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Accent)

        if (vm.clientId.isBlank()) {
            SetupCard(goSettings)
            return@Column
        }
        val showing = if (vm.results.isNotEmpty()) vm.results else vm.popular
        val label = if (vm.results.isNotEmpty()) "Resultados" else "Lo más escuchado esta semana"
        TrackList(showing, vm, header = label)
    }
}

@Composable
fun SetupCard(goSettings: () -> Unit) {
    Card(
        Modifier.padding(16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface2)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Falta un paso", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Onda Libre usa el catálogo gratuito de Jamendo. Necesitas una clave (Client ID) gratis: " +
                    "se saca en 2 minutos y se pega en Ajustes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = goSettings) { Text("Ir a Ajustes") }
        }
    }
}

// ------------------------------------------------------------------ Radio

@Composable
fun RadioScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        Header("Radio", "Canciones relacionadas sin fin")
        vm.radioTitle?.let { title ->
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface2)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Radio, null, tint = Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sonando ahora", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = vm::stopRadio) { Text("Parar radio") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (vm.radioLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        Text(
            "Elige un género, o en cualquier canción pulsa ⋮ → «Iniciar radio».",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(GENRES) { g ->
                val hue = (g.label.hashCode() and 0xFFFFFF) % 360
                Box(
                    Modifier.height(86.dp).clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color.hsv(hue.toFloat(), 0.65f, 0.55f), Color.hsv(((hue + 40) % 360).toFloat(), 0.7f, 0.3f))
                            )
                        )
                        .clickable(enabled = vm.clientId.isNotBlank()) { vm.startGenreRadio(g) }
                        .padding(14.dp)
                ) {
                    Text(g.label, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    Icon(
                        Icons.Rounded.PlayCircle, null, tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Mi música (local)

@Composable
fun LocalScreen(vm: MainViewModel) {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.loadLocal() else vm.message = "Sin permiso no puedo leer tu música"
    }
    LaunchedEffect(Unit) { if (vm.localTracks.isEmpty()) launcher.launch(perm) }
    TrackListScreen("Mi música", vm.localTracks, vm, "Aquí aparecerán tus MP3 y demás canciones del móvil.")
}

// ------------------------------------------------------------------ Ajustes

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf(vm.clientId) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Ajustes", null, padH = 0)
        Text("Client ID de Jamendo", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "1. Abre devportal.jamendo.com y crea una cuenta gratis.\n" +
                "2. Entra en «My applications» y crea una aplicación (pon cualquier nombre y web).\n" +
                "3. Copia el «Client ID» y pégalo aquí.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://devportal.jamendo.com/")))
        }) { Text("Abrir devportal.jamendo.com") }
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Client ID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.saveClientId(text) }, enabled = text.isNotBlank()) { Text("Guardar") }
        Spacer(Modifier.height(28.dp))
        Text(
            "Toda la música de la app viene de Jamendo, con licencias Creative Commons: es gratis, legal y sin anuncios. " +
                "Si te gusta un artista, apóyale en su página.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
    }
}

// ------------------------------------------------------------------ Listas de canciones

@Composable
fun Header(title: String, subtitle: String?, padH: Int = 16) {
    Column(Modifier.padding(start = padH.dp, end = padH.dp, top = 20.dp, bottom = 12.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
    }
}

@Composable
fun TrackListScreen(title: String, tracks: List<Track>, vm: MainViewModel, empty: String) {
    Column(Modifier.fillMaxSize()) {
        Header(title, if (tracks.isEmpty()) null else "${tracks.size} canciones")
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        if (tracks.isEmpty()) {
            Text(empty, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TrackList(tracks, vm, header = null)
        }
    }
}

@Composable
fun TrackList(tracks: List<Track>, vm: MainViewModel, header: String?) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        if (header != null) item {
            Text(
                header, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, playing = vm.nowPlaying.id == t.id, vm = vm) { vm.playList(tracks, i) }
        }
    }
}

@Composable
fun TrackRow(t: Track, playing: Boolean, vm: MainViewModel, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(t.imageUrl, 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) Accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                listOf(t.artist, t.genres.firstOrNull()).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opciones") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (t.source == Source.JAMENDO) DropdownMenuItem(
                    text = { Text("Iniciar radio") },
                    leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                    onClick = { menu = false; vm.startRadio(t) }
                )
                val fav = vm.isFavorite(t.id)
                DropdownMenuItem(
                    text = { Text(if (fav) "Quitar de favoritos" else "Añadir a favoritos") },
                    leadingIcon = { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                    onClick = { menu = false; vm.toggleFavorite(t) }
                )
            }
        }
    }
}

@Composable
fun Artwork(url: String, size: Int, corner: Int = 8) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(corner.dp)).background(Surface2),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) AsyncImage(
            model = url, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
    }
}

// ------------------------------------------------------------------ Reproductor

@Composable
fun MiniPlayer(vm: MainViewModel, open: () -> Unit) {
    val np = vm.nowPlaying
    Column(Modifier.background(Surface2)) {
        val progress = if (np.durationMs > 0) np.positionMs.toFloat() / np.durationMs else 0f
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Accent, trackColor = Color.Transparent
        )
        Row(
            Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(np.artwork, 44, 6)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(np.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    vm.radioTitle ?: np.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp, color = if (vm.radioTitle != null) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir/Pausa")
            }
            IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
        }
    }
}

@Composable
fun PlayerScreen(vm: MainViewModel, close: () -> Unit) {
    val np = vm.nowPlaying
    val track = vm.currentTrack()
    var dragging by remember { mutableStateOf<Float?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF3A1A12), Bg, Bg))
        ).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", Modifier.size(32.dp)) }
                Text(
                    vm.radioTitle ?: "Reproduciendo", Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                if (vm.radioTitle != null) TextButton(onClick = vm::stopRadio) { Text("Parar radio") }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 32.dp)) {
                BoxWithConstraints { Artwork(np.artwork, maxWidth.value.toInt(), 12) }
            }
            Spacer(Modifier.height(24.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(np.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(np.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (track != null) IconButton(onClick = { vm.toggleFavorite(track) }) {
                    val fav = vm.isFavorite(track.id)
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito",
                        tint = if (fav) Accent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        item {
            val dur = np.durationMs.coerceAtLeast(1)
            Column(Modifier.padding(horizontal = 24.dp)) {
                Slider(
                    value = dragging ?: (np.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { vm.seekTo((it * dur).toLong()) }; dragging = null },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent)
                )
                Row {
                    Text(fmt(np.positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(fmt(np.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(40.dp)) }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable(onClick = vm::togglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    if (np.buffering) CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
                    else Icon(
                        if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir/Pausa",
                        tint = Color.Black, modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(40.dp)) }
            }
        }
        item {
            if (track != null && track.source == Source.JAMENDO && vm.radioTitle == null) {
                OutlinedButton(onClick = { vm.startRadio(track) }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Rounded.Radio, null); Spacer(Modifier.width(8.dp)); Text("Iniciar radio de esta canción")
                }
            }
            if (vm.radioLoading) LinearProgressIndicator(Modifier.padding(16.dp).fillMaxWidth(), color = Accent)
        }
        if (vm.upNext.isNotEmpty()) {
            item {
                Text(
                    "A continuación", Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                    fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
            }
            itemsIndexed(vm.upNext) { i, item ->
                val md = item.mediaMetadata
                Row(
                    Modifier.fillMaxWidth().clickable { vm.playFromQueue(i) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(md.artworkUri?.toString().orEmpty(), 40, 6)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(md.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            md.artist?.toString().orEmpty(), maxLines = 1, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

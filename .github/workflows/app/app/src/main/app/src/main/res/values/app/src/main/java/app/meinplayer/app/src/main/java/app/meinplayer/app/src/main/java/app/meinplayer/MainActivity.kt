package app.meinplayer

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private val vm: PlayerVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            MaterialTheme(colorScheme = dynamicDarkColorScheme(ctx)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        if (vm.selected != null && vm.player.isPlaying) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) vm.player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (vm.selected != null) vm.player.play()
    }
}

@Composable
private fun App(vm: PlayerVm) {
    if (!vm.ready) return
    if (vm.playlistUrl.isBlank()) {
        SetupScreen(vm.playlistUrl) { vm.savePlaylistUrl(it) }
        return
    }

    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp >= 600
    val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE

    var fullscreen by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Alle") }

    val groups = remember(vm.channels) { listOf("Alle", "★ Favoriten") + vm.channels.map { it.group }.distinct() }
    val visible = remember(vm.channels, vm.favorites, query, group) {
        vm.channels.filter {
            (group == "Alle" || (group == "★ Favoriten" && it.url in vm.favorites) || it.group == group) &&
                it.name.contains(query, ignoreCase = true)
        }
    }

    if (showSetup) {
        SetupScreen(vm.playlistUrl) { vm.savePlaylistUrl(it); showSetup = false }
        BackHandler { showSetup = false }
        return
    }

    val sel = vm.selected
    val playerFillsScreen = sel != null && ((wide && fullscreen) || (!wide && landscape))
    Immersive(playerFillsScreen)

    BackHandler(enabled = sel != null) {
        if (fullscreen) fullscreen = false else vm.stop()
    }

    val list: @Composable (Modifier) -> Unit = { mod ->
        ChannelList(vm, visible, groups, group, { group = it }, query, { query = it },
            onSettings = { showSetup = true }, modifier = mod)
    }
    val player: @Composable (Modifier) -> Unit = { mod ->
        PlayerBox(vm, visible, fullscreen, { fullscreen = !fullscreen }, mod)
    }

    when {
        wide -> Row(Modifier.fillMaxSize()) {
            if (!fullscreen) list(Modifier.width(360.dp).fillMaxHeight().safeDrawingPadding())
            if (sel != null) player(Modifier.weight(1f).fillMaxHeight())
            else Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Kanal wählen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        sel == null -> list(Modifier.fillMaxSize().safeDrawingPadding())
        landscape -> player(Modifier.fillMaxSize())
        else -> Column(Modifier.fillMaxSize()) {
            player(Modifier.fillMaxWidth().aspectRatio(16f / 9f).statusBarsPadding())
            list(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding())
        }
    }
}

@Composable
private fun Immersive(on: Boolean) {
    val view = LocalView.current
    LaunchedEffect(on) {
        val window = (view.context as ComponentActivity).window
        val c = WindowInsetsControllerCompat(window, view)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun PlayerBox(
    vm: PlayerVm,
    list: List<Channel>,
    fullscreen: Boolean,
    toggleFullscreen: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    player = vm.player
                    keepScreenOn = true
                }
            },
            update = { it.player = vm.player },
        )
        Row(
            Modifier.align(Alignment.TopEnd).padding(8.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0x66000000)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton({ vm.zap(-1, list) }) { Icon(Icons.Default.KeyboardArrowUp, "Vorheriger Kanal", tint = Color.White) }
            IconButton({ vm.zap(+1, list) }) { Icon(Icons.Default.KeyboardArrowDown, "Nächster Kanal", tint = Color.White) }
            TextButton(toggleFullscreen) { Text(if (fullscreen) "⤡" else "⛶", color = Color.White) }
        }
        vm.selected?.let {
            Text(
                it.name, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelList(
    vm: PlayerVm,
    visible: List<Channel>,
    groups: List<String>,
    group: String,
    onGroup: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, onQuery, singleLine = true, placeholder = { Text("Suchen") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f),
            )
            IconButton({ vm.reload() }) { Icon(Icons.Default.Refresh, "Neu laden") }
            IconButton(onSettings) { Icon(Icons.Default.Settings, "Playlist") }
        }
        LazyRow(contentPadding = PaddingValues(12.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { g ->
                FilterChip(selected = g == group, onClick = { onGroup(g) }, label = { Text(g) })
            }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.url }) { ch ->
                val isSel = ch.url == vm.selected?.url
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { vm.select(ch) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        ch.logo, null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ch.group, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton({ vm.toggleFavorite(ch) }) {
                        Icon(
                            if (ch.url in vm.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorit",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(initial: String, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Playlist einrichten", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("URL deiner eigenen M3U-Playlist (vom Anbieter oder lokal gehostet).",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(url, { url = it }, label = { Text("M3U-URL") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button({ onSave(url) }, enabled = url.isNotBlank()) { Text("Speichern & laden") }
    }
}

package app.meinplayer

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Context.store by preferencesDataStore("prefs")
private val KEY_URL = stringPreferencesKey("playlist_url")
private val KEY_FAVS = stringSetPreferencesKey("favorites")
private val KEY_LAST = stringPreferencesKey("last_channel")

class PlayerVm(app: Application) : AndroidViewModel(app) {

    val player: ExoPlayer = ExoPlayer.Builder(app).build().apply { playWhenReady = true }

    var playlistUrl by mutableStateOf("")
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var favorites by mutableStateOf<Set<String>>(emptySet())
    var selected by mutableStateOf<Channel?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val p = app.store.data.first()
            playlistUrl = p[KEY_URL].orEmpty()
            favorites = p[KEY_FAVS].orEmpty()
            ready = true
            if (playlistUrl.isNotBlank()) {
                load()
                val last = p[KEY_LAST]
                channels.firstOrNull { it.url == last }?.let { select(it, autoplay = false) }
            }
        }
    }

    fun savePlaylistUrl(url: String) {
        playlistUrl = url.trim()
        viewModelScope.launch {
            getApplication<Application>().store.edit { it[KEY_URL] = playlistUrl }
            load()
        }
    }

    suspend fun load() {
        if (playlistUrl.isBlank()) return
        loading = true
        error = null
        try {
            val text = withContext(Dispatchers.IO) {
                val c = URL(playlistUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.inputStream.bufferedReader().use { it.readText() }
            }
            channels = M3u.parse(text)
            if (channels.isEmpty()) error = "Keine Kanäle in der Playlist gefunden."
        } catch (e: Exception) {
            error = "Laden fehlgeschlagen: ${e.message}"
        } finally {
            loading = false
        }
    }

    fun reload() = viewModelScope.launch { load() }

    fun select(ch: Channel, autoplay: Boolean = true) {
        selected = ch
        player.setMediaItem(MediaItem.fromUri(ch.url))
        player.prepare()
        player.playWhenReady = autoplay
        viewModelScope.launch {
            getApplication<Application>().store.edit { it[KEY_LAST] = ch.url }
        }
    }

    fun stop() {
        player.stop()
        selected = null
    }

    fun zap(delta: Int, list: List<Channel>) {
        val cur = list.indexOfFirst { it.url == selected?.url }
        if (list.isEmpty()) return
        val next = if (cur < 0) 0 else (cur + delta).mod(list.size)
        select(list[next])
    }

    fun toggleFavorite(ch: Channel) {
        favorites = if (ch.url in favorites) favorites - ch.url else favorites + ch.url
        viewModelScope.launch {
            getApplication<Application>().store.edit { it[KEY_FAVS] = favorites }
        }
    }

    override fun onCleared() {
        player.release()
    }
}

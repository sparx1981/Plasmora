package app.plasmora.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** One lyric line. timeMs is -1 when the lyrics are not time-synced. */
class LyricLine(val timeMs: Long, val text: String, val isKey: Boolean)

class LyricsState(
    val key: String = "",
    val loading: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val synced: Boolean = false,
    val keyCount: Int = 0,
    val notFound: Boolean = false
)

/**
 * Lyrics from LRCLIB (free, no account). Only the song title, artist, album and length are sent.
 * Results are cached on the phone, including "not found", so each song is looked up once.
 */
object Lyrics {
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> get() = _state
    private var dir: File? = null

    fun init(ctx: Context) {
        if (dir == null) dir = File(ctx.applicationContext.filesDir, "lyrics").also { it.mkdirs() }
    }

    private fun keyOf(title: String, artist: String) = (title.trim() + "|" + artist.trim()).lowercase()
    private fun fileFor(key: String): File? {
        val d = dir ?: return null
        val h = MessageDigest.getInstance("MD5").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(d, "$h.json")
    }

    fun clearCache() { dir?.listFiles()?.forEach { it.delete() }; _state.value = LyricsState() }

    /** Loads lyrics for a song (cache first, then network). Safe to call repeatedly for the same song. */
    suspend fun load(title: String, artist: String, album: String, durationMs: Long) {
        if (title.isBlank()) { _state.value = LyricsState(); return }
        val key = keyOf(title, artist)
        if (_state.value.key == key && !_state.value.notFound) return
        _state.value = LyricsState(key = key, loading = true)
        val result = withContext(Dispatchers.IO) {
            val f = fileFor(key)
            val cached = f?.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            cached ?: fetch(title, artist, album, durationMs)?.also { j -> runCatching { f?.writeText(j.toString()) } }
        }
        if (_state.value.key != key) return
        _state.value = if (result == null) LyricsState(key = key, notFound = true) else build(key, result)
    }

    private fun fetch(title: String, artist: String, album: String, durationMs: Long): JSONObject? {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val dur = if (durationMs > 0) "&duration=${durationMs / 1000}" else ""
        val get = "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
            (if (album.isNotBlank()) "&album_name=${enc(album)}" else "") + dur
        val direct = http(get)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (direct != null && (direct.optString("syncedLyrics").isNotBlank() || direct.optString("plainLyrics").isNotBlank())) return direct
        // Looser search without the album / exact length
        val s = http("https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}")
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
        var best: JSONObject? = null
        for (i in 0 until s.length()) {
            val o = s.optJSONObject(i) ?: continue
            if (o.optString("syncedLyrics").isBlank() && o.optString("plainLyrics").isBlank()) continue
            val d = o.optDouble("duration", 0.0) * 1000
            val ok = durationMs <= 0 || d <= 0 || kotlin.math.abs(d - durationMs) < 8000
            if (ok && (best == null || o.optString("syncedLyrics").isNotBlank() && best.optString("syncedLyrics").isBlank())) best = o
        }
        return best
    }

    private fun http(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Plasmora/1.0 (music visualiser)")
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (_: Throwable) { null }

    // ------------------------------------------------------------------ parsing
    private val lrc = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private fun build(key: String, j: JSONObject): LyricsState {
        val synced = j.optString("syncedLyrics")
        val raw = ArrayList<Pair<Long, String>>()
        if (synced.isNotBlank()) {
            for (ln in synced.lines()) {
                val m = lrc.findAll(ln).toList()
                if (m.isEmpty()) continue
                val text = ln.substring(m.last().range.last + 1).trim()
                for (x in m) {
                    val ms = x.groupValues[1].toLong() * 60000 + x.groupValues[2].toLong() * 1000 +
                        (x.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L)
                    raw.add(ms to text)
                }
            }
            raw.sortBy { it.first }
        }
        val isSynced = raw.size >= 4
        if (!isSynced) {
            raw.clear()
            for (ln in j.optString("plainLyrics").lines()) { val t = ln.trim(); if (t.isNotEmpty()) raw.add(-1L to t) }
        }
        if (raw.isEmpty()) return LyricsState(key = key, notFound = true)

        // Hook / chorus lines = lines that repeat, ignoring case and punctuation
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9\\p{L} ]"), "").replace(Regex("\\s+"), " ").trim()
        val counts = HashMap<String, Int>()
        for ((_, t) in raw) { val n = norm(t); if (n.length >= 6) counts[n] = (counts[n] ?: 0) + 1 }
        val lines = raw.map { (ms, t) -> LyricLine(ms, t, t.isNotBlank() && (counts[norm(t)] ?: 0) >= 2) }
        return LyricsState(key = key, lines = lines.filter { it.text.isNotBlank() }, synced = isSynced, keyCount = lines.count { it.isKey })
    }
}

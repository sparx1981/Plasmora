package app.plasmora.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Which dancer suits the artist: the artist name is looked up once and the answer is cached on the phone. */
class GenreState(val key: String = "", val style: Int = -1)

/**
 * Genre lookup from MusicBrainz (free, no account). Only the ARTIST NAME is sent, never the song title, and only while the
 * Dancer progress bar is in use. Results (including "no match") are cached on the phone so each artist is asked about once.
 * Maps the artist's community tags to one of the eight dancers (see DancerPoses); -1 means unknown.
 */
object Genre {
    private val _state = MutableStateFlow(GenreState())
    val state: StateFlow<GenreState> get() = _state
    private var dir: File? = null
    private var lastRequest = 0L

    fun keyOf(artist: String) = primaryArtist(artist).lowercase()

    /** "A feat. B", "A, B" -> "A" (names like "AC/DC" or "Simon & Garfunkel" are kept whole) */
    fun primaryArtist(artist: String): String =
        artist.split(Regex("""\s+(feat\.?|ft\.?|featuring)\s+|\s*[,;]\s*""", RegexOption.IGNORE_CASE)).first().trim()

    private fun fileFor(ctx: Context, key: String): File {
        val d = dir ?: File(ctx.applicationContext.filesDir, "genre").also { it.mkdirs(); dir = it }
        val h = MessageDigest.getInstance("MD5").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(d, "$h.json")
    }

    fun clearCache(ctx: Context) { fileFor(ctx, "x").parentFile?.listFiles()?.forEach { it.delete() }; _state.value = GenreState() }

    /** Looks the artist up (cache first). Cancel-safe: if the track is skipped quickly nothing is sent. */
    suspend fun load(ctx: Context, artist: String) {
        val name = primaryArtist(artist)
        if (name.isBlank()) { _state.value = GenreState(); return }
        val key = keyOf(artist)
        if (_state.value.key == key) return
        val f = fileFor(ctx, key)
        val cached = withContext(Dispatchers.IO) { f.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()).getInt("style") }.getOrNull() } }
        if (cached != null) { _state.value = GenreState(key, cached); return }
        _state.value = GenreState(key, -1)
        delay(1500)                                   // a skipped track never reaches the network
        val style = withContext(Dispatchers.IO) {
            val wait = 1100 - (System.currentTimeMillis() - lastRequest)   // MusicBrainz asks for at most one request a second
            if (wait > 0) Thread.sleep(wait)
            lastRequest = System.currentTimeMillis()
            fetch(name)
        }
        if (style == null) return                     // network trouble: don't cache, try again next time
        runCatching { withContext(Dispatchers.IO) { f.writeText(JSONObject().put("style", style).toString()) } }
        _state.value = GenreState(key, style)
    }

    /** null = couldn't ask; otherwise the dancer index (or -1 for no match). */
    private fun fetch(artist: String): Int? {
        val q = URLEncoder.encode("artist:\"" + artist.replace("\"", " ") + "\"", "UTF-8")
        val body = try {
            val c = URL("https://musicbrainz.org/ws/2/artist/?query=$q&fmt=json&limit=1").openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Plasmora/1.0 (music visualiser)")
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else return null
        } catch (_: Throwable) { return null }
        val a = runCatching { JSONObject(body).optJSONArray("artists")?.optJSONObject(0) }.getOrNull() ?: return -1
        if (a.optInt("score", 0) < 85) return -1       // not a confident match for this name
        val tags = a.optJSONArray("tags") ?: return -1
        val list = ArrayList<Pair<String, Int>>()
        for (i in 0 until tags.length()) { val t = tags.optJSONObject(i) ?: continue; list.add(t.optString("name") to t.optInt("count", 0)) }
        return styleFor(list)
    }

    // keyword -> dancer index. Checked in order, first hit wins per tag.
    private val rules: List<Pair<Int, List<String>>> = listOf(
        3 to listOf("post-rock"),
        5 to listOf("metal", "punk", "grunge", "hard rock", "rock", "shoegaze", "emo", "hardcore", "indie rock"),
        1 to listOf("hip hop", "hip-hop", "rap", "trap", "grime", "drill", "r&b", "rnb", "breakbeat"),
        6 to listOf("latin", "salsa", "reggaeton", "reggae", "samba", "bossa", "bachata", "merengue", "afrobeat", "afro", "dancehall", "cumbia", "tango", "flamenco"),
        7 to listOf("country", "folk", "bluegrass", "americana", "celtic", "western", "singer-songwriter"),
        4 to listOf("disco", "funk", "soul", "motown", "jazz", "blues", "swing", "boogie", "gospel"),
        3 to listOf("classical", "ambient", "soundtrack", "orchestral", "new age", "opera", "piano", "chamber", "baroque", "score", "lullaby", "post-rock", "instrumental"),
        2 to listOf("electronic", "techno", "house", "trance", "edm", "dubstep", "synth", "electro", "drum and bass", "drum & bass", "idm", "industrial", "dance", "downtempo", "trip hop", "vaporwave"),
        0 to listOf("pop", "indie", "alternative", "singer")
    )

    fun styleFor(tags: List<Pair<String, Int>>): Int {
        val score = FloatArray(8)
        for ((raw, count) in tags) {
            if (count <= 0) continue
            val t = raw.lowercase()
            val hit = rules.firstOrNull { (_, kws) -> kws.any { t.contains(it) } } ?: continue
            score[hit.first] += count.toFloat()
        }
        var best = -1; var bv = 0f
        for (i in score.indices) if (score[i] > bv) { bv = score[i]; best = i }
        return best
    }
}

package app.plasmora.media

import android.content.Context
import android.content.SharedPreferences

/** Remembers which effect (and palette) the user pinned for a particular song. */
object SongMemory {
    class Entry(val preset: Int, val palette: Int)   // palette -1 = automatic

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) prefs = ctx.applicationContext.getSharedPreferences("plasmora_songs", Context.MODE_PRIVATE)
    }

    private fun key(title: String, artist: String) = (title.trim() + "|" + artist.trim()).lowercase()

    fun get(title: String, artist: String): Entry? {
        if (!::prefs.isInitialized || title.isBlank()) return null
        val v = prefs.getString(key(title, artist), null) ?: return null
        val p = v.split(",")
        val a = p.getOrNull(0)?.toIntOrNull() ?: return null
        return Entry(a, p.getOrNull(1)?.toIntOrNull() ?: -1)
    }

    fun put(title: String, artist: String, preset: Int, palette: Int) {
        if (!::prefs.isInitialized || title.isBlank()) return
        prefs.edit().putString(key(title, artist), "$preset,$palette").apply()
    }

    fun remove(title: String, artist: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(key(title, artist)).apply()
    }

    fun count(): Int = if (::prefs.isInitialized) prefs.all.size else 0
    fun clear() { if (::prefs.isInitialized) prefs.edit().clear().apply() }
}

package game
import java.util.prefs.Preferences

//ДЛЯ ЗАПАМ'ЯТОВУВАННЯ ПОЗИЦІЙ
object AppSettings {
    private val prefs = Preferences.userRoot().node("Flyerd") // назва гри
    private const val KEY_MUSIC_ENABLED = "musicEnabled"
    private const val KEY_MUSIC_INDEX = "currentMusicIndex"
    private const val KEY_SKIN_INDEX = "skinChanged"

    fun loadMusicEnabled(default: Boolean = true): Boolean =
        prefs.getBoolean(KEY_MUSIC_ENABLED, default)

    fun saveMusicEnabled(value: Boolean) {
        prefs.putBoolean(KEY_MUSIC_ENABLED, value)
        runCatching { prefs.flush() } // щоб точно записалося
    }
    fun loadMusicIndex(default: Int = 0): Int =
        prefs.getInt(KEY_MUSIC_INDEX, default)

    fun saveMusicIndex(value: Int) {
        prefs.putInt(KEY_MUSIC_INDEX, value)
        runCatching { prefs.flush() }
    }

    fun loadSkinIndex(default: Int = 0): Int =
        prefs.getInt(KEY_SKIN_INDEX, default)

    fun saveSkinIndex(value: Int) {
        prefs.putInt(KEY_SKIN_INDEX, value)
        runCatching { prefs.flush() }
    }

}


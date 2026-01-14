import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import game.AppSettings
import game.BackgroundMusic
import game.GameEngine
import kotlinx.coroutines.delay
import ui.GameScreen

fun main() = application {
    val engine = remember { GameEngine() }
    val windowState = rememberWindowState(width = 500.dp, height = 650.dp)

    val tracks = remember {
        listOf(
            BackgroundMusic("/music/testSong.wav"),
            BackgroundMusic("/music/smaragdoveNebo.wav"),
            BackgroundMusic("/music/vanya.wav"),
        )
    }

    var musicEnabled by remember { mutableStateOf(AppSettings.loadMusicEnabled(true)) }
    var musicIndex by remember {
        mutableStateOf(AppSettings.loadMusicIndex(0).coerceIn(0, tracks.lastIndex))
    }

    // ✅ зберігаємо налаштування
    LaunchedEffect(musicEnabled) { AppSettings.saveMusicEnabled(musicEnabled) }
    LaunchedEffect(musicIndex) { AppSettings.saveMusicIndex(musicIndex) }

    // --- SKINS ---
    val skinsCount = 2
    var skinIndex by remember {
        mutableStateOf(AppSettings.loadSkinIndex(0).coerceIn(0, skinsCount - 1))
    }
    LaunchedEffect(skinIndex) { AppSettings.saveSkinIndex(skinIndex) }

    var musicReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        musicReady = true
    }

    // ✅ керуємо програванням ТІЛЬКИ тут
    LaunchedEffect(musicReady, musicEnabled, musicIndex) {
        if (!musicReady) return@LaunchedEffect

        // стоп усіх
        tracks.forEach { it.pause() }

        // якщо вимкнено — нічого не грає
        if (!musicEnabled) return@LaunchedEffect

        // грає вибраний
        tracks[musicIndex].play()
    }

    Window(
        onCloseRequest = {
            tracks.forEach { it.pause() }
            exitApplication()
        },
        title = "Flyerd",
        resizable = false,
        state = windowState
    ) {
        GameScreen(
            engine = engine,

            // музика
            musicEnabled = musicEnabled,
            onMusicClick = { musicEnabled = !musicEnabled },
            onTreeDoubleClick = { musicIndex = (musicIndex + 1) % tracks.size },

            // скіни (поки просто “наступний”)
            skinIndex = skinIndex,
            onSkinClick = { skinIndex = (skinIndex + 1) % skinsCount }
        )
    }
}

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import game.BackgroundMusic
import game.GameEngine
import kotlinx.coroutines.delay
import ui.GameScreen

fun main() = application {
    val engine = remember { GameEngine() }
    val windowState = rememberWindowState(width = 500.dp, height = 650.dp)

    var musicEnabled by remember { mutableStateOf(true) }
    val music = remember { BackgroundMusic("/music/testSong.wav") }

    var musicReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        musicReady = true
    }

    LaunchedEffect(musicReady, musicEnabled) {
        if (!musicReady) return@LaunchedEffect
        if (musicEnabled) music.play() else music.pause()
    }

    Window(
        onCloseRequest = {
            music.pause()
            exitApplication()
        },
        title = "Flyerd",
        resizable = false,
        state = windowState
    ) {
        GameScreen(
            engine = engine,
            musicEnabled = musicEnabled,
            onMusicClick = { musicEnabled = !musicEnabled }
        )
    }
}

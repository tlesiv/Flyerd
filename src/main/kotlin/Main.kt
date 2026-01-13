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

    var currentMusicIndex by remember { mutableStateOf(0) }
    var musicEnabled by remember { mutableStateOf(true) }

    val music1 = remember { BackgroundMusic("/music/testSong.wav") }
    val music2 = remember { BackgroundMusic("/music/smaragdoveNebo.wav") }

    var musicReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        musicReady = true
    }

    // 3. Логіка перемикання: реагує на готовність, стан ввімкнення ТА зміну індексу треку
    LaunchedEffect(musicReady, musicEnabled, currentMusicIndex) {
        if (!musicReady) return@LaunchedEffect

        // Спочатку зупиняємо обидва треки, щоб вони не грали одночасно
        music1.pause()
        music2.pause()

        if (musicEnabled) {
            if (currentMusicIndex == 0) music1.play() else music2.play()
        }
    }

    Window(
        onCloseRequest = {
            music1.pause()
            music2.pause()
            exitApplication()
        },
        title = "Flyerd",
        resizable = false,
        state = windowState
    ) {
        GameScreen(
            engine = engine,
            musicEnabled = musicEnabled,
            onMusicClick = { musicEnabled = !musicEnabled },
            onTreeDoubleClick = {
                currentMusicIndex = if (currentMusicIndex == 0) 1 else 0
            }
        )
    }
}
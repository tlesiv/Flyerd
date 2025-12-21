import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import game.GameEngine
import ui.GameScreen

fun main() = application {
    val engine = GameEngine()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Flyerd",
        resizable = false,
        state = WindowState(width = 500.dp, height = 650.dp)
    ) {
        GameScreen(engine)
    }
}

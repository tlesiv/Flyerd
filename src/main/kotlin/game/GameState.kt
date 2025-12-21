package game

data class Player(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

data class GameState(
    val player: Player = Player(x = 300f, y = 80f),
    var cameraY: Float = 0f,
    var running: Boolean = false
)

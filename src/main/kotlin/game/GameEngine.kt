package game

import kotlin.math.max
import kotlin.math.min

class GameEngine(
    val state: GameState = GameState()
) {
    // Налаштування
    var gravity = -1800f
    var sideSpeed = 650f
    var worldUpSpeed = 260f

    // Розмір екрана (з UI оновлюємо)
    var screenW = 600f
    var screenH = 650f

    private var leftPressed = false
    private var rightPressed = false

    fun start() {
        state.running = true
        state.player.x = screenW / 2f
        state.player.y = 80f
        state.player.vx = 0f
        state.player.vy = 0f
        state.cameraY = 0f
        leftPressed = false
        rightPressed = false
    }

    fun stop() {
        state.running = false
    }

    fun onLeft(down: Boolean) {
        leftPressed = down
        recomputeVX()
    }

    fun onRight(down: Boolean) {
        rightPressed = down
        recomputeVX()
    }

    private fun recomputeVX() {
        state.player.vx = when {
            leftPressed && !rightPressed -> -sideSpeed
            rightPressed && !leftPressed -> sideSpeed
            else -> 0f
        }
    }

    fun tick(dt: Float) {
        if (!state.running) return

        val p = state.player

        p.vy += gravity * dt
        p.y += (p.vy + worldUpSpeed) * dt
        p.x += p.vx * dt

        // стінки по X
        p.x = min(max(p.x, 20f), screenW - 20f)

        // камера
        val desiredScreenY = screenH * 0.30f
        state.cameraY = p.y - desiredScreenY
        if (state.cameraY < 0f) state.cameraY = 0f

        // "земля"
        if (p.y < 20f) {
            p.y = 20f
            p.vy = 0f
        }
    }
}

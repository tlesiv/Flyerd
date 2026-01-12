package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import game.GameEngine
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================
// Pixel-perfect masks (alpha collision)
// =====================================================
@Stable
class AlphaMask(
    val width: Int,
    val height: Int,
    val data: BooleanArray
) {
    inline fun opaque(x: Int, y: Int): Boolean = data[y * width + x]
}

private fun buildAlphaMask(
    painter: Painter,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    alphaCutoff: Float
): AlphaMask {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)

    val img = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
    val canvas = Canvas(img)
    val drawScope = CanvasDrawScope()

    drawScope.draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = Size(w.toFloat(), h.toFloat())
    ) {
        drawRect(Color.Transparent, size = size, blendMode = BlendMode.Src)
        with(painter) { draw(size = size) }
    }

    val pm = img.toPixelMap()
    val data = BooleanArray(w * h)
    var idx = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            data[idx++] = pm[x, y].alpha > alphaCutoff
        }
    }
    return AlphaMask(w, h, data)
}

@Composable
private fun rememberAlphaMask(
    painter: Painter,
    widthPx: Int,
    heightPx: Int,
    alphaCutoff: Float
): AlphaMask {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(painter, widthPx, heightPx, density, layoutDirection, alphaCutoff) {
        buildAlphaMask(painter, widthPx, heightPx, density, layoutDirection, alphaCutoff)
    }
}

private fun pixelPerfectCollision(
    aX: Int, aY: Int, aMask: AlphaMask,
    bX: Int, bY: Int, bMask: AlphaMask
): Boolean {
    val left = max(aX, bX)
    val top = max(aY, bY)
    val right = min(aX + aMask.width, bX + bMask.width)
    val bottom = min(aY + aMask.height, bY + bMask.height)
    if (left >= right || top >= bottom) return false

    for (y in top until bottom) {
        val ay = y - aY
        val by = y - bY
        for (x in left until right) {
            val ax = x - aX
            val bx = x - bX
            if (aMask.opaque(ax, ay) && bMask.opaque(bx, by)) return true
        }
    }
    return false
}

// =====================================================
// Obstacles
// =====================================================
data class Obstacle(
    val id: Int,
    val xPx: Float,
    val worldYPx: Float,
    val kind: Int // index in branchSpecs
)

enum class XAnchor { LEFT, RIGHT, CENTER }

data class BranchSpawn(
    val anchor: XAnchor,
    val yFrac: Float,
    val kind: Int,
    val xOffsetPx: Float = 0f
)

// ✅ NEW: per-branch draw size (fixes squished SVGs while keeping FillBounds + masks)
data class BranchSpec(
    val painter: Painter,
    val wDp: Dp,
    val hDp: Dp
)

@Composable
fun GameScreen(engine: GameEngine, musicEnabled: Boolean, onMusicClick: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }
    var skinChanged by remember { mutableStateOf(false) }

    // --- Bird sprites ---
    val birdRight = painterResource("images/ptashka_right.svg")
    val birdLeft = painterResource("images/ptashka_left.svg")
    val birdFlyLeft = painterResource("images/ptashka_fly_left.svg")
    val birdFlyRight = painterResource("images/ptashka_fly_right.svg")

    //kkk spr
    val kkk = painterResource("images/derevco.svg")

    // --- Decor tree (SVG) ---
    val derevco = painterResource("images/derevco.svg")

    // --- Branches ---
    val branch1 = painterResource("images/branch1.svg")
    val branch2 = painterResource("images/branch2.svg")
    val branch3 = painterResource("images/branch3.svg")
    val branch4 = painterResource("images/branch4.svg")
    val branch5 = painterResource("images/branch5.svg")
    val branch6 = painterResource("images/branch6.svg")
    val branch7 = painterResource("images/branch7.svg")
    val branch8 = painterResource("images/branch8.svg")
    val branch9 = painterResource("images/branch9.svg")
    val branch10 = painterResource("images/branch10.svg")
    val branch11 = painterResource("images/branch11.svg")
    val branch12 = painterResource("images/branch12.svg")
    val branch13 = painterResource("images/branch13.svg")

    // --- Backgrounds
    val backgrounds = listOf(
        painterResource("images/game_background.svg"),
        painterResource("images/game_background.svg"),
        painterResource("images/game_background.svg"),
        painterResource("images/game_background5.svg"),
        painterResource("images/game_background5.svg"),
        painterResource("images/game_background5.svg"),
    )

    // --- Layouts ---
    val layoutLevel1 = listOf(
        BranchSpawn(XAnchor.RIGHT, 0.08f, kind = 0),
        BranchSpawn(XAnchor.LEFT,  0.30f, kind = 1),
        BranchSpawn(XAnchor.RIGHT, 0.55f, kind = 2),
        BranchSpawn(XAnchor.LEFT,  0.73f, kind = 3)
    )

    val layoutLevel2 = listOf(
        BranchSpawn(XAnchor.RIGHT, 0.29f, kind = 5),
        BranchSpawn(XAnchor.LEFT,  0.44f, kind = 4)
    )

    val layoutLevel3 = listOf(
        BranchSpawn(XAnchor.RIGHT, 0.22f, kind = 12),
        BranchSpawn(XAnchor.LEFT,  0.35f, kind = 11),
        BranchSpawn(XAnchor.LEFT,  0.68f, kind = 10)
    )

    val layoutLevel4 = listOf(
        BranchSpawn(XAnchor.RIGHT, 0.04f, kind = 8),
        BranchSpawn(XAnchor.LEFT,  0.20f, kind = 9),
        BranchSpawn(XAnchor.LEFT,  0.55f, kind = 7),
        BranchSpawn(XAnchor.RIGHT, 0.60f, kind = 6)
    )

    val layouts = listOf(layoutLevel1, layoutLevel2, layoutLevel3, layoutLevel4)

    fun layoutForLevel(level: Int): List<BranchSpawn> {
        if (level <= 0 || level >= 5   ) return emptyList()
        return layouts[(level - 1) % layouts.size]
    }

    val buttonColor = Color(0xFF586316)
    val baseBackgroundColor = Color(0xFF0B1020)

    // --- Screen (px) ---
    var screenH by remember { mutableStateOf(650f) }
    var screenW by remember { mutableStateOf(600f) }

    val density = LocalDensity.current

    // --- Bird size ---
    val birdSizeDp = 50.dp
    val birdSizePx = with(density) { birdSizeDp.toPx() }
    val birdSizePxInt = birdSizePx.roundToInt().coerceAtLeast(1)

    // ✅ BASE size for most branches
    val baseBranchWdp = 240.dp
    val baseBranchHdp = 100.dp

    // ✅ PER-BRANCH sizes (fix squish only for the problematic ones)
    // FIX HERE: put bigger width/height for the 2 squished branches.
    val branchSpecs = listOf(
        BranchSpec(branch1, baseBranchWdp, baseBranchHdp),   // kind 0
        BranchSpec(branch2, baseBranchWdp, baseBranchHdp),   // kind 1

        BranchSpec(branch3, baseBranchWdp, baseBranchHdp),   // kind 2  <- if squished: make wider/higher
        BranchSpec(branch4, baseBranchWdp, baseBranchHdp),   // kind 3  <- if squished: make wider/higher

        BranchSpec(branch5, baseBranchWdp, 180.dp),   // kind 4
        BranchSpec(branch6, baseBranchWdp, baseBranchHdp),   // kind 5
        BranchSpec(branch7, baseBranchWdp, baseBranchHdp),   // kind 6
        BranchSpec(branch8, baseBranchWdp, baseBranchHdp),   // kind 7
        BranchSpec(branch9, 300.dp, baseBranchHdp),   // kind 8
        BranchSpec(branch10, 210.dp, 140.dp),  // kind 9
        BranchSpec(branch11, 320.dp, baseBranchHdp),  // kind 10
        BranchSpec(branch12, baseBranchWdp, baseBranchHdp),  // kind 11
        BranchSpec(branch13, baseBranchWdp, baseBranchHdp),  // kind 12
    )

    // ✅ helper: per-kind px sizes
    fun branchWidthPx(kind: Int): Float = with(density) { branchSpecs[kind].wDp.toPx() }
    fun branchHeightPx(kind: Int): Float = with(density) { branchSpecs[kind].hDp.toPx() }

    // ✅ Tree size (як було)
    val treeWdp = 220.dp
    val treeHdp = 320.dp
    val treeHPx = with(density) { treeHdp.toPx() }

    // --- Alpha cutoff ---
    val alphaCutoff = 0.12f

    // --- Bird masks ---
    val birdMaskRight = rememberAlphaMask(birdRight, birdSizePxInt, birdSizePxInt, alphaCutoff)
    val birdMaskLeft = rememberAlphaMask(birdLeft, birdSizePxInt, birdSizePxInt, alphaCutoff)
    val birdMaskFlyLeft = rememberAlphaMask(birdFlyLeft, birdSizePxInt, birdSizePxInt, alphaCutoff)
    val birdMaskFlyRight = rememberAlphaMask(birdFlyRight, birdSizePxInt, birdSizePxInt, alphaCutoff)

    // ✅ Branch masks per branchSpec size (IMPORTANT)
    val branchMasks = branchSpecs.map { spec ->
        val wPxInt = with(density) { spec.wDp.toPx() }.roundToInt().coerceAtLeast(1)
        val hPxInt = with(density) { spec.hDp.toPx() }.roundToInt().coerceAtLeast(1)
        rememberAlphaMask(spec.painter, wPxInt, hPxInt, alphaCutoff)
    }

    // --- Bird position ---
    var birdX by remember { mutableStateOf(250f) }
    var birdWorldY by remember { mutableStateOf(0f) }
    var cameraY by remember { mutableStateOf(0f) }
    var birdScreenY by remember { mutableStateOf(200f) }

    // --- Speeds ---
    var speedX by remember { mutableStateOf(0f) }
    var speedY by remember { mutableStateOf(0f) }

    val sideSpeed = 700f
    val jumpImpulse = 500f
    val gravity = 1800f
    val secondsPerFrame = 1f / 60f

    // --- Bird animation ---
    var facingLeft by remember { mutableStateOf(false) }
    var flyFrames by remember { mutableStateOf(0) }

    // --- Held keys ---
    var leftHeld by remember { mutableStateOf(false) }
    var rightHeld by remember { mutableStateOf(false) }

    fun recomputeBirdScreenY() {
        birdScreenY = birdWorldY - cameraY
    }

    fun applySpeedFromHeld() {
        speedX = when {
            leftHeld && !rightHeld -> -sideSpeed
            rightHeld && !leftHeld -> sideSpeed
            else -> 0f
        }
    }

    fun jumpTo(dir: Int) {
        speedY = -jumpImpulse
        flyFrames = 8
        if (dir < 0) {
            facingLeft = true
            speedX = -sideSpeed
        } else {
            facingLeft = false
            speedX = sideSpeed
        }
    }

    // --- Camera window ---
    val topLockY = 140f
    val bottomLockY by remember { derivedStateOf { (screenH - 200f).coerceAtLeast(topLockY + 60f) } }

    // --- Floor in world ---
    val groundWorldY by remember { derivedStateOf { screenH - 80f } }

    // --- Tree in world ---
    val treeWorldX = 0f
    val treeWorldY by remember { derivedStateOf { groundWorldY - treeHPx } }

    // --- Obstacles ---
    var obstacles by remember { mutableStateOf(listOf<Obstacle>()) }
    var nextObstacleId by remember { mutableStateOf(1) }
    val spawnedTiles = remember { mutableSetOf<Int>() }

    fun modIndex(i: Int, size: Int): Int {
        if (size <= 0) return 0
        val m = i % size
        return if (m < 0) m + size else m
    }

    // ✅ spawn: X uses per-branch width (IMPORTANT)
    fun spawnBranchesForLevel(level: Int, tileH: Float) {
        val topWorldY = -level * tileH

        val margin = 12f
        val xLeft = -margin

        for (s in layoutForLevel(level)) {
            val kind = s.kind.coerceIn(0, branchSpecs.lastIndex)
            val wPx = branchWidthPx(kind)

            val xRight = screenW - wPx + margin
            val xCenter = (screenW - wPx) / 2f

            val x = when (s.anchor) {
                XAnchor.LEFT -> xLeft + s.xOffsetPx
                XAnchor.RIGHT -> xRight + s.xOffsetPx
                XAnchor.CENTER -> xCenter + s.xOffsetPx
            }

            val yFrac = s.yFrac.coerceIn(0.02f, 0.98f)
            val worldY = topWorldY + tileH * yFrac

            obstacles = obstacles + Obstacle(
                id = nextObstacleId++,
                xPx = x,
                worldYPx = worldY,
                kind = kind
            )
        }
    }

    val focusRequester = remember { FocusRequester() }

    // =====================================================
    // Physics loop
    // =====================================================
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect

        while (started) {
            if (flyFrames > 0) flyFrames--

            speedY += gravity * secondsPerFrame
            birdWorldY += speedY * secondsPerFrame
            birdX += speedX * secondsPerFrame

            birdX = birdX.coerceIn(0f, screenW - birdSizePx)

            if (birdWorldY > groundWorldY) {
                birdWorldY = groundWorldY
                speedY = 0f
            }

            recomputeBirdScreenY()

            if (birdScreenY < topLockY) {
                val diff = topLockY - birdScreenY
                cameraY -= diff
                recomputeBirdScreenY()
            }

            if (birdScreenY > bottomLockY) {
                val diff = birdScreenY - bottomLockY
                cameraY += diff
                recomputeBirdScreenY()
            }

            if (cameraY > 0f) {
                cameraY = 0f
                recomputeBirdScreenY()
            }

            val tileH = screenH.coerceAtLeast(1f)
            val topVisibleLevel = -floor(cameraY / tileH).toInt()
            // Якщо гравець піднявся до 5 рівня — він переміг
            if (topVisibleLevel >= 6) {
                gameWon = true
                started = false
            }

            for (lvl in listOf(topVisibleLevel, topVisibleLevel + 1)) {
                if (lvl >= 1 && spawnedTiles.add(lvl)) {
                    spawnBranchesForLevel(lvl, tileH)
                }
            }

            val isFlyingUp = speedY < 0f
            val useFly = (flyFrames > 0) || isFlyingUp

            val birdMask = when {
                useFly && facingLeft -> birdMaskFlyLeft
                useFly && !facingLeft -> birdMaskFlyRight
                !useFly && facingLeft -> birdMaskLeft
                else -> birdMaskRight
            }

            val birdDrawX = birdX.roundToInt()
            val birdDrawY = birdScreenY.roundToInt()

            for (o in obstacles) {
                val oScreenY = o.worldYPx - cameraY
                val hPx = branchHeightPx(o.kind)

                if (oScreenY > screenH + 250f || oScreenY + hPx < -250f) continue

                val obsX = o.xPx.roundToInt()
                val obsY = oScreenY.roundToInt()
                val mask = branchMasks[o.kind]

                if (pixelPerfectCollision(
                        aX = birdDrawX, aY = birdDrawY, aMask = birdMask,
                        bX = obsX, bY = obsY, bMask = mask
                    )
                ) {
                    gameOver = true
                    started = false
                    speedX = 0f
                    speedY = 0f
                    leftHeld = false
                    rightHeld = false
                    break
                }
            }

            delay(16)
        }
    }

    // =====================================================
    // UI + Rendering
    // =====================================================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(baseBackgroundColor)
            .onSizeChanged { s ->
                screenW = s.width.toFloat()
                screenH = s.height.toFloat()

                if (started) {
                    birdX = birdX.coerceIn(0f, screenW - birdSizePx)
                    birdWorldY = birdWorldY.coerceAtMost(groundWorldY)
                    if (cameraY > 0f) cameraY = 0f
                    recomputeBirdScreenY()
                }
            }
            .focusRequester(focusRequester)
            .focusProperties { canFocus = true }
            .onKeyEvent { e ->
                if (!started) return@onKeyEvent false

                val isLeftKey = (e.key == Key.DirectionLeft || e.key == Key.A || e.key == Key.NumPad4)
                val isRightKey = (e.key == Key.DirectionRight || e.key == Key.D || e.key == Key.NumPad6)

                when (e.type) {
                    KeyEventType.KeyDown -> when {
                        isLeftKey -> {
                            if (!leftHeld) { leftHeld = true; jumpTo(-1) } else { leftHeld = true; applySpeedFromHeld() }
                            true
                        }
                        isRightKey -> {
                            if (!rightHeld) { rightHeld = true; jumpTo(+1) } else { rightHeld = true; applySpeedFromHeld() }
                            true
                        }
                        else -> false
                    }

                    KeyEventType.KeyUp -> when {
                        isLeftKey -> { leftHeld = false; applySpeedFromHeld(); true }
                        isRightKey -> { rightHeld = false; applySpeedFromHeld(); true }
                        else -> false
                    }

                    else -> false
                }
            }
            .focusable()
    ) {
        // =====================================================
        // BACKGROUND (ТВІЙ, НЕ ЧІПАЮ)
        // =====================================================
        if (backgrounds.isNotEmpty()) {
            val tileH = screenH.coerceAtLeast(1f)

            val blendPx = 380f
            val overlapPx = 2f
            val overscan = 1.03f

            val baseTile = floor(cameraY / tileH).toInt()
            val baseScreenY = (baseTile * tileH - cameraY)
            val seamY = baseScreenY + tileH

            val topLevel = -baseTile
            val bottomLevel = topLevel - 1

            val topPainter = backgrounds[modIndex(topLevel, backgrounds.size)]
            val bottomPainter = backgrounds[modIndex(bottomLevel, backgrounds.size)]

            val half = blendPx / 2f
            val startP = ((seamY - half) / tileH).coerceIn(0f, 1f)
            val endP = ((seamY + half) / tileH).coerceIn(0f, 1f)

            Image(
                painter = bottomPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (baseScreenY + tileH - overlapPx).roundToInt()) }
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = overscan
                        scaleY = overscan
                    }
                    .drawWithContent {
                        drawContent()
                        val mask = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                startP to Color.Transparent,
                                endP to Color.White,
                                1f to Color.White
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter
            )

            Image(
                painter = topPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (baseScreenY + overlapPx).roundToInt()) }
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = overscan
                        scaleY = overscan
                    }
                    .drawWithContent {
                        drawContent()
                        val mask = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White,
                                startP to Color.White,
                                endP to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter
            )
        }

        // TREE (як було)
        run {
            val treeScreenY = treeWorldY - cameraY
            if (treeScreenY < screenH + 200f && treeScreenY + treeHPx > -200f) {
                Image(
                    painter = derevco,
                    contentDescription = null,
                    modifier = Modifier
                        .offset { IntOffset(treeWorldX.roundToInt(), treeScreenY.roundToInt()) }
                        .size(treeWdp, treeHdp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        // BRANCHES (✅ fixed)
        for (o in obstacles) {
            val oScreenY = o.worldYPx - cameraY
            val hPx = branchHeightPx(o.kind)
            if (oScreenY > screenH + 250f || oScreenY + hPx < -250f) continue

            val spec = branchSpecs[o.kind]
            Image(
                painter = spec.painter,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(o.xPx.roundToInt(), oScreenY.roundToInt()) }
                    .size(spec.wDp, spec.hDp),
                contentScale = ContentScale.FillBounds
            )
        }

// =====================================================
        // PLAY / RESTART
        // =====================================================
        if (!started && !gameOver) {
            Button(
                onClick = {
                    started = true
                    gameOver = false

                    birdX = screenW / 2f
                    birdWorldY = screenH * 0.65f
                    cameraY = 0f
                    speedX = 0f
                    speedY = 0f
                    flyFrames = 0
                    facingLeft = false

                    leftHeld = false
                    rightHeld = false

                    obstacles = emptyList()
                    nextObstacleId = 1
                    spawnedTiles.clear()

                    recomputeBirdScreenY()
                    focusRequester.requestFocus()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
            ) { Text("Play", color = Color.White) }
        }
        // =====================================================
        // SKINS
        // =====================================================
        if(!started){
            Button(
                onClick = {
                    skinChanged = !skinChanged
                },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(buttonColor),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 60.dp)
                    .size(45.dp),
            ){
                Image(
                    painterResource("images/icon_of_skins.svg"),
                    contentDescription = null,
                    Modifier.size(30.dp)
                )
            }
            // =====================================================
            // MUSIC
            // =====================================================
            Button(
                onClick = onMusicClick,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(buttonColor),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 60.dp)
                    .size(45.dp),
            ) {
                Image(
                    painterResource("images/music_icon.svg"),
                    contentDescription = null,
                    Modifier.size(25.dp)
                )
            }
        }

        if (gameOver) {
            Button(
                onClick = {
                    started = true
                    gameOver = false

                    birdX = screenW / 2f
                    birdWorldY = screenH * 0.65f
                    cameraY = 0f
                    speedX = 0f
                    speedY = 0f
                    flyFrames = 0
                    facingLeft = false

                    leftHeld = false
                    rightHeld = false

                    obstacles = emptyList()
                    nextObstacleId = 1
                    spawnedTiles.clear()

                    recomputeBirdScreenY()
                    focusRequester.requestFocus()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.Center)
            ) { Text("Restart", color = Color.White) }
        }
        // =====================================================
// WIN SCREEN
// =====================================================
        if (gameWon) {
            Button(
                onClick = {
                    // Скидаємо все для нової гри
                    gameWon = false
                    started = true
                    gameOver = false
                    birdX = screenW / 2f
                    birdWorldY = screenH * 0.65f
                    cameraY = 0f
                    speedX = 0f
                    speedY = 0f
                    flyFrames = 0
                    facingLeft = false
                    obstacles = emptyList()
                    nextObstacleId = 1
                    spawnedTiles.clear()
                    recomputeBirdScreenY()
                    focusRequester.requestFocus()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)), // Зелений для перемоги
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("You Win! Play Again", color = Color.White)
            }
        }

        // =====================================================
        // BIRD
        // =====================================================
        if (started) {
            val isFlyingUp = speedY < 0f
            val useFly = (flyFrames > 0) || isFlyingUp

            val birdPainter = when {
                useFly && facingLeft -> birdFlyLeft
                useFly && !facingLeft -> birdFlyRight
                !useFly && facingLeft -> birdLeft
                else -> birdRight
            }

            if (!skinChanged) {
                Image(
                    painter = birdPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(birdSizeDp)
                        .offset { IntOffset(birdX.roundToInt(), birdScreenY.roundToInt()) },
                    contentScale = ContentScale.FillBounds
                )
            }
            else {
                Image(
                    painter = kkk,
                    contentDescription = null,
                    modifier = Modifier
                        .size(birdSizeDp)
                        .offset { IntOffset(birdX.roundToInt(), birdScreenY.roundToInt()) },
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}


package ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.*
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
import kotlin.random.Random
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults.elevation
import kotlin.math.abs

// =====================================================
// Storm cutscene (cloud → lightning → smooth background switch)
// =====================================================
private enum class StormPhase {
    NONE,
    PAN_UP_TO_CLOUD,
    LIGHTNING_STRIKE,
    FADE_TO_STORM,
    PAN_BACK_TO_PLAYER
}

private fun smoothStep01(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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
    val kind: Int, // index in branchSpecs
    val isWind: Boolean,

    // --- enter animation (використовуємо тільки для wind) ---
    val targetXPx: Float = xPx,      // фінальний X (той, що ти порахував у layout)
    val spawnFromXPx: Float = xPx,   // стартовий X (за екраном)
    val enterT: Float = 1f,          // 0..1
    val enterDurSec: Float = 0f,     // тривалість “вльоту”
    val enterStarted: Boolean = true // стартувати не одразу (бо спавниш на тайл наперед)
)

enum class XAnchor { LEFT, RIGHT, CENTER }

data class ObstacleSpawn(
    val anchor: XAnchor,
    val yFrac: Float,
    val xFrac: Float,
    val kind: Int,
    val isWind : Boolean = false,
    val xOffsetPx: Float = 0f
)
interface IObstacleSpec {
    val painter: Painter
    val wDp: Dp
    val hDp: Dp
}

data class BranchSpec(
    override val painter: Painter,
    override val wDp: Dp,
    override val hDp: Dp
) : IObstacleSpec

data class BlackCloudSpec(
    val painter: Painter,
    val wDp: Dp,
    val hDp: Dp
)

data class WindSpec(
    override val painter: Painter,
    override val wDp: Dp,
    override val hDp: Dp
) : IObstacleSpec


@Composable
fun GameScreen(engine: GameEngine, musicEnabled: Boolean, onMusicClick: () -> Unit, onTreeDoubleClick: () -> Unit, skinIndex: Int, onSkinSelected: (Int) -> Unit) {
    var started by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }

    // =====================================================
    // Storm cutscene state (cloud → lightning → smooth BG3→BG4)
    // =====================================================
    var stormPhase by remember { mutableStateOf(StormPhase.NONE) }
    var stormPhaseTime by remember { mutableStateOf(0f) } // seconds inside current phase

    var stormCutsceneDone by remember { mutableStateOf(false) }
    var stormApplied by remember { mutableStateOf(false) } // BG3 заміняємо на BG4 після катсцени

    var stormFadeAlpha by remember { mutableStateOf(0f) } // 0..1 overlay BG4 while fading
    var flashAlpha by remember { mutableStateOf(0f) }     // 0..1 white flash overlay
    var lightningAlpha by remember { mutableStateOf(0f) } // 0..1 lightning visibility
    var lightningPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    var cloudVisible by remember { mutableStateOf(false) }
    var cloudAlphaTarget by remember { mutableStateOf(0f) }
    val cloudAlpha by animateFloatAsState(
        targetValue = cloudAlphaTarget,
        animationSpec = tween(durationMillis = 700),
        label = "cloudAlpha"
    )
    var cutsceneCamFromY by remember { mutableStateOf(0f) }
    var cutsceneCamToY by remember { mutableStateOf(0f) }

    // --- Bird sprites ---
    val birdRight = painterResource("images/ptashka_right.svg")
    val birdLeft = painterResource("images/ptashka_left.svg")
    val birdFlyLeft = painterResource("images/ptashka_fly_left.svg")
    val birdFlyRight = painterResource("images/ptashka_fly_right.svg")


    val derevco = painterResource("images/derevco.svg")

    var skinsOpen by remember { mutableStateOf(false) }

    val skinPreviews = listOf(
        birdRight,                 // 0: стандартна пташка (прев’ю)
        derevco,                   // 1
        painterResource("images/ura.svg") // 2
    )
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

    val blackCloud = painterResource("images/black_cloud.svg")

    val windBG1_1 = painterResource("images/windBG1_1.svg")
    val windBG1_2 = painterResource("images/windBG1_2.svg")
    val windBG1_3 = painterResource("images/windBG1_3.svg")
    val windBG1_4 = painterResource("images/windBG1_4.svg")
    val windBG1_5 = painterResource("images/windBG1_5.svg")
    val windBG2_1 = painterResource("images/windBG2_1.svg")
    val windBG2_2 = painterResource("images/windBG2_2.svg")
    val windBG2_3 = painterResource("images/windBG2_3.svg")
    val windBG2_4 = painterResource("images/windBG2_4.svg")
    val windBG2_5 = painterResource("images/windBG2_5.svg")
    val windBG2_6 = painterResource("images/windBG2_6.svg")



    // --- Backgrounds
    val backgrounds = listOf(

        painterResource("images/game_background1.png"),
        painterResource("images/game_background1.png"),
        painterResource("images/game_background1.png"),
        painterResource("images/game_background2.png"),
        painterResource("images/game_background2.png"),
        painterResource("images/game_background2.png"),
        painterResource("images/game_background3.png"),
        painterResource("images/game_background4.png"),
        painterResource("images/game_background4.png"),
        painterResource("images/game_background4.png"),
        painterResource("images/game_background4.png"),
        painterResource("images/game_background5.png"),
    )

    // =====================================================
    // Storm cutscene config
    // =====================================================

    val stormLevelIndex = 6 //index 6  = game_background3 (момент з хмарою), index 7  = game_background4 (буря)
    val stormBg4Painter = backgrounds.getOrNull(stormLevelIndex + 1) ?: backgrounds.last()

    // --- Layouts ---
    val layoutLevel1 = listOf(
        ObstacleSpawn(XAnchor.RIGHT, 0.08f,0f, kind = 0),
        ObstacleSpawn(XAnchor.LEFT, 0.30f, 0f, kind = 1),
        ObstacleSpawn(XAnchor.RIGHT, 0.55f, 0f, kind = 2),
        ObstacleSpawn(XAnchor.LEFT, 0.73f, 0f, kind = 3)
    )

    val layoutLevel2 = listOf(
        ObstacleSpawn(XAnchor.RIGHT, 0.29f,0f,  kind = 5),
        ObstacleSpawn(XAnchor.LEFT, 0.44f, 0f, kind = 4)
    )

    val layoutLevel3 = listOf(
        ObstacleSpawn(XAnchor.RIGHT, 0.22f, 0f, kind = 12),
        ObstacleSpawn(XAnchor.LEFT, 0.35f, 0f, kind = 11),
        ObstacleSpawn(XAnchor.LEFT, 0.68f, 0f, kind = 10)
    )

    val layoutLevel4 = listOf(
        ObstacleSpawn(XAnchor.RIGHT, 0.04f, 0f, kind = 8),
        ObstacleSpawn(XAnchor.LEFT, 0.20f, 0f, kind = 9),
        ObstacleSpawn(XAnchor.LEFT, 0.55f, 0f, kind = 7),
        ObstacleSpawn(XAnchor.RIGHT, 0.60f, 0f, kind = 6)
    )
    //WINDS
    val layoutLevel5 = listOf(
        ObstacleSpawn(XAnchor.RIGHT, 0.72f,0.70f,  kind = 0, isWind = true),
        ObstacleSpawn(XAnchor.RIGHT, 0.67f, 0.90f, kind = 1, isWind = true),
        ObstacleSpawn(XAnchor.LEFT, 0.45f, 0.1f, kind = 2, isWind = true),
        ObstacleSpawn(XAnchor.LEFT, 0.30f, 0.45f, kind = 3, isWind = true),
        ObstacleSpawn(XAnchor.RIGHT, 0.15f, 0f, kind = 4, isWind = true)
    )
    val layoutLevel6 = listOf(
        ObstacleSpawn(XAnchor.LEFT, 0.72f,0.05f,  kind = 5, isWind = true),
        ObstacleSpawn(XAnchor.RIGHT, 0.57f, 0.90f, kind = 6, isWind = true),
        ObstacleSpawn(XAnchor.LEFT, 0.40f, 0.90f, kind = 7, isWind = true),
        ObstacleSpawn(XAnchor.RIGHT, 0.30f, 0.45f, kind = 8, isWind = true),
        ObstacleSpawn(XAnchor.LEFT, 0.15f, 0f, kind = 9, isWind = true),
        ObstacleSpawn(XAnchor.RIGHT, 0.10f, 0f, 10, isWind = true)
    )

    fun layoutForLevel(level: Int): List<ObstacleSpawn> {
        return when(level){
            1 -> layoutLevel1
            2 -> layoutLevel2
            3 -> layoutLevel3
            4 -> layoutLevel4
            5 -> layoutLevel2
            7 -> layoutLevel5
            8 -> layoutLevel6

            else -> emptyList()
        }
    }

    var minCameraYReached by remember { mutableStateOf(0f) } // найбільш "висока" камера (найменше значення)
    var cameraLocked by remember { mutableStateOf(false) }

    val buttonColor = Color(0xFF1A1F2E).copy(alpha = 0.5f)
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

    val baseWindWdp = 140.dp
    val baseWindHdp = 75.dp

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
    val blackCloudSpecs = listOf(
        BlackCloudSpec(blackCloud, 150.dp, 100.dp)
    )

    val windSpecs = listOf(
        WindSpec(windBG1_1, 230.dp, baseWindHdp),
        WindSpec(windBG1_2, baseWindWdp, baseWindHdp),
        WindSpec(windBG1_3, 210.dp, baseWindHdp),
        WindSpec(windBG1_4, baseWindWdp, baseWindHdp),
        WindSpec(windBG1_5, baseWindWdp, baseWindHdp),

        WindSpec(windBG2_1, baseWindWdp, baseWindHdp),
        WindSpec(windBG2_2, baseWindWdp, baseWindHdp),
        WindSpec(windBG2_3, baseWindWdp, baseWindHdp),
        WindSpec(windBG2_4, baseWindWdp, baseWindHdp),
        WindSpec(windBG2_5, baseWindWdp, baseWindHdp),
        WindSpec(windBG2_6, baseWindWdp, baseWindHdp),
    )

    // ✅ helper: per-kind px sizes
    fun branchWidthPx(kind: Int): Float = with(density) { branchSpecs[kind].wDp.toPx() }
    fun branchHeightPx(kind: Int): Float = with(density) { branchSpecs[kind].hDp.toPx() }

    fun windWidthPx(kind: Int): Float = with(density) { windSpecs[kind].wDp.toPx() }
    fun windHeightPx(kind: Int): Float = with(density) { windSpecs[kind].hDp.toPx() }

    // ✅ helper: cloud size in px
    fun cloudWidthPx(): Float = with(density) { blackCloudSpecs[0].wDp.toPx() }
    fun cloudHeightPx(): Float = with(density) { blackCloudSpecs[0].hDp.toPx() }

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

    val windMasks = windSpecs.map { spec ->
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
    var activeWindId by remember { mutableStateOf<Int?>(null) }


    fun modIndex(i: Int, size: Int): Int {
        if (size <= 0) return 0
        val m = i % size
        return if (m < 0) m + size else m
    }

    fun backgroundForLevel(level: Int): Painter {
        val idx = modIndex(level.coerceAtLeast(0), backgrounds.size)
        val base = backgrounds[idx]
        return if (stormApplied && idx == stormLevelIndex) stormBg4Painter else base
    }

    // ✅ helper: generate a simple zig-zag lightning polyline (screen coordinates)
    fun buildLightningPolyline(start: Offset, end: Offset, segments: Int = 7): List<Offset> {
        val pts = ArrayList<Offset>(segments + 1)
        pts.add(start)

        val jitterX = 48f
        val jitterY = 26f

        for (i in 1 until segments) {
            val t = i / segments.toFloat()
            val x = lerp(start.x, end.x, t) + (Random.nextFloat() * 2f - 1f) * jitterX
            val y = lerp(start.y, end.y, t) + (Random.nextFloat() * 2f - 1f) * jitterY
            pts.add(Offset(x, y))
        }
        pts.add(end)
        return pts
    }

    // ✅ spawn: X uses per-branch width (IMPORTANT)
    fun spawnObstaclesForLevel(level: Int, tileH: Float) {
        val topWorldY = -level * tileH

        val margin = 12f
        val xLeft = -margin

        for (s in layoutForLevel(level)) {
            val kind = if(s.isWind) {
                s.kind.coerceIn(0, windSpecs.lastIndex)
            }else{
                s.kind.coerceIn(0, branchSpecs.lastIndex)
            }
            val wPx = if (s.isWind) windWidthPx(kind) else branchWidthPx(kind)

            val xRight = screenW - wPx + margin
            val xCenter = (screenW - wPx) / 2f

            val targetX: Float = if (s.xFrac != 0f) {
                (screenW - wPx) * s.xFrac + s.xOffsetPx
            } else {
                when (s.anchor) {
                    XAnchor.LEFT -> xLeft + s.xOffsetPx
                    XAnchor.RIGHT -> xRight + s.xOffsetPx
                    XAnchor.CENTER -> xCenter + s.xOffsetPx
                }
            }


            val yFrac = s.yFrac.coerceIn(0.02f, 0.98f)
            val worldY = topWorldY + tileH * yFrac

            if (s.isWind) {
                // стартуємо з того боку, де фінальна позиція ближче (не перелітаємо через весь екран)
                val startX = when (s.anchor) {
                    XAnchor.RIGHT -> screenW + 80f    // вітер "дує вправо" → прилітає зліва
                    XAnchor.LEFT  -> -wPx - 80f     // вітер "дує вліво"  → прилітає справа
                    XAnchor.CENTER -> if (targetX + wPx * 0.5f < screenW * 0.5f) {
                        -wPx - 80f
                    } else {
                        screenW + 80f
                    }
                }


                // щоб “не випереджав гравця”: обмежуємо швидкість входу
                // (у тебе sideSpeed = 700f, тому тут ставимо менше)
                val windMaxSpeedPxPerSec = 480f

                // ми будемо використовувати smoothStep01(t). В нього max slope ≈ 1.5,
                // тож щоб реальна швидкість не перевищувала windMaxSpeed, множимо dur на 1.5
                val dist = abs(targetX - startX)
                val dur = (dist * 1.5f / windMaxSpeedPxPerSec).coerceIn(0.45f, 1.25f)


                obstacles = obstacles + Obstacle(
                    id = nextObstacleId++,
                    xPx = startX,
                    worldYPx = worldY,
                    kind = kind,
                    isWind = true,
                    targetXPx = targetX,
                    spawnFromXPx = startX,
                    enterT = 0f,
                    enterDurSec = dur,
                    enterStarted = false
                )
            } else {
                obstacles = obstacles + Obstacle(
                    id = nextObstacleId++,
                    xPx = targetX,
                    worldYPx = worldY,
                    kind = kind,
                    isWind = false
                )
            }
        }
    }


    val focusRequester = remember { FocusRequester() }

    // -------------------------------------------------
    // Cloud fade AFTER background switch (stormApplied=true)
    // -------------------------------------------------
    LaunchedEffect(stormApplied) {
        if (!stormApplied) return@LaunchedEffect
        // 0.5s після кросфейду фону
        delay(500)
        // запускаємо плавне згасання (бо alpha прив'язана до animateFloatAsState)
        cloudAlphaTarget = 0f
        // дочекайся завершення tween(700), і тоді прибираємо з дерева
        delay(750)
        cloudVisible = false
    }

    // =====================================================
    // Physics loop
    // =====================================================
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect

        var lastNanos = 0L

        while (started) {
            val frameNanos = withFrameNanos { it }
            if (lastNanos == 0L) {
                lastNanos = frameNanos
                continue
            }
            var dt = (frameNanos - lastNanos) / 1_000_000_000f
            lastNanos = frameNanos

            // важливо: коли alt-tab/лаги — dt може стати 0.2с і все "стрибає"
            dt = dt.coerceIn(0f, 0.05f) // максимум 50мс за тик

            // -------------------------------------------------
            // Storm cutscene (BG3 -> cloud -> lightning -> BG4)
            // -------------------------------------------------
            val tileH = screenH.coerceAtLeast(1f)
            val currentLevel = -floor(cameraY / tileH).toInt()

            // Тригер: коли ми вже дійшли до рівня з background3
            // (і пташка достатньо високо в цьому "тайлі")
            if (!stormCutsceneDone && stormPhase == StormPhase.NONE && currentLevel == stormLevelIndex) {
                val triggerWorldY = (-stormLevelIndex * tileH) + tileH * 0.55f //(0 = самий верх тайлу, 1 = самий низ)
                if (birdWorldY <= triggerWorldY) {
                    stormPhase = StormPhase.PAN_UP_TO_CLOUD
                    stormPhaseTime = 0f

                    cloudVisible = true
                    cloudAlphaTarget = 1f

                    // запам'ятовуємо позицію камери, щоб потім повернутись
                    cutsceneCamFromY = cameraY

                    // хмару ставимо ближче до верху цього рівня
                    val cloudWorldY = (-stormLevelIndex * tileH) + tileH * 0.28f//Де стоїть хмара по висоті
                    val desiredCloudScreenY = 110f//На яку позицію на екрані камера “цілиться” при під’їзді
                    //Менше число → камера підніме хмару вище (ближче до верху екрану).
                    //Більше число → хмара буде нижче.

                    // щоб під час панорами ми не перескочили на наступний "рівень" (щоб не змінився topLevel)
                    val camMin = (-stormLevelIndex * tileH)
                    val camMax = (-(stormLevelIndex - 1) * tileH) - 0.001f
                    cutsceneCamToY = (cloudWorldY - desiredCloudScreenY).coerceIn(camMin, camMax)

                    // фіксуємо пташку
                    speedX = 0f
                    speedY = 0f
                    leftHeld = false
                    rightHeld = false
                    flyFrames = 0

                    // очистимо ефекти (про всяк випадок)
                    flashAlpha = 0f
                    lightningAlpha = 0f
                    stormFadeAlpha = 0f
                    lightningPoints = emptyList()
                }
            }

            val cutsceneActive = stormPhase != StormPhase.NONE
            if (cutsceneActive) {
                stormPhaseTime += dt

                when (stormPhase) {
                    StormPhase.PAN_UP_TO_CLOUD -> {
                        val dur = 1.10f//панорама вверх
                        val t = (stormPhaseTime / dur).coerceIn(0f, 1f)
                        cameraY = lerp(cutsceneCamFromY, cutsceneCamToY, smoothStep01(t))

                        if (t >= 1f) {
                            // під'їхали — робимо блискавку
                            cameraY = cutsceneCamToY
                            stormPhase = StormPhase.LIGHTNING_STRIKE
                            stormPhaseTime = 0f

                            flashAlpha = 1f
                            lightningAlpha = 1f

                            // генеруємо просту зигзаг-блискавку в координатах екрана
                            val cloudW = cloudWidthPx()
                            val cloudH = cloudHeightPx()
                            val cloudX = (screenW - cloudW) / 2f
                            val cloudWorldY = (-stormLevelIndex * tileH) + tileH * 0.18f
                            val cloudScreenY = cloudWorldY - cameraY

                            val start = Offset(
                                x = cloudX + cloudW * 0.35f,
                                y = cloudScreenY + cloudH * 0.85f
                            )
                            val end = Offset(
                                x = (start.x + (Random.nextFloat() * 160f - 80f)).coerceIn(40f, screenW - 40f),
                                y = screenH * 0.92f
                            )
                            lightningPoints = buildLightningPolyline(start, end)
                        }
                    }

                    StormPhase.LIGHTNING_STRIKE -> {
                        cameraY = cutsceneCamToY

                        val strikeDur = 0.32f//блискавка
                        val t = (stormPhaseTime / strikeDur).coerceIn(0f, 1f)

                        // блискавка + спалах згасають
                        lightningAlpha = (1f - t).coerceIn(0f, 1f)
                        flashAlpha = (1f - (stormPhaseTime / 0.13f)).coerceIn(0f, 1f)

                        if (t >= 1f) {
                            lightningAlpha = 0f
                            flashAlpha = 0f
                            stormPhase = StormPhase.FADE_TO_STORM
                            stormPhaseTime = 0f
                        }
                    }

                    StormPhase.FADE_TO_STORM -> {
                        cameraY = cutsceneCamToY

                        val fadeDur = 1.05f//кросфейд на бурю
                        val t = (stormPhaseTime / fadeDur).coerceIn(0f, 1f)
                        stormFadeAlpha = smoothStep01(t)

                        if (t >= 1f) {
                            // ✅ тут ми "вмикаємо бурю" назавжди для цього рівня
                            stormApplied = true

                            stormFadeAlpha = smoothStep01(t)
                            lightningPoints = emptyList()

                            stormPhase = StormPhase.PAN_BACK_TO_PLAYER
                            stormPhaseTime = 0f
                        }
                    }

                    StormPhase.PAN_BACK_TO_PLAYER -> {
                        val dur = 0.90f//повернення камери
                        val t = (stormPhaseTime / dur).coerceIn(0f, 1f)
                        cameraY = lerp(cutsceneCamToY, cutsceneCamFromY, smoothStep01(t))

                        if (t >= 1f) {
                            cameraY = cutsceneCamFromY
                            stormPhase = StormPhase.NONE
                            stormCutsceneDone = true
                            stormPhaseTime = 0f

                            // фікс: на виході точно прибираємо залишки ефектів
                            flashAlpha = 0f
                            lightningAlpha = 0f
                            stormFadeAlpha = 0f
                            lightningPoints = emptyList()
                        }
                    }

                    else -> Unit
                }

                // пташку не рухаємо, але її screenY має оновлюватись (бо камера рухається)
                recomputeBirdScreenY()

                delay(16)
                continue
            }


            engine.tick(dt)

            if (flyFrames > 0) flyFrames--

            speedY += gravity * secondsPerFrame
            birdWorldY += speedY * secondsPerFrame
            birdX += speedX * secondsPerFrame

            birdX = birdX.coerceIn(0f, screenW - birdSizePx)

            if (!cameraLocked && birdWorldY > groundWorldY) {
                birdWorldY = groundWorldY
                speedY = 0f
            }
// камера підтягується вгору
            if (birdScreenY < topLockY) {
                val diff = topLockY - birdScreenY
                cameraY -= diff
                recomputeBirdScreenY()
            }
// -------------------------------------------------
// Wind enter animation (ONE at a time)
// -------------------------------------------------
            val windPreEnterPx = 130f

// 1) якщо активний вітер уже закінчився — звільняємо слот
            activeWindId?.let { id ->
                val stillRunning = obstacles.any { it.id == id && it.isWind && it.enterT < 1f }
                if (!stillRunning) activeWindId = null
            }

// 2) якщо немає активного — запускаємо РІВНО ОДИН, який першим дійшов до "межі"
            if (activeWindId == null) {
                val next = obstacles
                    .asSequence()
                    .filter { it.isWind && it.enterT < 1f && !it.enterStarted }
                    .mapNotNull { o ->
                        val oScreenY = o.worldYPx - cameraY
                        val hPx = windHeightPx(o.kind)
                        if (oScreenY > -hPx - windPreEnterPx) o else null
                    }
                    .maxByOrNull { it.worldYPx } // найближчий до гравця (перший по порядку)

                if (next != null) {
                    activeWindId = next.id
                    obstacles = obstacles.map { o ->
                        if (o.id == next.id) o.copy(enterStarted = true) else o
                    }
                }
            }

// 3) анімуємо ТІЛЬКИ активний
            val idToAnimate = activeWindId
            if (idToAnimate != null) {
                var changed = false

                val updated = obstacles.map { o ->
                    if (!o.isWind) return@map o
                    if (o.id != idToAnimate) return@map o
                    if (o.enterT >= 1f) return@map o
                    if (!o.enterStarted) return@map o

                    val dur = o.enterDurSec.coerceAtLeast(0.001f)
                    val t = (o.enterT + dt / dur).coerceIn(0f, 1f)

                    val eased = smoothStep01(t)
                    val newX = lerp(o.spawnFromXPx, o.targetXPx, eased)

                    changed = true
                    o.copy(xPx = newX, enterT = t)
                }

                if (changed) obstacles = updated

                // якщо доробив — чистимо activeWindId
                val done = obstacles.firstOrNull { it.id == idToAnimate }?.enterT ?: 1f
                if (done >= 1f) activeWindId = null
            }


            recomputeBirdScreenY()



// лочимо вниз з моменту level2
            val lockAtLevel = 2
            val noting = -floor(cameraY / tileH).toInt()
            if (!cameraLocked && noting >= lockAtLevel) {
                cameraLocked = true
                minCameraYReached = cameraY
            }

            if (cameraLocked) {
                minCameraYReached = min(minCameraYReached, cameraY)
                cameraY = min(cameraY, minCameraYReached)
                recomputeBirdScreenY()

                if (birdScreenY > screenH + 60f) {
                    gameOver = true
                    started = false
                    speedX = 0f
                    speedY = 0f
                    leftHeld = false
                    rightHeld = false
                }
            } else {
                // до level2: можна падати і камера може вертатись вниз
                if (birdScreenY > bottomLockY) {
                    val diff = birdScreenY - bottomLockY
                    cameraY += diff
                    recomputeBirdScreenY()
                }
                if (cameraY > 0f) {
                    cameraY = 0f
                    recomputeBirdScreenY()
                }
            }

            recomputeBirdScreenY()

            val topVisibleLevel = -floor(cameraY / tileH).toInt()
            // Якщо гравець піднявся до 11 рівня — він переміг
            if (topVisibleLevel >= 11) {
                gameWon = true
                started = false
            }

            for (lvl in listOf(topVisibleLevel, topVisibleLevel + 1)) {
                if (lvl >= 1 && spawnedTiles.add(lvl)) {
                    spawnObstaclesForLevel(lvl, tileH)
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
                val hPx = if (o.isWind) windHeightPx(o.kind) else branchHeightPx(o.kind)

                if (oScreenY > screenH + 250f || oScreenY + hPx < -250f) continue

                val obsX = o.xPx.roundToInt()
                val obsY = oScreenY.roundToInt()
                val mask = if (o.isWind) windMasks[o.kind] else branchMasks[o.kind]

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

                // Під час катсцени (хмара/блискавка) — блокуємо керування
                if (stormPhase != StormPhase.NONE) return@onKeyEvent true

                val isLeftKey = (e.key == Key.DirectionLeft || e.key == Key.A || e.key == Key.NumPad4)
                val isRightKey = (e.key == Key.DirectionRight || e.key == Key.D || e.key == Key.NumPad6)

                when (e.type) {
                    KeyEventType.KeyDown -> when {
                        isLeftKey -> {
                            if (!leftHeld) {
                                leftHeld = true; jumpTo(-1)
                            } else {
                                leftHeld = true; applySpeedFromHeld()
                            }
                            true
                        }

                        isRightKey -> {
                            if (!rightHeld) {
                                rightHeld = true; jumpTo(+1)
                            } else {
                                rightHeld = true; applySpeedFromHeld()
                            }
                            true
                        }

                        else -> false
                    }

                    KeyEventType.KeyUp -> when {
                        isLeftKey -> {
                            leftHeld = false; applySpeedFromHeld(); true
                        }

                        isRightKey -> {
                            rightHeld = false; applySpeedFromHeld(); true
                        }

                        else -> false
                    }

                    else -> false
                }
            }
            .focusable()
    ) {


        // =====================================================
        // BACKGROUND
        // =====================================================
        val tileH = screenH.coerceAtLeast(1f)


        val blendPx = 280f
        val overlapPx = 16f
        val overscan = 1.25f//якщо змінити на більший то зміниться перехід(довший/коротший) між фонами різними
        //не ставити менше 1.2f!!!

        val baseTile = floor(cameraY / tileH).toInt()
        val baseScreenY = (baseTile * tileH - cameraY)
        val seamY = baseScreenY + tileH

        val topLevel = -baseTile
        val bottomLevel = topLevel - 1

        val topPainter = backgroundForLevel(topLevel)
        val bottomPainter = backgroundForLevel(bottomLevel)

        val topTy = baseScreenY + overlapPx
        val seamLocal = seamY - topTy

        // BOTTOM (без маски)
        Image(
            painter = bottomPainter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = baseScreenY + tileH - overlapPx
                    scaleX = overscan
                    scaleY = overscan
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter
        )

        // TOP (з маскою — твій smoothing)
        Image(
            painter = topPainter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = baseScreenY + overlapPx
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = overscan
                    scaleY = overscan
                }
                .drawWithContent {
                    drawContent()

                    val h = size.height
                    val y0 = (seamLocal - blendPx / 2f).coerceIn(0f, h)
                    val y1 = (seamLocal + blendPx / 2f).coerceIn(0f, h)
                    val p0 = (y0 / h).coerceIn(0f, 1f)
                    val p1 = (y1 / h).coerceIn(0f, 1f)

                    val mask = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White,
                            p0 to Color.White,
                            p1 to Color.Transparent,
                            1f to Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    )

                    drawRect(brush = mask, blendMode = BlendMode.DstIn)
                }
            ,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter
        )

        // Storm Overlay
        if (stormFadeAlpha > 0f) {
            Image(
                painter = stormBg4Painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = stormFadeAlpha
                        translationY = baseScreenY + tileH - overlapPx
                        scaleX = overscan
                        scaleY = overscan
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter
            )

            Image(
                painter = stormBg4Painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = stormFadeAlpha
                        translationY = baseScreenY + overlapPx
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = overscan
                        scaleY = overscan
                    }
                    .drawWithContent {
                        drawContent()

                        val h = size.height
                        val y0 = (seamLocal - blendPx / 2f).coerceIn(0f, h)
                        val y1 = (seamLocal + blendPx / 2f).coerceIn(0f, h)
                        val p0 = (y0 / h).coerceIn(0f, 1f)
                        val p1 = (y1 / h).coerceIn(0f, 1f)

                        val mask = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White,
                                p0 to Color.White,
                                p1 to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )

                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    }
                ,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter
            )
        }

        // =====================================================
        // CLOUD
        // =====================================================
        if (cloudVisible && cloudAlpha > 0f) {
            val tileH = screenH.coerceAtLeast(1f)
            val cloudW = cloudWidthPx()
            val cloudH = cloudHeightPx()
            val cloudX = (screenW - cloudW) / 2f
            val cloudWorldY = (-stormLevelIndex * tileH) + tileH * 0.18f
            val cloudScreenY = cloudWorldY - cameraY

            if (cloudScreenY < screenH + 250f && cloudScreenY + cloudH > -250f) {
                Image(
                    painter = blackCloud,
                    contentDescription = null,
                    modifier = Modifier
                        .offset { IntOffset(cloudX.roundToInt(), cloudScreenY.roundToInt()) }
                        .graphicsLayer(alpha = cloudAlpha)
                        .size(blackCloudSpecs[0].wDp, blackCloudSpecs[0].hDp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        // =====================================================
        // TREE
        // =====================================================
        @OptIn(ExperimentalFoundationApi::class)
        run {
            val treeScreenY = treeWorldY - cameraY
            if (treeScreenY < screenH + 200f && treeScreenY + treeHPx > -200f) {
                Image(
                    painter = derevco,
                    contentDescription = null,
                    modifier = Modifier
                        .offset { IntOffset(treeWorldX.roundToInt(), treeScreenY.roundToInt()) }
                        .size(treeWdp, treeHdp)
                        .combinedClickable(
                            // 1. Створюємо джерело взаємодії без візуалізації
                            interactionSource = remember { MutableInteractionSource() },
                            // 2. Вказуємо null, щоб прибрати ефект підсвічування (ripple)
                            indication = null,
                            onClick = {},
                            onDoubleClick = {
                                onTreeDoubleClick()
                            }
                        ),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        // =====================================================
        // BRANCHES
        // ====================================================

        for (o in obstacles) {
            val oScreenY = o.worldYPx - cameraY
            val hPx = if (o.isWind) windHeightPx(o.kind) else branchHeightPx(o.kind)
            if (oScreenY > screenH + 250f || oScreenY + hPx < -250f) continue

            val spec = if (o.isWind) windSpecs[o.kind] else branchSpecs[o.kind]
            val alpha = if (o.isWind) smoothStep01(o.enterT) else 1f
            Image(
                painter = spec.painter,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(o.xPx.roundToInt(), oScreenY.roundToInt()) }
                    .size(spec.wDp, spec.hDp)
                    .graphicsLayer{this.alpha = alpha},
                contentScale = ContentScale.FillBounds
            )
        }

        // =====================================================
        // PLAY / RESTART
        // =====================================================
        if (!started && !gameOver && !gameWon) {
            Button(
                onClick = {
                    started = true
                    gameOver = false

                    // --- reset storm cutscene ---
                    stormPhase = StormPhase.NONE
                    stormPhaseTime = 0f
                    stormCutsceneDone = false
                    stormApplied = false
                    stormFadeAlpha = 0f
                    flashAlpha = 0f
                    lightningAlpha = 0f
                    lightningPoints = emptyList()
                    cloudVisible = false
                    cloudAlphaTarget = 0f
                    cutsceneCamFromY = 0f
                    cutsceneCamToY = 0f

                    birdX = screenW / 2f
                    birdWorldY = screenH * 0.65f
                    cameraY = 0f
                    minCameraYReached = 0f
                    cameraLocked = false
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
        // SKINS BUTTON
        // =====================================================
        if (!started) {
            // клік по фону закриває панель
            if (skinsOpen) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { skinsOpen = false }
                )
            }

            // Правий нижній кут: панель над кнопкою
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 60.dp),
                horizontalAlignment = Alignment.End
            ) {

                AnimatedVisibility(
                    visible = skinsOpen,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Box(
                        modifier = Modifier
                            .width(210.dp)
                            .heightIn(max = 240.dp)
                            .background(color = buttonColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                            .padding(10.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(skinPreviews) { idx, preview ->
                                SkinTile(
                                    selected = (idx == skinIndex),
                                    onClick = {
                                        onSkinSelected(idx)
                                        skinsOpen = false
                                    }
                                ) {
                                    Image(
                                        painter = preview,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // сама кнопка скінів
                Button(
                    onClick = { skinsOpen = !skinsOpen },
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(buttonColor),
                    elevation = elevation(0.dp,0.dp,0.dp, 0.dp, 0.dp),//Прибираємо тіні
                    shape = CircleShape,
                    modifier = Modifier.size(45.dp),
                ) {
                    Image(
                        painterResource("images/icon_of_skins.svg"),
                        contentDescription = null,
                        Modifier.size(35.dp).padding(end = 2.dp)
                    )
                }
            }

            val musicPainter = if(musicEnabled) painterResource("/images/music_icon.svg") else painterResource("/images/music_icon_off.svg")
            // =====================================================
            // MUSIC
            // =====================================================
            Button(
                onClick = onMusicClick,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(buttonColor),
                elevation = elevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 60.dp)
                    .size(45.dp),
            ) {
                Image(
                    painter = musicPainter,
                    contentDescription = null,
                    Modifier.size(28.dp)
                )
            }
        }
        // =====================================================
        // RESTART
        // =====================================================
        if (gameOver) {
            Button(
                onClick = {
                    started = true
                    gameOver = false

                    // --- reset storm cutscene ---
                    stormPhase = StormPhase.NONE
                    stormPhaseTime = 0f
                    stormCutsceneDone = false
                    stormApplied = false
                    stormFadeAlpha = 0f
                    flashAlpha = 0f
                    lightningAlpha = 0f
                    lightningPoints = emptyList()
                    cloudVisible = false
                    cloudAlphaTarget = 0f
                    cutsceneCamFromY = 0f
                    cutsceneCamToY = 0f

                    birdX = screenW / 2f
                    birdWorldY = screenH * 0.65f
                    cameraY = 0f
                    minCameraYReached = 0f
                    cameraLocked = false
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
                elevation = elevation(0.dp, 0.dp, 0.dp, 0.7.dp),
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

                    // --- reset storm cutscene ---
                    stormPhase = StormPhase.NONE
                    stormPhaseTime = 0f
                    stormCutsceneDone = false
                    stormApplied = false
                    stormFadeAlpha = 0f
                    flashAlpha = 0f
                    lightningAlpha = 0f
                    lightningPoints = emptyList()
                    cloudVisible = false
                    cloudAlphaTarget = 0f
                    cutsceneCamFromY = 0f
                    cutsceneCamToY = 0f

                    birdX = screenW / 2f
                    birdWorldY = screenH * 0.65f
                    cameraY = 0f
                    minCameraYReached = 0f
                    cameraLocked = false
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
                elevation = elevation(0.dp, 0.dp, 0.dp, 0.7.dp),
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
            val painterToDraw = when (skinIndex) {
                0 -> birdPainter
                1 -> derevco
                2 -> painterResource("images/ura.svg")
                else -> birdPainter
            }
            Image(
                painter = painterToDraw,
                contentDescription = null,
                modifier = Modifier
                    .size(birdSizeDp)
                    .offset { IntOffset(birdX.roundToInt(), birdScreenY.roundToInt()) },
                contentScale = ContentScale.FillBounds
            )

        }

        // =====================================================
        // LIGHTNING + FLASH overlays (cutscene)
        // =====================================================
        if (lightningAlpha > 0f && lightningPoints.size >= 2) {
            val pts = lightningPoints
            ComposeCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = lightningAlpha }
            ) {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x, pts[i].y)
                    }
                }

                // легкий "glow" (ширше + прозоріше)
                drawPath(
                    path = path,
                    color = Color.Yellow.copy(alpha = 0.35f),
                    style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // основна лінія блискавки
                drawPath(
                    path = path,
                    color = Color.Yellow,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }
        val tileHDev = screenH.coerceAtLeast(1f)
        val devLevelNow = -floor(cameraY / tileHDev).toInt()

        if(!started) {
            DevToolsOverlay(
                levelNow = devLevelNow,
                buttonColor = buttonColor,
                onPlayFromLevel = { lvl ->
                    gameOver = false
                    gameWon = false

                    val h = screenH.coerceAtLeast(1f)
                    cameraY = -lvl * h
                    minCameraYReached = cameraY
                    cameraLocked = (lvl >= 3)

                    birdX = screenW / 2f
                    birdWorldY = (-lvl * h) + h * 0.90f//Менше число = вище, більше = нижче
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
                    started = true
                    focusRequester.requestFocus()

                    stormApplied = false
                    stormCutsceneDone = false
                }

            )
        }

    }
}


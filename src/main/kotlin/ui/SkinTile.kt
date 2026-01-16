package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SkinTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF586316), // під колір твоїх кнопок
    content: @Composable BoxScope.() -> Unit
) {
    var hovered by remember { mutableStateOf(false) }

    // ✅ ВАЖЛИВО: робимо діапазон "поза плиткою", щоб Restart не мигав
    val shift by rememberInfiniteTransition(label = "shine")
        .animateFloat(
            initialValue = -240f,
            targetValue = 240f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shift"
        )

    val shape = RoundedCornerShape(14.dp)

    // Те саме "переливання" як було, тільки без флікера
    val shineBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.05f),
        ),
        start = Offset(shift, 0f),
        end = Offset(shift + 120f, 120f)
    )

    Box(
        modifier = modifier
            .size(60.dp)
            .pointerMoveFilter(
                onEnter = { hovered = true; false },
                onExit = { hovered = false; false }
            )
            .clip(shape)
            .background(if (selected) Color(0xFF1F2A16) else Color(0xFF1A1F2E))
            .drawBehind {
                val stroke = 2.dp.toPx()

                // ✅ selected — красивий статичний бордер (НЕ миготить)
                if (selected) {
                    // легкий glow
                    drawRoundRect(
                        color = accent.copy(alpha = 0.18f),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 2.2f)
                    )
                    // основний бордер
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.25f),
                                accent.copy(alpha = 0.70f),
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                }

                // ✅ hovered — той самий "перелив" як у першому варіанті
                if (hovered) {
                    drawRoundRect(
                        brush = shineBrush,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        alpha = 0.35f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                    )
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.DevToolsOverlay(
    levelNow: Int,
    cameraLocked: Boolean,
    buttonColor: Color,
    onPlayFromLevel: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(12.dp)
            .background(Color.Transparent, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("DEV", color = Color.White)
        Text("level: $levelNow", color = Color.White)
        Text("camLocked: $cameraLocked", color = Color.White)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DevBtn("Play BG1", buttonColor) { onPlayFromLevel(0) }
            DevBtn("Play BG2", buttonColor) { onPlayFromLevel(3) }
            DevBtn("Play BG3", buttonColor) { onPlayFromLevel(6) }
        }
    }
}

@Composable
private fun DevBtn(text: String, buttonColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(text, color = Color.White) }
}

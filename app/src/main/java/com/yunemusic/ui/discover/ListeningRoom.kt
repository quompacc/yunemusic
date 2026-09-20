package com.yunemusic.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yunemusic.R
import com.yunemusic.ui.theme.*

/** Builds a fresh personal queue from local listening evidence. */
@Composable
fun ListeningRoom(isLoading: Boolean, error: String?, onPlay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(TextPrimary, RoundedCornerShape(6.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DEIN HÖRRAUM", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace, color = DarkBackground)
            Icon(painterResource(R.drawable.ic_launcher_monochrome), null,
                modifier = Modifier.size(48.dp).background(DarkBackground, RoundedCornerShape(12.dp)),
                tint = TextPrimary)
        }
        Text("Weniger suchen.\nMehr versinken.",
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Serif, color = DarkBackground)
        Text(
            error ?: if (isLoading) "Passende neue Songs werden für dich gesucht …"
                else "Neue Entdeckungen und vertraute Favoriten. Inspiriert von deinen Likes und deinem Hörverlauf.",
            style = MaterialTheme.typography.bodyMedium, color = Color(0xFF50584B)
        )
        Button(
            onClick = onPlay,
            enabled = !isLoading,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkBackground, contentColor = TextPrimary,
                disabledContainerColor = Color(0xFFD8D8CB), disabledContentColor = Color(0xFF50584B))
        ) {
            Text(if (isLoading) "Session wird erstellt" else "Session starten",
                modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SignalOrange)
        }
    }
}

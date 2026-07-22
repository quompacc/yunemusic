package com.yunemusic.ui.carmode

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yunemusic.ui.player.PlayerViewModel
import com.yunemusic.ui.theme.*

@Composable
fun CarModeScreen(
    onExitCarMode: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        VioletDark.copy(alpha = 0.2f),
                        Color(0xFF050508)
                    ),
                    radius = 1200f
                )
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 150f) {
                            viewModel.skipPrevious()
                        } else if (dragOffset < -150f) {
                            viewModel.skipNext()
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, delta ->
                        dragOffset += delta
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header with exit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onExitCarMode,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Car Mode beenden",
                        tint = TextTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = VioletLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Car Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = VioletLight
                    )
                }
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Album Art
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceVariantDark)
            ) {
                val thumbnailUrl = uiState.currentTrack?.thumbnailUrl
                if (!thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = VioletPrimary,
                        modifier = Modifier
                            .size(80.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Track Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.currentTrack?.title ?: "Kein Titel",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 26.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.currentTrack?.channelName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp
                )
            }

            // Fehler-Feedback — groß und gut lesbar für den Fahrbetrieb
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = ErrorRed,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorRed.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                )
            }

            // Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VioletPrimary,
                    trackColor = OutlineDark
                )
            }

            // Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                FilledIconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SurfaceVariantDark
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Vorheriger",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play/Pause - extra large
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(96.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = VioletPrimary
                    ),
                    shape = CircleShape
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = OnPrimary,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = OnPrimary,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // Next
                FilledIconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SurfaceVariantDark
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Nachster",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Swipe hint
            Text(
                text = "Wischen fur nachsten / vorherigen Song",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

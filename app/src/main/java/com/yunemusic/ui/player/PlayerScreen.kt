package com.yunemusic.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yunemusic.ui.theme.*

@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    onQueueClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VioletDark.copy(alpha = 0.3f),
                        DarkBackground
                    )
                )
            )
    ) {
        if (uiState.showQueue) {
            QueueSheet(
                queue = uiState.queue,
                currentIndex = uiState.currentQueueIndex,
                onDismiss = { viewModel.toggleQueueVisibility() },
                onJumpTo = { index ->
                    viewModel.toggleQueueVisibility()
                    viewModel.jumpToQueueItem(index)
                },
                onRemove = { viewModel.removeFromQueue(it) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Wird abgespielt",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Album Art
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = VioletPrimary.copy(alpha = 0.4f),
                        spotColor = VioletPrimary.copy(alpha = 0.6f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceVariantDark)
            ) {
                if (uiState.currentTrack?.thumbnailUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = uiState.currentTrack?.thumbnailUrl,
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

            Spacer(modifier = Modifier.height(40.dp))

            // Track Info + Like + Download buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.currentTrack?.title ?: "Kein Titel",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.currentTrack?.channelName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.toggleLike() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (uiState.isLiked) "Unlike" else "Like",
                        tint = if (uiState.isLiked) LikeRed else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (uiState.currentTrack != null) {
                    IconButton(
                        onClick = {
                            val track = uiState.currentTrack ?: return@IconButton
                            when {
                                uiState.isDownloading -> viewModel.cancelDownload()
                                uiState.isDownloaded -> viewModel.deleteDownload(track.id)
                                else -> viewModel.downloadTrack(track)
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (uiState.isDownloading) {
                            // Fortschrittsbalken mit X zum Abbrechen
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { uiState.downloadProgress },
                                    modifier = Modifier.size(26.dp),
                                    color = VioletPrimary,
                                    strokeWidth = 3.dp,
                                    trackColor = OutlineDark
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Abbrechen",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (uiState.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = if (uiState.isDownloaded) "Download entfernen" else "Download",
                                tint = if (uiState.isDownloaded) VioletPrimary else TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Download-Status-Text
            if (uiState.isDownloading && uiState.downloadStatus.isNotEmpty()) {
                Text(
                    text = uiState.downloadStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = VioletPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Progress Slider — seekt erst beim Loslassen; beim Ziehen zeigt eine
            // große Zeitvorschau die exakte Zielposition
            var dragProgress by remember { mutableStateOf<Float?>(null) }
            val durationSec = uiState.currentTrack?.durationSeconds ?: 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                dragProgress?.let { drag ->
                    Text(
                        text = formatDuration((drag * durationSec).toInt()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VioletPrimary
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = dragProgress ?: uiState.progress,
                    onValueChange = { dragProgress = it },
                    onValueChangeFinished = {
                        dragProgress?.let { viewModel.seekTo(it) }
                        dragProgress = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = VioletPrimary,
                        activeTrackColor = VioletPrimary,
                        inactiveTrackColor = OutlineDark
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val current = ((dragProgress ?: uiState.progress) * durationSec).toInt()
                    Text(
                        text = formatDuration(current),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    // Feinjustierung: ±10 Sekunden spulen
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.seekBy(-10) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "10 Sekunden zurück",
                                tint = TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(
                            onClick = { viewModel.seekBy(10) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "10 Sekunden vor",
                                tint = TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Text(
                        text = formatDuration(durationSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (uiState.isShuffleEnabled) VioletPrimary else TextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = VioletPrimary),
                    shape = CircleShape
                ) {
                    if (uiState.isLoading || uiState.isLoadingRadio) {
                        CircularProgressIndicator(color = OnPrimary, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = OnPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = when (uiState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (uiState.repeatMode != RepeatMode.OFF) VioletPrimary else TextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error display
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
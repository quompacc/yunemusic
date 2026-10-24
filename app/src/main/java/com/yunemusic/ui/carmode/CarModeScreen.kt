package com.yunemusic.ui.carmode

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yunemusic.R
import com.yunemusic.ui.player.PlayerUiState
import com.yunemusic.ui.player.PlayerViewModel
import com.yunemusic.ui.theme.*

@Composable
fun CarModeScreen(
    onExitCarMode: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dragOffset by remember { mutableFloatStateOf(0f) }

    ShowOverLockScreenAndKeepAwake()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SignalDark.copy(alpha = 0.2f),
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
        // Phone mounts are often landscape; there the controls get the right half.
        val controlsHeight = minOf(maxHeight * 0.3f, 200.dp)
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    CarModeHeader(onExitCarMode)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlbumArt(uiState, Modifier.size(96.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        TrackInfo(uiState, Modifier.weight(1f), TextAlign.Start)
                    }
                    ErrorBanner(uiState)
                    ProgressBar(uiState)
                }
                PlaybackControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CarModeHeader(onExitCarMode)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArt(
                        uiState,
                        Modifier
                            .sizeIn(maxWidth = 220.dp, maxHeight = 220.dp)
                            .aspectRatio(1f)
                    )
                }
                TrackInfo(uiState, Modifier.fillMaxWidth(), TextAlign.Center)
                ErrorBanner(uiState)
                ProgressBar(uiState)
                PlaybackControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlsHeight)
                )
                Text(
                    text = stringResource(R.string.carmode_swipe_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Lets the car mode appear over the keyguard without unlocking, and keeps the screen on
 * while it is open. Only this screen is shown while locked: leaving it hands control back
 * to the keyguard, so the rest of the app still requires unlocking.
 */
@Composable
private fun ShowOverLockScreenAndKeepAwake() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.setShowOverLockScreen(true)
        onDispose {
            activity.setShowOverLockScreen(false)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Suppress("DEPRECATION")
private fun Activity.setShowOverLockScreen(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(show)
    } else if (show) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun CarModeHeader(onExitCarMode: () -> Unit) {
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
                contentDescription = stringResource(R.string.carmode_exit),
                tint = TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = SignalLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.carmode_title),
                style = MaterialTheme.typography.labelLarge,
                color = SignalLight
            )
        }
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun AlbumArt(uiState: PlayerUiState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceVariantDark)
    ) {
        val thumbnailUrl = uiState.currentTrack?.thumbnailUrl
        if (!thumbnailUrl.isNullOrEmpty()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = stringResource(R.string.carmode_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SignalOrange,
                modifier = Modifier
                    .fillMaxSize(0.4f)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun TrackInfo(uiState: PlayerUiState, modifier: Modifier, textAlign: TextAlign) {
    Column(modifier = modifier) {
        Text(
            text = uiState.currentTrack?.title ?: stringResource(R.string.carmode_no_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 26.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.currentTrack?.channelName ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Fehler-Feedback — groß und gut lesbar für den Fahrbetrieb
@Composable
private fun ErrorBanner(uiState: PlayerUiState) {
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
}

@Composable
private fun ProgressBar(uiState: PlayerUiState) {
    LinearProgressIndicator(
        progress = { uiState.progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = SignalOrange,
        trackColor = OutlineDark
    )
}

/** Three large tap targets that split the available space, so they can be hit blind. */
@Composable
private fun PlaybackControls(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CarModeButton(
            icon = Icons.Default.SkipPrevious,
            contentDescription = stringResource(R.string.carmode_previous),
            containerColor = SurfaceVariantDark,
            contentColor = TextPrimary,
            onClick = { viewModel.skipPrevious() },
            modifier = Modifier.weight(1f)
        )
        CarModeButton(
            icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (uiState.isPlaying) R.string.carmode_pause else R.string.carmode_play),
            containerColor = SignalOrange,
            contentColor = OnPrimary,
            isLoading = uiState.isLoading || uiState.isLoadingRadio || uiState.isBuffering,
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.weight(1f)
        )
        CarModeButton(
            icon = Icons.Default.SkipNext,
            contentDescription = stringResource(R.string.carmode_next),
            containerColor = SurfaceVariantDark,
            contentColor = TextPrimary,
            onClick = { viewModel.skipNext() },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CarModeButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    isLoading: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = contentColor,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 5.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

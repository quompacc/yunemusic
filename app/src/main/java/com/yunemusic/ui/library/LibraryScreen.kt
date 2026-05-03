package com.yunemusic.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yunemusic.domain.model.Track
import com.yunemusic.ui.components.TrackOptionsSheet
import com.yunemusic.ui.discover.TrackListItem
import com.yunemusic.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var trackWithOptions by remember { mutableStateOf<Track?>(null) }

    val tabs = listOf("Geliked", "Verlauf", "Playlists")

    trackWithOptions?.let { track ->
        val isLiked = selectedTab == 0
        TrackOptionsSheet(
            track = track,
            onDismiss = { trackWithOptions = null },
            onPlayNow = { onTrackClick(track) },
            onPlayNext = { onPlayNext(track) },
            onAddToQueue = { onAddToQueue(track) },
            onLibraryToggle = if (isLiked) ({ viewModel.unlikeTrack(track.id) }) else null,
            libraryActionLabel = "Aus Bibliothek entfernen"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Bibliothek",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = VioletPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = VioletPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) VioletLight else TextSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> LikedSongsTab(
                tracks = uiState.likedTracks,
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onOptionsClick = { trackWithOptions = it }
            )
            1 -> HistoryTab(
                tracks = uiState.historyTracks,
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onOptionsClick = { trackWithOptions = it }
            )
            2 -> PlaylistsTab()
        }
    }
}

@Composable
private fun LikedSongsTab(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onOptionsClick: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyState(
            icon = Icons.Default.FavoriteBorder,
            message = "Noch keine gelikten Songs",
            subtitle = "Like Songs um sie hier zu sehen"
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                PlayAllHeader(
                    count = tracks.size,
                    label = "Songs",
                    onPlayAll = { onPlayAll(tracks) },
                    onShuffleAll = { onShuffleAll(tracks) }
                )
            }
            items(tracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onOptionsClick = { onOptionsClick(track) }
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onOptionsClick: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            message = "Noch keine Wiedergabe",
            subtitle = "Dein Hörverlauf erscheint hier"
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                PlayAllHeader(
                    count = tracks.size,
                    label = "Songs",
                    onPlayAll = { onPlayAll(tracks) },
                    onShuffleAll = { onShuffleAll(tracks) }
                )
            }
            items(tracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onOptionsClick = { onOptionsClick(track) }
                )
            }
        }
    }
}

@Composable
private fun PlayAllHeader(
    count: Int,
    label: String,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Abspielen")
            }
            OutlinedButton(
                onClick = onShuffleAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletLight),
                border = BorderStroke(1.dp, VioletPrimary)
            ) {
                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Zufällig")
            }
        }
    }
}

@Composable
private fun PlaylistsTab() {
    EmptyState(
        icon = Icons.Default.PlaylistPlay,
        message = "Playlists kommen bald",
        subtitle = "Diese Funktion wird noch entwickelt"
    )
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

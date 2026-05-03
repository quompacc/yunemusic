package com.yunemusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yunemusic.domain.model.Playlist
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
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // intercept back in playlist detail
    if (uiState.selectedPlaylist != null) {
        BackHandler { viewModel.deselectPlaylist() }
    }

    // track options sheet
    trackWithOptions?.let { track ->
        val isInPlaylist = uiState.selectedPlaylist != null
        val isLiked = selectedTab == 0 && uiState.selectedPlaylist == null
        TrackOptionsSheet(
            track = track,
            onDismiss = { trackWithOptions = null },
            onPlayNow = { onTrackClick(track) },
            onPlayNext = { onPlayNext(track) },
            onAddToQueue = { onAddToQueue(track) },
            onAddToPlaylist = { trackForPlaylist = track },
            onLibraryToggle = when {
                isLiked -> ({ viewModel.unlikeTrack(track.id) })
                isInPlaylist -> ({ viewModel.removeTrackFromPlaylist(track.id) })
                else -> null
            },
            libraryActionLabel = if (isInPlaylist) "Aus Playlist entfernen" else "Aus Bibliothek entfernen"
        )
    }

    // playlist picker sheet
    trackForPlaylist?.let { track ->
        PlaylistPickerSheet(
            playlists = uiState.playlists,
            onDismiss = { trackForPlaylist = null },
            onSelectPlaylist = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, track)
                trackForPlaylist = null
            },
            onCreateNew = {
                trackForPlaylist = null
                showCreatePlaylistDialog = true
                newPlaylistName = ""
                // remember track so we can add after creation
            }
        )
        // keep track stored for after-create add
    }

    // create playlist dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Neue Playlist", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Name der Playlist", color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = OutlineDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = VioletPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName)
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Erstellen", color = VioletLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Abbrechen", color = TextSecondary)
                }
            }
        )
    }

    val tabs = listOf("Geliked", "Verlauf", "Playlists")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                if (uiState.selectedPlaylist != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.deselectPlaylist() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = TextPrimary)
                        }
                        Text(
                            text = uiState.selectedPlaylist!!.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Bibliothek",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = VioletPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = VioletPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        if (index != 2) viewModel.deselectPlaylist()
                    },
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
            2 -> PlaylistsTab(
                playlists = uiState.playlists,
                selectedPlaylist = uiState.selectedPlaylist,
                playlistTracks = uiState.selectedPlaylistTracks,
                onPlaylistClick = { viewModel.selectPlaylist(it) },
                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                onCreatePlaylist = { showCreatePlaylistDialog = true; newPlaylistName = "" },
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onOptionsClick = { trackWithOptions = it }
            )
        }
    }
}

// ── Playlists Tab ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    selectedPlaylist: Playlist?,
    playlistTracks: List<Track>,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onOptionsClick: (Track) -> Unit
) {
    if (selectedPlaylist != null) {
        PlaylistDetailView(
            tracks = playlistTracks,
            onTrackClick = onTrackClick,
            onPlayAll = onPlayAll,
            onShuffleAll = onShuffleAll,
            onOptionsClick = onOptionsClick
        )
    } else {
        PlaylistListView(
            playlists = playlists,
            onPlaylistClick = onPlaylistClick,
            onDeletePlaylist = onDeletePlaylist,
            onCreatePlaylist = onCreatePlaylist
        )
    }
}

@Composable
private fun PlaylistListView(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                message = "Noch keine Playlists",
                subtitle = "Tippe + um eine Playlist zu erstellen"
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDelete = { onDeletePlaylist(playlist.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onCreatePlaylist,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = VioletPrimary,
            contentColor = TextPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Playlist erstellen")
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Playlist löschen", color = TextPrimary) },
            text = { Text("\"${playlist.name}\" wirklich löschen?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Abbrechen", color = TextSecondary)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = VioletLight,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${playlist.trackCount} Songs",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Optionen",
                tint = TextTertiary
            )
        }
    }
}

@Composable
private fun PlaylistDetailView(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onOptionsClick: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyState(
            icon = Icons.Default.MusicNote,
            message = "Playlist ist leer",
            subtitle = "Füge Songs über das Optionsmenü hinzu"
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

// ── Playlist Picker Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OutlineDark) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Zur Playlist hinzufügen",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = OutlineDark.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss(); onCreateNew() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = VioletLight, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Neue Playlist erstellen", style = MaterialTheme.typography.bodyMedium, color = VioletLight)
            }

            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(playlist) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("${playlist.trackCount} Songs", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }
        }
    }
}

// ── Shared tabs ───────────────────────────────────────────────────────────────

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

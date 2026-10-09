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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yunemusic.R
import com.yunemusic.domain.model.Playlist
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.model.YouTubePlaylist
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var trackWithOptions by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // intercept back in playlist detail
    if (uiState.selectedPlaylist != null || uiState.ytSelectedPlaylist != null) {
        BackHandler {
            if (uiState.ytSelectedPlaylist != null) viewModel.deselectYouTubePlaylist()
            else viewModel.deselectPlaylist()
        }
    }

    // track options sheet
    trackWithOptions?.let { track ->
        val isInPlaylist = uiState.selectedPlaylist != null
        val isLiked = selectedTab == 0 && uiState.selectedPlaylist == null
        val isDownloaded = selectedTab == 2
        TrackOptionsSheet(
            track = track,
            isDownloaded = isDownloaded,
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
            onDownloadToggle = when {
                isDownloaded -> ({ viewModel.deleteDownload(track.id) })
                isLiked -> null
                else -> null
            },
            libraryActionLabel = stringResource(
                if (isInPlaylist) R.string.library_remove_from_playlist else R.string.library_remove_from_library
            )
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
            }
        )
    }

    // create playlist dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = SurfaceDark,
            title = { Text(stringResource(R.string.library_new_playlist_title), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(stringResource(R.string.library_playlist_name_placeholder), color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalOrange,
                        unfocusedBorderColor = OutlineDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = SignalOrange
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
                    Text(stringResource(R.string.library_create), color = SignalLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(stringResource(R.string.library_cancel), color = TextSecondary)
                }
            }
        )
    }

    val tabs = listOf(
        stringResource(R.string.library_tab_liked),
        stringResource(R.string.library_tab_history),
        stringResource(R.string.library_tab_downloads),
        stringResource(R.string.library_tab_playlists)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                if (uiState.ytSelectedPlaylist != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.deselectYouTubePlaylist() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), tint = TextPrimary)
                        }
                        Text(
                            text = uiState.ytSelectedPlaylist!!.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (uiState.selectedPlaylist != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.deselectPlaylist() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), tint = TextPrimary)
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
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        // Give each label its required width; narrow screens / large fonts can scroll.
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = DarkBackground,
            contentColor = SignalOrange,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = SignalOrange
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        if (index != 3) {
                            viewModel.deselectPlaylist()
                            viewModel.deselectYouTubePlaylist()
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            softWrap = false,
                            color = if (selectedTab == index) SignalLight else TextSecondary,
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
            2 -> DownloadsTab(
                tracks = uiState.downloadedTracks,
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onOptionsClick = { trackWithOptions = it }
            )
            3 -> PlaylistsTab(
                uiState = uiState,
                viewModel = viewModel,
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onOptionsClick = { trackWithOptions = it },
                onCreatePlaylist = {
                    newPlaylistName = ""
                    showCreatePlaylistDialog = true
                }
            )
        }
    }
}

// ── Downloads Tab ─────────────────────────────────────────────────────────

@Composable
private fun DownloadsTab(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onOptionsClick: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Download,
            message = stringResource(R.string.library_downloads_empty),
            subtitle = stringResource(R.string.library_downloads_empty_subtitle)
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                PlayAllHeader(
                    countText = pluralStringResource(R.plurals.library_songs_offline_count, tracks.size, tracks.size),
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

// ── Playlists Tab (lokale Playlists + YouTube-Suche) ───────────────────────

@Composable
private fun PlaylistsTab(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onOptionsClick: (Track) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    // Wenn eine lokale Playlist ausgewaehlt ist, zeige deren Tracks
    if (uiState.selectedPlaylist != null) {
        if (uiState.selectedPlaylistTracks.isEmpty()) {
            EmptyState(
                icon = Icons.Default.MusicNote,
                message = stringResource(R.string.library_playlist_empty),
                subtitle = stringResource(R.string.library_playlist_empty_subtitle)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                item {
                    PlayAllHeader(
                        countText = pluralStringResource(R.plurals.library_song_count, uiState.selectedPlaylistTracks.size, uiState.selectedPlaylistTracks.size),
                        onPlayAll = { onPlayAll(uiState.selectedPlaylistTracks) },
                        onShuffleAll = { onShuffleAll(uiState.selectedPlaylistTracks) }
                    )
                }
                items(uiState.selectedPlaylistTracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOptionsClick = { onOptionsClick(track) }
                    )
                }
            }
        }
        return
    }

    // Wenn eine YouTube-Playlist ausgewaehlt ist, zeige Detailansicht
    if (uiState.ytSelectedPlaylist != null) {
        if (uiState.ytIsLoadingTracks) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SignalOrange)
            }
        } else if (uiState.ytPlaylistTracks.isEmpty()) {
            EmptyState(
                icon = Icons.Default.MusicNote,
                message = stringResource(R.string.library_no_tracks_found),
                subtitle = stringResource(R.string.library_yt_playlist_empty_subtitle)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                item {
                    PlayAllHeader(
                        countText = pluralStringResource(R.plurals.library_song_count, uiState.ytPlaylistTracks.size, uiState.ytPlaylistTracks.size),
                        onPlayAll = { onPlayAll(uiState.ytPlaylistTracks) },
                        onShuffleAll = { onShuffleAll(uiState.ytPlaylistTracks) }
                    )
                }
                items(uiState.ytPlaylistTracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOptionsClick = { onOptionsClick(track) }
                    )
                }
            }
        }
        return
    }

    // Uebersicht: lokale Playlists + YouTube-Suche
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Meine Playlists ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_my_playlists),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = SignalLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.library_new), color = SignalLight, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (uiState.playlists.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.library_no_own_playlists),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            items(uiState.playlists, key = { it.id }) { playlist ->
                LocalPlaylistItem(
                    playlist = playlist,
                    onClick = { viewModel.selectPlaylist(playlist) },
                    onDelete = { viewModel.deletePlaylist(playlist.id) }
                )
            }
        }

        // ── YouTube Playlists ───────────────────────────────────────────
        item {
            Text(
                text = stringResource(R.string.library_youtube_playlists),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
        }

        // Suchleiste
        item {
            OutlinedTextField(
                value = uiState.ytSearchQuery,
                onValueChange = { viewModel.updateYtSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.library_yt_search_placeholder), color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                trailingIcon = {
                    if (uiState.ytSearchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.searchYouTubePlaylists()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.library_search), tint = SignalLight)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchYouTubePlaylists() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SignalOrange,
                    unfocusedBorderColor = OutlineDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = SignalOrange,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Fehlermeldung
        uiState.ytError?.let { error ->
            item {
                Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    action = {
                        TextButton(onClick = { viewModel.clearYtError() }) {
                            Text(stringResource(R.string.library_ok))
                        }
                    }
                ) {
                    Text(
                        text = when (error) {
                            is LibraryMessage.Text -> error.text
                            is LibraryMessage.Resource -> stringResource(error.resId)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Ladeanzeige / Leer-Status / Ergebnisse
        if (uiState.ytIsSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SignalOrange)
                }
            }
        } else if (uiState.ytSearchResults.isEmpty()) {
            item {
                if (uiState.ytSearchQuery.isBlank()) {
                    InlineEmptyState(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        message = stringResource(R.string.library_youtube_playlists),
                        subtitle = stringResource(R.string.library_yt_search_hint)
                    )
                } else {
                    InlineEmptyState(
                        icon = Icons.Default.SearchOff,
                        message = stringResource(R.string.library_no_results),
                        subtitle = stringResource(R.string.library_no_results_subtitle)
                    )
                }
            }
        } else {
            items(uiState.ytSearchResults, key = { it.id }) { playlist ->
                YouTubePlaylistItem(
                    playlist = playlist,
                    onClick = { viewModel.selectYouTubePlaylist(playlist) }
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = SignalLight,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(R.plurals.library_song_count, playlist.trackCount, playlist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.library_delete_playlist),
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Kompakter Empty-State fuer die Verwendung innerhalb einer LazyColumn
@Composable
private fun InlineEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun YouTubePlaylistItem(
    playlist: YouTubePlaylist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = SignalLight,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(R.plurals.library_song_count, playlist.trackCount, playlist.trackCount),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = stringResource(R.string.library_open),
            tint = SignalLight,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Shared tabs ────────────────────────────────────────────────────────────

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
            message = stringResource(R.string.library_liked_empty),
            subtitle = stringResource(R.string.library_liked_empty_subtitle)
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                PlayAllHeader(
                    countText = pluralStringResource(R.plurals.library_song_count, tracks.size, tracks.size),
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
            message = stringResource(R.string.library_history_empty),
            subtitle = stringResource(R.string.library_history_empty_subtitle)
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                PlayAllHeader(
                    countText = pluralStringResource(R.plurals.library_song_count, tracks.size, tracks.size),
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
    countText: String,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.library_play))
            }
            OutlinedButton(
                onClick = onShuffleAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalLight),
                border = BorderStroke(1.dp, SignalOrange)
            ) {
                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.library_shuffle))
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

// ── Playlist Picker Sheet ──────────────────────────────────────────────────

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
                text = stringResource(R.string.library_add_to_playlist),
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
                Icon(Icons.Default.Add, contentDescription = null, tint = SignalLight, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.library_create_new_playlist), style = MaterialTheme.typography.bodyMedium, color = SignalLight)
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
                        Text(pluralStringResource(R.plurals.library_song_count, playlist.trackCount, playlist.trackCount), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }
        }
    }
}

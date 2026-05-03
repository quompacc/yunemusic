package com.yunemusic.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yunemusic.domain.model.Track
import com.yunemusic.ui.components.TrackOptionsSheet
import com.yunemusic.ui.theme.*
import java.util.Calendar

val musicCategories = listOf("Rock", "Electronic", "Jazz", "Hip-Hop", "Classical", "Pop", "R&B", "Metal")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    onTrackClick: (Track) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToLibrary: (Track) -> Unit = {},
    onPlayAll: (List<Track>) -> Unit = {},
    onShuffleAll: (List<Track>) -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackWithOptions by remember { mutableStateOf<Track?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { uiState.featuredTracks.size })

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Guten Morgen"
            in 12..17 -> "Guten Tag"
            in 18..21 -> "Guten Abend"
            else -> "Gute Nacht"
        }
    }

    trackWithOptions?.let { track ->
        TrackOptionsSheet(
            track = track,
            onDismiss = { trackWithOptions = null },
            onPlayNow = { onTrackClick(track) },
            onPlayNext = { onPlayNext(track) },
            onAddToQueue = { onAddToQueue(track) },
            onLibraryToggle = { onAddToLibrary(track) },
            libraryActionLabel = "Zur Bibliothek hinzufügen"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ── Top Bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(greeting, style = MaterialTheme.typography.labelMedium, color = VioletLight)
                Text(
                    text = "YuneMusic",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = {
                    if (showSearch) viewModel.clearSearch()
                    showSearch = !showSearch
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceVariantDark, CircleShape)
            ) {
                Icon(
                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Suche",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Collapsible Search Bar ───────────────────────────────────────────
        AnimatedVisibility(visible = showSearch, enter = expandVertically(), exit = shrinkVertically()) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.search(it) },
                onSearch = { viewModel.search(it) },
                active = false,
                onActiveChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Suche nach Songs, Artists...", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, null, tint = TextSecondary)
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = SurfaceVariantDark,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = VioletPrimary
                    )
                )
            ) {}
        }

        // ── Main Content Grid ────────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Hero Carousel (full width) ───────────────────────────────────
            if (uiState.featuredTracks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(end = 32.dp),
                            pageSpacing = 12.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            FeaturedCard(
                                track = uiState.featuredTracks[page],
                                onClick = { onTrackClick(uiState.featuredTracks[page]) },
                                onLongClick = { trackWithOptions = uiState.featuredTracks[page] }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(uiState.featuredTracks.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(
                                            width = if (pagerState.currentPage == index) 20.dp else 6.dp,
                                            height = 6.dp
                                        )
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (pagerState.currentPage == index) VioletPrimary
                                            else TextTertiary.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }
            } else if (uiState.isLoadingTrending) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceVariantDark),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = VioletPrimary) }
                }
            }

            // ── Genre Chips (full width) ─────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(musicCategories) { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = {
                                if (uiState.selectedCategory == category) {
                                    viewModel.clearSearch()
                                } else {
                                    showSearch = true
                                    viewModel.searchByCategory(category)
                                }
                            },
                            label = { Text(category, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceVariantDark,
                                labelColor = TextSecondary,
                                selectedContainerColor = VioletContainer,
                                selectedLabelColor = OnVioletContainer
                            )
                        )
                    }
                }
            }

            // ── SEARCH RESULTS ───────────────────────────────────────────────
            if (uiState.isSearchActive) {
                if (uiState.isLoadingSearch) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = VioletPrimary) }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Suchergebnisse")
                    }
                    items(
                        items = uiState.searchResults,
                        span = { GridItemSpan(maxLineSpan) }
                    ) { track ->
                        TrackListItem(
                            track = track,
                            onClick = { onTrackClick(track) },
                            onOptionsClick = { trackWithOptions = track }
                        )
                    }
                }
            } else {
                // ── Personalization hint ─────────────────────────────────────
                if (!uiState.hasPersonalProfile && !uiState.isLoadingRecommendations) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VioletContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = VioletLight, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Hör mehr Musik – dein Algorithmus lernt deinen Stil",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // ── "Für dich" Recommendations ───────────────────────────────
                if (uiState.recommendations.isNotEmpty() || uiState.isLoadingRecommendations) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = if (uiState.hasPersonalProfile) "Für dich" else "Entdecken",
                            subtitle = if (uiState.hasPersonalProfile) "Basierend auf deinem Geschmack" else null,
                            onPlay = if (uiState.recommendations.isNotEmpty()) ({ onPlayAll(uiState.recommendations) }) else null,
                            onShuffle = if (uiState.recommendations.isNotEmpty()) ({ onShuffleAll(uiState.recommendations) }) else null
                        )
                    }
                    if (uiState.isLoadingRecommendations) {
                        items(4) { AlbumCardSkeleton() }
                    } else {
                        items(uiState.recommendations) { track ->
                            AlbumCard(
                                track = track,
                                onClick = { onTrackClick(track) },
                                onLongClick = { trackWithOptions = track }
                            )
                        }
                    }
                }

                // ── "Zuletzt gehört" ─────────────────────────────────────────
                if (uiState.recentTracks.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "Zuletzt gehört",
                            onPlay = { onPlayAll(uiState.recentTracks) },
                            onShuffle = { onShuffleAll(uiState.recentTracks) }
                        )
                    }
                    items(uiState.recentTracks.take(8)) { track ->
                        AlbumCard(
                            track = track,
                            onClick = { onTrackClick(track) },
                            onLongClick = { trackWithOptions = track }
                        )
                    }
                }

                // ── Smart Mixes ──────────────────────────────────────────────
                for (mix in uiState.smartMixes) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "${mix.emoji} ${mix.label}",
                            onPlay = { onPlayAll(mix.tracks) },
                            onShuffle = { onShuffleAll(mix.tracks) }
                        )
                    }
                    items(mix.tracks.take(6)) { track ->
                        AlbumCard(
                            track = track,
                            onClick = { onTrackClick(track) },
                            onLongClick = { trackWithOptions = track }
                        )
                    }
                }

                // ── Trending ─────────────────────────────────────────────────
                if (uiState.trendingTracks.isNotEmpty() || uiState.isLoadingTrending) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "Trending",
                            onPlay = if (uiState.trendingTracks.isNotEmpty()) ({ onPlayAll(uiState.trendingTracks) }) else null,
                            onShuffle = if (uiState.trendingTracks.isNotEmpty()) ({ onShuffleAll(uiState.trendingTracks) }) else null
                        )
                    }
                    if (uiState.isLoadingTrending) {
                        items(4) { AlbumCardSkeleton() }
                    } else {
                        items(uiState.trendingTracks) { track ->
                            AlbumCard(
                                track = track,
                                onClick = { onTrackClick(track) },
                                onLongClick = { trackWithOptions = track }
                            )
                        }
                    }
                }

                // ── Error ────────────────────────────────────────────────────
                uiState.error?.let { error ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f))) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = ErrorRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(Icons.Default.Close, null, tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Hero / Featured Card ────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedCard(
    track: Track,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.78f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(34.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(track.channelName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

// ── Square Album Card — fills grid column width ──────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    track: Track,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVariantDark)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle ?: track.channelName,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Skeleton placeholder card ────────────────────────────────────────────────

@Composable
fun AlbumCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVariantDark)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceVariantDark))
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceVariantDark))
    }
}

// ── Section Header ──────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = VioletLight)
            }
        }
        if (onShuffle != null || onPlay != null) {
            Row {
                if (onShuffle != null) {
                    IconButton(onClick = onShuffle, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Shuffle, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                if (onPlay != null) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.PlayCircle, null, tint = VioletLight, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ── Kept for LibraryScreen backward compat ──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendationCard(
    track: Track,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AlbumCard(track = track, onClick = onClick, onLongClick = onLongClick, modifier = modifier)
}

@Composable
fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOptionsClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark)
        ) {
            AsyncImage(model = track.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(track.channelName, style = MaterialTheme.typography.bodySmall, color = TextTertiary, maxLines = 1)
        }
        if (track.durationSeconds > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(formatDurationShort(track.durationSeconds), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        trailingContent?.let { Spacer(modifier = Modifier.width(4.dp)); it() }
        if (onOptionsClick != null) {
            IconButton(onClick = onOptionsClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatDurationShort(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

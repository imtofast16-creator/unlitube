package app.unlitube.android.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.PlaybackException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import app.unlitube.android.data.ChannelPage
import app.unlitube.android.data.CommentItem
import app.unlitube.android.BuildConfig
import app.unlitube.android.data.DownloadStore
import app.unlitube.android.data.HistoryEntity
import app.unlitube.android.data.LibraryStore
import app.unlitube.android.data.PlaylistEntity
import app.unlitube.android.data.RemoteVersion
import app.unlitube.android.data.SponsorBlockClient
import app.unlitube.android.data.SponsorSegment
import app.unlitube.android.data.UpdateCheck
import app.unlitube.android.data.SettingsStore
import app.unlitube.android.data.VideoDetails
import app.unlitube.android.data.VideoItem
import app.unlitube.android.data.VideoPage
import app.unlitube.android.data.YouTubeRepository
import app.unlitube.android.player.PlayerHolder
import app.unlitube.android.player.PlayerSurface
import app.unlitube.android.player.UnliPlayer
import app.unlitube.android.ui.theme.UnliCoral
import app.unlitube.android.ui.theme.UnliViolet
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Phase 1: live data from Engine A (InnerTube calls made on the phone, see
// data/YouTubeClient.kt). Home, search, watch, quality picker and related
// videos are real. Slice 5 adds the local Room library: subscriptions,
// history with resume, likes, Watch Later and playlists, all on the phone.

private val feedFolders = listOf("All", "Study", "Music", "Gaming", "News", "Live", "Shorts")

private sealed interface FeedState {
    data object Loading : FeedState
    data class Error(val message: String) : FeedState
    data class Ready(val items: List<VideoItem>) : FeedState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(initialVideoId: String? = null) {
    val context = LocalContext.current
    val repository = remember { YouTubeRepository() }
    var updateAvailable by remember { mutableStateOf<RemoteVersion?>(null) }

    LaunchedEffect(Unit) {
        val remote = runCatching { UpdateCheck.fetch() }.getOrNull()
        if (remote != null && remote.versionCode > BuildConfig.VERSION_CODE &&
            remote.apkUrl.isNotBlank()
        ) {
            updateAvailable = remote
        }
    }


    var selectedTab by remember { mutableStateOf(0) }
    var selectedFolder by remember { mutableStateOf("All") }

    LaunchedEffect(SettingsStore.hideShorts) {
        if (SettingsStore.hideShorts && selectedFolder == "Shorts") {
            selectedFolder = "All"
        }
    }
    var feedState by remember { mutableStateOf<FeedState>(FeedState.Loading) }
    var feedNonce by remember { mutableStateOf(0) }
    var openedVideoId by remember { mutableStateOf<String?>(initialVideoId) }
    var openedChannel by remember { mutableStateOf<Pair<String, String>?>(null) }
    var history by remember { mutableStateOf<List<HistoryEntity>>(emptyList()) }

    LaunchedEffect(openedVideoId, selectedTab) {
        if (openedVideoId == null) {
            history = runCatching { LibraryStore.history() }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(selectedFolder, feedNonce) {
        feedState = FeedState.Loading
        feedState = runCatching { repository.loadHome(selectedFolder) }
            .fold(
                onSuccess = { FeedState.Ready(it) },
                onFailure = { FeedState.Error(it.message ?: "Could not load the feed") }
            )
    }

    BackHandler(enabled = openedVideoId != null) { openedVideoId = null }
    BackHandler(enabled = openedVideoId == null && openedChannel != null) { openedChannel = null }

    Scaffold(
        bottomBar = {
            if (openedVideoId == null) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (PlayerHolder.nowPlaying != null && !PlayerHolder.embedOnly) {
                        MiniPlayerBar(
                            onOpen = { openedVideoId = PlayerHolder.nowPlaying?.id },
                            onClose = { PlayerHolder.stopAndClear() }
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            NavPillItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = Icons.Filled.Home,
                                label = "Home"
                            )
                            NavPillItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = Icons.Filled.Search,
                                label = "Search"
                            )
                            NavPillItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = Icons.Filled.Subscriptions,
                                label = "Library"
                            )
                            NavPillItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = Icons.Filled.Person,
                                label = "You"
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val videoId = openedVideoId
            val channel = openedChannel
            if (videoId != null) {
                WatchScreen(
                    repository = repository,
                    videoId = videoId,
                    onBack = { openedVideoId = null },
                    onOpenVideo = { openedVideoId = it },
                    onOpenChannel = { id, name -> openedChannel = id to name }
                )
            } else if (channel != null) {
                ChannelScreen(
                    repository = repository,
                    channelId = channel.first,
                    fallbackName = channel.second,
                    onBack = { openedChannel = null },
                    onOpenVideo = { openedVideoId = it }
                )
            } else {
                when (selectedTab) {
                    0 -> if (SettingsStore.searchOnlyMode) {
                        SearchOnlyHome(onSearchTap = { selectedTab = 1 })
                    } else HomeFeed(
                        feedState = feedState,
                        selectedFolder = selectedFolder,
                        history = history,
                        onFolderSelected = { selectedFolder = it },
                        onRetry = { feedNonce++ },
                        onSearchTap = { selectedTab = 1 },
                        onOpenVideo = { openedVideoId = it },
                        onOpenChannel = { id, name -> openedChannel = id to name }
                    )
                    1 -> SearchScreen(
                        repository = repository,
                        onOpenVideo = { openedVideoId = it },
                        onOpenChannel = { id, name -> openedChannel = id to name }
                    )
                    2 -> LibraryScreen(
                        repository = repository,
                        onOpenVideo = { openedVideoId = it },
                        onOpenChannel = { id, name -> openedChannel = id to name }
                    )
                    else -> YouScreen()
                }
            }
        }
    }

    val available = updateAvailable
    if (available != null) {
        AlertDialog(
            onDismissRequest = { updateAvailable = null },
            title = { Text("Update available: ${available.versionName}") },
            text = { Text(available.notes) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(available.apkUrl))
                    )
                    updateAvailable = null
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updateAvailable = null }) { Text("Later") }
            }
        )
    }
}

@Composable
private fun SearchOnlyHome(onSearchTap: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Header(onSearchTap = onSearchTap) }
        item {
            Text(
                text = "Search-only mode is on. The feed, Shorts and recommendations are hidden. Turn it off in the You tab to bring the feed back.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun NavPillItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeed(
    feedState: FeedState,
    selectedFolder: String,
    history: List<HistoryEntity>,
    onFolderSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onSearchTap: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Header(onSearchTap = onSearchTap) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(if (SettingsStore.hideShorts) feedFolders - "Shorts" else feedFolders) { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onFolderSelected(folder) },
                        label = { Text(folder) }
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            item {
                Text(
                    text = "Continue Watching",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history.take(8), key = { "continue-${it.videoId}" }) { entry ->
                        Column(
                            modifier = Modifier
                                .size(width = 164.dp, height = 150.dp)
                                .clickable { onOpenVideo(entry.videoId) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = entry.thumbnailUrl,
                                    contentDescription = entry.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val fraction = if (entry.durationMs > 0) {
                                (entry.positionMs.toFloat() / entry.durationMs.toFloat())
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = UnliViolet,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = entry.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
        when (val state = feedState) {
            FeedState.Loading -> item { LoadingBlock("Loading your live feed") }
            is FeedState.Error -> item {
                ErrorBlock(message = state.message, onRetry = onRetry)
            }
            is FeedState.Ready -> {
                if (state.items.isEmpty()) {
                    item { ErrorBlock(message = "The feed came back empty.", onRetry = onRetry) }
                } else {
                    item {
                        HeroCard(
                            video = state.items.first(),
                            onClick = { onOpenVideo(state.items.first().id) },
                            onChannelClick = { id, name -> onOpenChannel(id, name) }
                        )
                    }
                    items(state.items.drop(1), key = { it.id }) { video ->
                        VideoRow(
                            video = video,
                            onClick = { onOpenVideo(video.id) },
                            onChannelClick = { id, name -> onOpenChannel(id, name) }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "UnliTube 0.9.0 - premium UI redesign",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun SearchScreen(
    repository: YouTubeRepository,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var continuation by remember { mutableStateOf<String?>(null) }
    var submittedQuery by remember { mutableStateOf("") }

    fun runSearch() {
        val trimmed = query.trim()
        if (trimmed.isBlank() || searching) return
        searching = true
        error = null
        submittedQuery = trimmed
        scope.launch {
            runCatching { repository.search(trimmed) }
                .onSuccess { page ->
                    results = page.items
                    continuation = page.continuation
                    if (page.items.isEmpty()) error = "No results for \"$trimmed\"."
                }
                .onFailure { error = it.message ?: "Search failed" }
            searching = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search videos, channels, live") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() })
            )
        }
        item {
            Button(onClick = { runSearch() }, enabled = !searching, modifier = Modifier.fillMaxWidth()) {
                Text(if (searching) "Searching..." else "Search")
            }
        }
        if (searching) {
            item { LoadingBlock("Searching YouTube") }
        }
        error?.let { message ->
            item {
                Text(text = message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
        items(results, key = { it.id }) { video ->
            VideoCard(
                video = video,
                onClick = { onOpenVideo(video.id) },
                onChannelClick = { id, name -> onOpenChannel(id, name) }
            )
        }
        if (continuation != null && results.isNotEmpty()) {
            item {
                Button(
                    onClick = {
                        val token = continuation ?: return@Button
                        loadingMore = true
                        scope.launch {
                            runCatching { repository.search(submittedQuery, token) }
                                .onSuccess { page: VideoPage ->
                                    results = (results + page.items).distinctBy { it.id }
                                    continuation = page.continuation
                                }
                                .onFailure { error = it.message ?: "Could not load more" }
                            loadingMore = false
                        }
                    },
                    enabled = !loadingMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loadingMore) "Loading..." else "Load more results")
                }
            }
        }
    }
}

@Composable
private fun InfoScreen(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = body, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingBlock(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun Header(onSearchTap: () -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(UnliViolet, UnliCoral))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "UnliTube",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Guest",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSearchTap() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Search videos, channels, live",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
@Composable
private fun HeroCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: ((String, String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            startY = 300f
                        )
                    )
            )
            val badge = when {
                video.isLive -> "LIVE"
                !video.durationText.isNullOrBlank() -> video.durationText
                else -> null
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (video.isLive) UnliCoral else Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        video.channel.takeIf { it.isNotBlank() },
                        video.viewCountText,
                        video.publishedText
                    ).joinToString(" - "),
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: ((String, String) -> Unit)? = null
) {
    val channelTap: (() -> Unit)? = if (onChannelClick != null && video.channelId != null) {
        { onChannelClick(video.channelId, video.channel) }
    } else {
        null
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(width = 156.dp, height = 88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val badge = when {
                video.isLive -> "LIVE"
                !video.durationText.isNullOrBlank() -> video.durationText
                else -> null
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (video.isLive) UnliCoral else Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(text = badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.channel,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (channelTap != null) {
                    Modifier.clickable { channelTap() }
                } else {
                    Modifier
                }
            )
            val meta = listOfNotNull(video.viewCountText, video.publishedText)
                .joinToString(" - ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: ((String, String) -> Unit)? = null
) {
    val channelTap: (() -> Unit)? = if (onChannelClick != null && video.channelId != null) {
        { onChannelClick(video.channelId, video.channel) }
    } else {
        null
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val badge = when {
                video.isLive -> "LIVE"
                !video.durationText.isNullOrBlank() -> video.durationText
                else -> null
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (video.isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badge, color = Color.White, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(UnliViolet.copy(alpha = 0.55f))
                    .then(if (channelTap != null) Modifier.clickable { channelTap() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (!video.channelAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.channelAvatarUrl,
                        contentDescription = video.channel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = video.channel.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (video.channel.isNotBlank()) {
                        Text(
                            text = video.channel,
                            fontSize = 12.5.sp,
                            fontWeight = if (channelTap != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (channelTap != null) {
                                Modifier.clickable { channelTap() }
                            } else {
                                Modifier
                            }
                        )
                    }
                    val meta = listOfNotNull(video.viewCountText, video.publishedText)
                        .joinToString(" - ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = if (video.channel.isNotBlank()) " - $meta" else meta,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchScreen(
    repository: YouTubeRepository,
    videoId: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit
) {
    var details by remember(videoId) { mutableStateOf<VideoDetails?>(null) }
    var loadError by remember(videoId) { mutableStateOf<String?>(null) }
    var nonce by remember(videoId) { mutableStateOf(0) }

    LaunchedEffect(videoId, nonce) {
        details = null
        loadError = null
        runCatching { repository.loadDetails(videoId) }
            .onSuccess { details = it }
            .onFailure { loadError = it.message ?: "Could not load this video" }
    }

    val loaded = details
    when {
        loadError != null -> Column(modifier = Modifier.fillMaxSize()) {
            WatchTopBar(onBack)
            ErrorBlock(message = loadError ?: "Could not load this video", onRetry = { nonce++ })
        }
        loaded == null -> Column(modifier = Modifier.fillMaxSize()) {
            WatchTopBar(onBack)
            LoadingBlock("Resolving playable streams")
        }
        else -> WatchContent(
            repository = repository,
            details = loaded,
            onBack = onBack,
            onOpenVideo = onOpenVideo,
            onOpenChannel = onOpenChannel
        )
    }
}

@Composable
private fun WatchTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(text = "Watch", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WatchContent(
    repository: YouTubeRepository,
    details: VideoDetails,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit
) {
    val context = LocalContext.current
    val video = details.video
    val channelId = video.channelId
    val listState = rememberLazyListState()
    val player = remember { PlayerHolder.get(context) }

    var chosenStreamIndex by remember(video.id) { mutableStateOf<Int?>(null) }
    var fallbackMode by remember(video.id) { mutableStateOf(false) }
    var playbackError by remember(video.id) { mutableStateOf<String?>(null) }
    var isPlaying by remember(video.id) { mutableStateOf(false) }
    var isBuffering by remember(video.id) { mutableStateOf(false) }
    var positionMs by remember(video.id) { mutableStateOf(0L) }
    var durationMs by remember(video.id) { mutableStateOf(0L) }
    var sliderDrag by remember(video.id) { mutableStateOf<Float?>(null) }
    var speed by remember(video.id) { mutableStateOf(1.0f) }
    var dialog by remember(video.id) { mutableStateOf("none") }
    var descriptionExpanded by remember(video.id) { mutableStateOf(false) }
    var liked by remember(video.id) { mutableStateOf(false) }
    var watchLater by remember(video.id) { mutableStateOf(false) }
    var downloadStatus by remember(video.id) { mutableStateOf<String?>(null) }
    var sponsorSegments by remember(video.id) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var skippedSegment by remember(video.id) { mutableStateOf<SponsorSegment?>(null) }

    LaunchedEffect(video.id) {
        if (SettingsStore.sponsorBlockEnabled) {
            sponsorSegments = runCatching { SponsorBlockClient.fetch(video.id) }
                .getOrDefault(emptyList())
        }
    }
    var showSaveDialog by remember(video.id) { mutableStateOf(false) }
    var savePlaylists by remember(video.id) { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var newPlaylistName by remember(video.id) { mutableStateOf("") }

    LaunchedEffect(video.id) {
        liked = runCatching { LibraryStore.isLiked(video.id) }.getOrDefault(false)
        watchLater = runCatching { LibraryStore.isWatchLater(video.id) }.getOrDefault(false)
        downloadStatus = runCatching { LibraryStore.download(video.id)?.status }.getOrNull()
    }

    val scope = rememberCoroutineScope()
    var comments by remember(video.id) { mutableStateOf<List<CommentItem>?>(null) }
    var commentsError by remember(video.id) { mutableStateOf<String?>(null) }
    var commentsContinuation by remember(video.id) { mutableStateOf<String?>(null) }
    var loadingMoreComments by remember(video.id) { mutableStateOf(false) }
    var repliesByComment by remember(video.id) { mutableStateOf<Map<String, List<CommentItem>>>(emptyMap()) }
    var expandedReplies by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }
    var loadingReplies by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(video.id) {
        runCatching { repository.loadComments(video.id) }
            .onSuccess { page ->
                comments = page.comments
                commentsContinuation = page.continuation
            }
            .onFailure { commentsError = it.message ?: "Comments failed to load" }
    }

    fun toggleReplies(comment: CommentItem) {
        if (expandedReplies.contains(comment.id)) {
            expandedReplies = expandedReplies - comment.id
            return
        }
        expandedReplies = expandedReplies + comment.id
        val token = comment.repliesToken ?: return
        if (repliesByComment.containsKey(comment.id) || loadingReplies.contains(comment.id)) return
        loadingReplies = loadingReplies + comment.id
        scope.launch {
            runCatching { repository.loadReplies(token) }
                .onSuccess { repliesByComment = repliesByComment + (comment.id to it) }
            loadingReplies = loadingReplies - comment.id
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Playback failed"
            }
        }
        player.exoPlayer.addListener(listener)
        onDispose { player.exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(video.id) {
        listState.scrollToItem(0)
        val alreadyLoaded = PlayerHolder.nowPlaying?.id == video.id && PlayerHolder.hasMedia()
        PlayerHolder.nowPlaying = video
        val savedDownload = runCatching { LibraryStore.download(video.id) }.getOrNull()
        val localOption = if (savedDownload?.status == "completed") {
            DownloadStore.localOption(savedDownload)
        } else {
            null
        }
        if (localOption != null) {
            PlayerHolder.embedOnly = false
            if (!alreadyLoaded) {
                player.play(localOption)
            }
            PlayerHolder.startService(context)
        } else if (details.streams.isEmpty()) {
            fallbackMode = true
            PlayerHolder.embedOnly = true
        } else {
            PlayerHolder.embedOnly = false
            if (!alreadyLoaded) {
                val index = details.defaultStreamIndex.coerceIn(0, details.streams.lastIndex)
                chosenStreamIndex = index
                player.play(details.streams[index])
                val resumeMs = runCatching { LibraryStore.historyPosition(video.id) }
                    .getOrDefault(0L)
                if (resumeMs > 10000L) player.seekTo(resumeMs)
            }
            PlayerHolder.startService(context)
        }
    }

    LaunchedEffect(player, fallbackMode, sponsorSegments) {
        var lastSavedMs = 0L
        var lastSkippedEnd = -1L
        while (true) {
            isPlaying = player.exoPlayer.isPlaying
            isBuffering = player.exoPlayer.playbackState == Player.STATE_BUFFERING
            positionMs = player.exoPlayer.currentPosition.coerceAtLeast(0L)
            val duration = player.exoPlayer.duration
            durationMs = if (duration > 0) duration else 0L
            if (!fallbackMode && positionMs > 0 &&
                kotlin.math.abs(positionMs - lastSavedMs) >= 10000L
            ) {
                lastSavedMs = positionMs
                runCatching { LibraryStore.saveHistory(video, positionMs, durationMs) }
            }
            if (!fallbackMode && SettingsStore.sponsorBlockEnabled) {
                val hit = sponsorSegments.firstOrNull { segment ->
                    positionMs in segment.startMs until (segment.endMs - 500L)
                }
                if (hit != null && hit.endMs != lastSkippedEnd) {
                    lastSkippedEnd = hit.endMs
                    skippedSegment = hit
                    player.seekTo(hit.endMs)
                }
            }
            delay(400)
        }
    }

    fun selectStream(index: Int) {
        val option = details.streams.getOrNull(index) ?: return
        val resumeAt = positionMs
        chosenStreamIndex = index
        playbackError = null
        player.play(option)
        if (resumeAt > 0) player.seekTo(resumeAt)
    }

    if (PlayerHolder.inPip) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (fallbackMode) {
                EmbedFallback(videoId = video.id, modifier = Modifier.fillMaxSize())
            } else {
                PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                if (fallbackMode) {
                    EmbedFallback(videoId = video.id, modifier = Modifier.fillMaxSize())
                } else {
                    PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    IconButton(
                        onClick = { player.togglePlay() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { dialog = "speed" }) {
                                Text("${trimSpeed(speed)}x", color = Color.White)
                            }
                            IconButton(onClick = { dialog = "quality" }) {
                                Icon(
                                    Icons.Filled.HighQuality,
                                    contentDescription = "Quality",
                                    tint = Color.White
                                )
                            }
                        }
                        Slider(
                            value = sliderDrag ?: positionMs.toFloat(),
                            onValueChange = { sliderDrag = it },
                            onValueChangeFinished = {
                                sliderDrag?.let { player.seekTo(it.toLong()) }
                                sliderDrag = null
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val meta = listOfNotNull(video.viewCountText, video.publishedText).joinToString(" - ")
                if (meta.isNotBlank()) {
                    Text(text = meta, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (channelId != null) {
                        Modifier.clickable { onOpenChannel(channelId, video.channel) }
                    } else {
                        Modifier
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(UnliViolet.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = video.channel.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = video.channel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (channelId != null) {
                        Text(
                            text = "View channel",
                            fontSize = 12.5.sp,
                            color = UnliViolet,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${video.id}")
                        }
                        context.startActivity(Intent.createChooser(share, "Share video"))
                    }) { Text("Share") }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}"))
                        )
                    }) { Text("Open in YouTube") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(
                        label = if (liked) "Liked" else "Like",
                        icon = Icons.Filled.Favorite,
                        active = liked,
                        onClick = {
                            scope.launch { liked = LibraryStore.toggleLiked(video) }
                        }
                    )
                    ActionChip(
                        label = if (watchLater) "Saved" else "Watch Later",
                        icon = Icons.Filled.Bookmark,
                        active = watchLater,
                        onClick = {
                            scope.launch { watchLater = LibraryStore.toggleWatchLater(video) }
                        }
                    )
                    ActionChip(
                        label = "Save",
                        icon = Icons.Filled.Bookmark,
                        active = false,
                        onClick = {
                            scope.launch {
                                savePlaylists = LibraryStore.playlists()
                                showSaveDialog = true
                            }
                        }
                    )
                    ActionChip(
                        label = when (downloadStatus) {
                            "completed" -> "Downloaded"
                            "downloading" -> "Downloading"
                            "failed" -> "Retry download"
                            else -> "Download"
                        },
                        icon = Icons.Filled.Download,
                        active = downloadStatus == "completed",
                        onClick = {
                            scope.launch {
                                when (downloadStatus) {
                                    "completed" -> {
                                        DownloadStore.deleteDownload(context, video.id)
                                        downloadStatus = null
                                    }
                                    "downloading" -> Unit
                                    else -> {
                                        downloadStatus = "downloading"
                                        DownloadStore.startDownload(context, details)
                                    }
                                }
                            }
                        }
                    )
                }
                if (fallbackMode) {
                    Text(
                        text = "Fallback Mode: the official embed player is showing this video.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                playbackError?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    if (!fallbackMode) {
                        Button(onClick = {
                            fallbackMode = true
                            PlayerHolder.embedOnly = true
                            player.setPlaying(false)
                        }) {
                            Text("Use fallback player")
                        }
                    }
                }
                val skipped = skippedSegment
                if (skipped != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Skipped a ${skipped.category} segment.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            player.seekTo(skipped.startMs)
                            skippedSegment = null
                        }) { Text("Undo") }
                    }
                }
                if (details.description.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { descriptionExpanded = !descriptionExpanded }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Description",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = details.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3
                        )
                        Text(
                            text = if (descriptionExpanded) "Show less" else "Show more",
                            fontSize = 12.5.sp,
                            color = UnliViolet,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (!SettingsStore.hideComments) {
            item {
                Text(
                    text = "Comments",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        val commentList = comments
        if (!SettingsStore.hideComments) when {
            commentsError != null -> item {
                Text(
                    text = commentsError ?: "Comments failed to load",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            commentList == null -> item { LoadingBlock("Loading comments") }
            commentList.isEmpty() -> item {
                Text(
                    text = "No comments to show for this video.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            else -> {
                items(commentList, key = { "comment-${it.id}" }) { comment ->
                    CommentRow(
                        comment = comment,
                        replies = repliesByComment[comment.id],
                        repliesExpanded = expandedReplies.contains(comment.id),
                        repliesLoading = loadingReplies.contains(comment.id),
                        onToggleReplies = { toggleReplies(comment) }
                    )
                }
                if (commentsContinuation != null) {
                    item {
                        Button(
                            onClick = {
                                val token = commentsContinuation ?: return@Button
                                loadingMoreComments = true
                                scope.launch {
                                    runCatching { repository.loadMoreComments(token) }
                                        .onSuccess { page ->
                                            comments = ((comments ?: emptyList()) + page.comments)
                                                .distinctBy { it.id }
                                            commentsContinuation = page.continuation
                                        }
                                        .onFailure {
                                            commentsError = it.message ?: "Could not load more comments"
                                        }
                                    loadingMoreComments = false
                                }
                            },
                            enabled = !loadingMoreComments,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(if (loadingMoreComments) "Loading..." else "Load more comments")
                        }
                    }
                }
            }
        }
        if (!SettingsStore.hideRelated && details.related.isNotEmpty()) {
            item {
                Text(
                    text = "Up next",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(details.related, key = { "related-${it.id}" }) { related ->
                VideoCard(
                    video = related,
                    onClick = { onOpenVideo(related.id) },
                    onChannelClick = { id, name -> onOpenChannel(id, name) }
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save to playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (savePlaylists.isEmpty()) {
                        Text(
                            text = "No playlists yet. Create one below, then save again.",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        savePlaylists.forEach { playlist ->
                            Text(
                                text = playlist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            LibraryStore.addToPlaylist(playlist.playlistId, video)
                                            showSaveDialog = false
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (newPlaylistName.isNotBlank()) {
                        Button(onClick = {
                            scope.launch {
                                val id = LibraryStore.createPlaylist(newPlaylistName.trim())
                                LibraryStore.addToPlaylist(id, video)
                                newPlaylistName = ""
                                showSaveDialog = false
                            }
                        }) { Text("Create and save") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Close") }
            }
        )
    }

    if (dialog == "quality") {
        AlertDialog(
            onDismissRequest = { dialog = "none" },
            title = { Text("Quality") },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(details.streams.indices.toList()) { index ->
                        val option = details.streams[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectStream(index)
                                    dialog = "none"
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = if (index == chosenStreamIndex) "${option.label} (current)" else option.label,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = option.detail,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = "none" }) { Text("Close") }
            }
        )
    }

    if (dialog == "speed") {
        AlertDialog(
            onDismissRequest = { dialog = "none" },
            title = { Text("Playback speed") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)) { value ->
                        Text(
                            text = if (value == speed) "${trimSpeed(value)}x (current)" else "${trimSpeed(value)}x",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    speed = value
                                    player.setSpeed(value)
                                    dialog = "none"
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = "none" }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun EmbedFallback(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0")
            }
        },
        update = { webView ->
            val target = "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
            if (webView.url != target) webView.loadUrl(target)
        },
        modifier = modifier
    )
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun trimSpeed(speed: Float): String =
    if (speed % 1.0f == 0.0f) speed.toInt().toString() else speed.toString()

@Composable
private fun CommentRow(
    comment: CommentItem,
    replies: List<CommentItem>?,
    repliesExpanded: Boolean,
    repliesLoading: Boolean,
    onToggleReplies: () -> Unit,
    isReply: Boolean = false
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(UnliViolet.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                if (!comment.authorAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = comment.authorAvatarUrl,
                        contentDescription = comment.author,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = comment.author.removePrefix("@").take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                val authorLine = listOfNotNull(
                    comment.author.takeIf { it.isNotBlank() },
                    comment.publishedText,
                    if (comment.isPinned) "Pinned" else null
                ).joinToString(" - ")
                Text(
                    text = authorLine,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = comment.text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val meta = listOfNotNull(
                    comment.likeCountText?.let { "$it likes" }
                ).joinToString(" - ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isReply && comment.replyCount > 0) {
                    TextButton(onClick = onToggleReplies) {
                        Text(
                            when {
                                repliesLoading -> "Loading replies..."
                                repliesExpanded -> "Hide replies"
                                else -> "View ${comment.replyCount} replies"
                            }
                        )
                    }
                }
                if (!isReply && repliesExpanded && replies != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        replies.forEach { reply ->
                            CommentRow(
                                comment = reply,
                                replies = null,
                                repliesExpanded = false,
                                repliesLoading = false,
                                onToggleReplies = {},
                                isReply = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelScreen(
    repository: YouTubeRepository,
    channelId: String,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    var page by remember(channelId) { mutableStateOf<ChannelPage?>(null) }
    var loadError by remember(channelId) { mutableStateOf<String?>(null) }
    var nonce by remember(channelId) { mutableStateOf(0) }
    var descriptionExpanded by remember(channelId) { mutableStateOf(false) }
    var subscribed by remember(channelId) { mutableStateOf(false) }
    val channelScope = rememberCoroutineScope()

    LaunchedEffect(channelId, nonce) {
        page = null
        loadError = null
        runCatching { repository.loadChannel(channelId) }
            .onSuccess { page = it }
            .onFailure { loadError = it.message ?: "Could not load this channel" }
    }

    LaunchedEffect(channelId) {
        subscribed = runCatching { LibraryStore.isSubscribed(channelId) }.getOrDefault(false)
    }

    val loaded = page
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(Brush.linearGradient(listOf(UnliViolet, UnliCoral)))
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(UnliViolet.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatar = loaded?.info?.avatarUrl
                            if (!avatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = loaded?.info?.name ?: fallbackName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = (loaded?.info?.name ?: fallbackName).take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = loaded?.info?.name ?: fallbackName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            loaded?.info?.subtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    val description = loaded?.info?.description.orEmpty()
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            channelScope.launch {
                                val info = loaded?.info
                                subscribed = LibraryStore.toggleSubscribe(
                                    channelId,
                                    info?.name ?: fallbackName,
                                    info?.avatarUrl
                                )
                            }
                        }) {
                            Text(if (subscribed) "Subscribed" else "Subscribe")
                        }
                        Text(
                            text = "Saved locally on this phone.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        when {
            loadError != null -> item {
                ErrorBlock(message = loadError ?: "Could not load this channel", onRetry = { nonce++ })
            }
            loaded == null -> item { LoadingBlock("Loading channel") }
            loaded.videos.isEmpty() -> item {
                Text(
                    text = "No videos found on this channel page.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            else -> {
                item {
                    Text(
                        text = "Videos",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                items(loaded.videos, key = { "channel-${it.id}" }) { video ->
                    VideoCard(video = video, onClick = { onOpenVideo(video.id) })
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(onOpen: () -> Unit, onClose: () -> Unit) {
    val video = PlayerHolder.nowPlaying ?: return
    val context = LocalContext.current
    var positionMs by remember(video.id) { mutableStateOf(0L) }
    var durationMs by remember(video.id) { mutableStateOf(0L) }

    LaunchedEffect(video.id) {
        while (true) {
            val exo = PlayerHolder.peek()?.exoPlayer
            if (exo != null) {
                positionMs = exo.currentPosition.coerceAtLeast(0L)
                val duration = exo.duration
                durationMs = if (duration > 0) duration else 0L
            }
            delay(600)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp
    ) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = video.channel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = { PlayerHolder.get(context).togglePlay() }) {
                Icon(
                    imageVector = if (PlayerHolder.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (PlayerHolder.isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close player")
            }
        }
        val fraction = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = UnliViolet,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouScreen() {
    val youContext = LocalContext.current
    val youScope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<String?>(null) }
    var manualUpdate by remember { mutableStateOf<RemoteVersion?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(text = "You", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Guest Mode is active. Everything public works with no sign in, " +
                        "and nothing about your watching leaves this phone. Likes, " +
                        "subscriptions and playlists are saved locally in the Library.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Appearance", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            "dark" to "Dark",
                            "amoled" to "AMOLED black",
                            "light" to "Light",
                            "system" to "System"
                        )
                    ) { (value, label) ->
                        FilterChip(
                            selected = SettingsStore.themeMode == value,
                            onClick = { SettingsStore.updateThemeMode(value) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Playback", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Lite Mode", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Lighter home feed and capped default quality. Made for 3GB RAM phones and slow data.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = SettingsStore.liteMode,
                        onCheckedChange = { SettingsStore.updateLiteMode(it) }
                    )
                }
                Text(
                    text = "Default quality",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            0 to "Auto",
                            360 to "360p",
                            480 to "480p",
                            720 to "720p",
                            1080 to "1080p"
                        )
                    ) { (cap, label) ->
                        FilterChip(
                            selected = SettingsStore.qualityCap == cap,
                            onClick = { SettingsStore.updateQualityCap(cap) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Distraction Shield", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Hide parts of the app that pull you in. These are local settings only.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ShieldRow(
                    label = "Hide Shorts",
                    description = "Removes the Shorts folder from the home feed.",
                    checked = SettingsStore.hideShorts,
                    onChange = { SettingsStore.updateHideShorts(it) }
                )
                ShieldRow(
                    label = "Hide comments",
                    description = "Hides the comments section on watch pages.",
                    checked = SettingsStore.hideComments,
                    onChange = { SettingsStore.updateHideComments(it) }
                )
                ShieldRow(
                    label = "Hide Up next",
                    description = "Hides related videos on watch pages.",
                    checked = SettingsStore.hideRelated,
                    onChange = { SettingsStore.updateHideRelated(it) }
                )
                ShieldRow(
                    label = "Search-only mode",
                    description = "Home shows only the search box, no feed at all.",
                    checked = SettingsStore.searchOnlyMode,
                    onChange = { SettingsStore.updateSearchOnlyMode(it) }
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "SponsorBlock", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                ShieldRow(
                    label = "Skip sponsor segments",
                    description = "Skips community-marked sponsor, intro, outro and self-promo segments, with an Undo button when a skip happens.",
                    checked = SettingsStore.sponsorBlockEnabled,
                    onChange = { SettingsStore.updateSponsorBlockEnabled(it) }
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Updates", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        checkingUpdate = true
                        updateResult = null
                        manualUpdate = null
                        youScope.launch {
                            val remote = runCatching { UpdateCheck.fetch() }.getOrNull()
                            checkingUpdate = false
                            if (remote == null) {
                                updateResult = "Could not reach the update server."
                            } else if (remote.versionCode > BuildConfig.VERSION_CODE) {
                                manualUpdate = remote
                                updateResult = "Version ${remote.versionName} is available."
                            } else {
                                updateResult = "You are on the latest version."
                            }
                        }
                    },
                    enabled = !checkingUpdate
                ) {
                    Text(if (checkingUpdate) "Checking..." else "Check for updates")
                }
                updateResult?.let { result ->
                    Text(
                        text = result,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val availableManual = manualUpdate
                if (availableManual != null) {
                    if (availableManual.notes.isNotBlank()) {
                        Text(
                            text = availableManual.notes,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (availableManual.apkUrl.isNotBlank()) {
                        Button(onClick = {
                            youContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(availableManual.apkUrl))
                            )
                        }) { Text("Download update") }
                    } else {
                        Text(
                            text = "Get it at unlitube.vercel.app",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "UnliTube 0.9.0 - UI polish",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ShieldRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

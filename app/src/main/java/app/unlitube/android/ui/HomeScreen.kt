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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import app.unlitube.android.data.VideoDetails
import app.unlitube.android.data.VideoItem
import app.unlitube.android.data.VideoPage
import app.unlitube.android.data.YouTubeRepository
import app.unlitube.android.player.PlayerSurface
import app.unlitube.android.player.UnliPlayer
import app.unlitube.android.ui.theme.UnliCoral
import app.unlitube.android.ui.theme.UnliViolet
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Phase 1: live data from Engine A (InnerTube calls made on the phone, see
// data/YouTubeClient.kt). Home, search, watch, quality picker and related
// videos are real. Library, downloads and account actions land in Phase 2.

private val feedFolders = listOf("All", "Study", "Music", "Gaming", "News", "Live", "Shorts")

private sealed interface FeedState {
    data object Loading : FeedState
    data class Error(val message: String) : FeedState
    data class Ready(val items: List<VideoItem>) : FeedState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val repository = remember { YouTubeRepository() }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedFolder by remember { mutableStateOf("All") }
    var feedState by remember { mutableStateOf<FeedState>(FeedState.Loading) }
    var feedNonce by remember { mutableStateOf(0) }
    var openedVideoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedFolder, feedNonce) {
        feedState = FeedState.Loading
        feedState = runCatching { repository.loadHome(selectedFolder) }
            .fold(
                onSuccess = { FeedState.Ready(it) },
                onFailure = { FeedState.Error(it.message ?: "Could not load the feed") }
            )
    }

    BackHandler(enabled = openedVideoId != null) { openedVideoId = null }

    Scaffold(
        bottomBar = {
            if (openedVideoId == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.Subscriptions, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "You") },
                        label = { Text("You") }
                    )
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
            if (videoId != null) {
                WatchScreen(
                    repository = repository,
                    videoId = videoId,
                    onBack = { openedVideoId = null },
                    onOpenVideo = { openedVideoId = it }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeFeed(
                        feedState = feedState,
                        selectedFolder = selectedFolder,
                        onFolderSelected = { selectedFolder = it },
                        onRetry = { feedNonce++ },
                        onSearchTap = { selectedTab = 1 },
                        onOpenVideo = { openedVideoId = it }
                    )
                    1 -> SearchScreen(
                        repository = repository,
                        onOpenVideo = { openedVideoId = it }
                    )
                    2 -> InfoScreen(
                        title = "Library",
                        body = "Downloads, local playlists, history and Custom Feeds arrive in Phase 2. Your feed, search and playback are already live."
                    )
                    else -> InfoScreen(
                        title = "You",
                        body = "You are in Guest Mode. Everything public works with no sign in, and nothing about your watching leaves this phone. Optional Signed In Mode arrives later in Phase 1."
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeed(
    feedState: FeedState,
    selectedFolder: String,
    onFolderSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onSearchTap: () -> Unit,
    onOpenVideo: (String) -> Unit
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
                items(feedFolders) { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onFolderSelected(folder) },
                        label = { Text(folder) }
                    )
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
                    items(state.items, key = { it.id }) { video ->
                        VideoCard(video = video, onClick = { onOpenVideo(video.id) })
                    }
                }
            }
        }
        item {
            Text(
                text = "UnliTube 0.2.0 - live feed and playback, Phase 1",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun SearchScreen(repository: YouTubeRepository, onOpenVideo: (String) -> Unit) {
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
            VideoCard(video = video, onClick = { onOpenVideo(video.id) })
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
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(UnliViolet, UnliCoral))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "UnliTube",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
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
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
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
                    .background(UnliViolet.copy(alpha = 0.55f)),
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
                val meta = listOfNotNull(
                    video.channel.takeIf { it.isNotBlank() },
                    video.viewCountText,
                    video.publishedText
                ).joinToString(" - ")
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
}

@Composable
private fun WatchScreen(
    repository: YouTubeRepository,
    videoId: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit
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
        else -> WatchContent(details = loaded, onBack = onBack, onOpenVideo = onOpenVideo)
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
    details: VideoDetails,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val video = details.video
    val listState = rememberLazyListState()
    val player = remember(video.id) { UnliPlayer(context) }

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

    DisposableEffect(player) {
        onDispose { player.release() }
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
        if (details.streams.isEmpty()) {
            fallbackMode = true
        } else {
            val index = details.defaultStreamIndex.coerceIn(0, details.streams.lastIndex)
            chosenStreamIndex = index
            player.play(details.streams[index])
        }
    }

    LaunchedEffect(player, fallbackMode) {
        while (true) {
            isPlaying = player.exoPlayer.isPlaying
            isBuffering = player.exoPlayer.playbackState == Player.STATE_BUFFERING
            positionMs = player.exoPlayer.currentPosition.coerceAtLeast(0L)
            val duration = player.exoPlayer.duration
            durationMs = if (duration > 0) duration else 0L
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Button(onClick = { fallbackMode = true }) {
                            Text("Use fallback player")
                        }
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

        if (details.related.isNotEmpty()) {
            item {
                Text(
                    text = "Up next",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(details.related, key = { "related-${it.id}" }) { related ->
                VideoCard(video = related, onClick = { onOpenVideo(related.id) })
            }
        }
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

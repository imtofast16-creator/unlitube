package app.unlitube.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlitube.android.data.DownloadEntity
import app.unlitube.android.data.DownloadStore
import app.unlitube.android.data.HistoryEntity
import app.unlitube.android.data.LibraryStore
import app.unlitube.android.data.LikedEntity
import app.unlitube.android.data.PlaylistEntity
import app.unlitube.android.data.PlaylistItemEntity
import app.unlitube.android.data.SubscriptionEntity
import app.unlitube.android.data.VideoItem
import app.unlitube.android.data.WatchLaterEntity
import app.unlitube.android.data.YouTubeRepository
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Slice 5 Library tab. Every section is local Room data except the
 * "Latest from your subscriptions" feed, which loads each subscribed
 * channel's public videos live through Engine A.
 */
@Composable
fun LibraryScreen(
    repository: YouTubeRepository,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var nonce by remember { mutableStateOf(0) }
    var subscriptions by remember { mutableStateOf<List<SubscriptionEntity>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryEntity>>(emptyList()) }
    var liked by remember { mutableStateOf<List<LikedEntity>>(emptyList()) }
    var watchLater by remember { mutableStateOf<List<WatchLaterEntity>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<DownloadEntity>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var subFeed by remember { mutableStateOf<List<VideoItem>?>(null) }
    var expandedPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistItems by remember { mutableStateOf<List<PlaylistItemEntity>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            subscriptions = LibraryStore.subscriptions()
            history = LibraryStore.history()
            liked = LibraryStore.liked()
            watchLater = LibraryStore.watchLater()
            downloads = LibraryStore.downloads()
            playlists = LibraryStore.playlists()
            loaded = true
        }
    }

    LaunchedEffect(nonce) { reload() }

    LaunchedEffect(subscriptions) {
        if (subscriptions.isEmpty()) {
            subFeed = emptyList()
            return@LaunchedEffect
        }
        subFeed = null
        scope.launch {
            val pages = subscriptions.map { sub ->
                runCatching { repository.loadChannel(sub.channelId).videos.take(3) }
                    .getOrDefault(emptyList())
            }
            val merged = mutableListOf<VideoItem>()
            val max = pages.maxOfOrNull { it.size } ?: 0
            for (index in 0 until max) {
                for (page in pages) page.getOrNull(index)?.let(merged::add)
            }
            subFeed = merged.distinctBy { it.id }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { nonce++ }) { Text("Refresh") }
            }
            Text(
                text = "Saved on this phone only. Nothing here is a YouTube account action.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!loaded) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        item {
            SectionTitle("Subscriptions (${subscriptions.size})")
            if (subscriptions.isEmpty()) {
                Text(
                    text = "No subscriptions yet. Open a channel and tap Subscribe.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(subscriptions, key = { "sub-${it.channelId}" }) { sub ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChannel(sub.channelId, sub.name) }
            ) {
                LibraryAvatar(url = sub.avatarUrl, label = sub.name)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = sub.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    scope.launch {
                        LibraryStore.toggleSubscribe(sub.channelId, sub.name, sub.avatarUrl)
                        reload()
                    }
                }) { Text("Unsubscribe") }
            }
        }

        item { SectionTitle("Latest from your subscriptions") }
        val feed = subFeed
        when {
            subscriptions.isEmpty() -> item {
                Text(
                    text = "Subscribe to a channel to see its latest videos here.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            feed == null -> item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            feed.isEmpty() -> item {
                Text(
                    text = "No recent videos found for your subscriptions.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> items(feed, key = { "feed-${it.id}" }) { video ->
                LibraryVideoRow(
                    title = video.title,
                    channel = video.channel,
                    thumbnailUrl = video.thumbnailUrl,
                    onClick = { onOpenVideo(video.id) }
                )
            }
        }

        item { SectionTitle("History (${history.size})") }
        if (history.isEmpty()) {
            item {
                Text(
                    text = "Videos you watch appear here, with your position saved so you can resume.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history, key = { "hist-${it.videoId}" }) { entry ->
                LibraryVideoRow(
                    title = entry.title,
                    channel = entry.channel,
                    thumbnailUrl = entry.thumbnailUrl,
                    extra = if (entry.durationMs > 0) {
                        "Resume at ${formatLibraryTime(entry.positionMs)} of ${formatLibraryTime(entry.durationMs)}"
                    } else {
                        null
                    },
                    onClick = { onOpenVideo(entry.videoId) },
                    onRemove = {
                        scope.launch {
                            LibraryStore.removeHistory(entry.videoId)
                            reload()
                        }
                    }
                )
            }
        }

        item { SectionTitle("Liked (${liked.size})") }
        if (liked.isEmpty()) {
            item {
                Text(
                    text = "Tap Like on a video to keep it here.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(liked, key = { "liked-${it.videoId}" }) { entry ->
                LibraryVideoRow(
                    title = entry.title,
                    channel = entry.channel,
                    thumbnailUrl = entry.thumbnailUrl,
                    onClick = { onOpenVideo(entry.videoId) }
                )
            }
        }

        item { SectionTitle("Watch Later (${watchLater.size})") }
        if (watchLater.isEmpty()) {
            item {
                Text(
                    text = "Tap Watch Later on a video to queue it here.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(watchLater, key = { "wl-${it.videoId}" }) { entry ->
                LibraryVideoRow(
                    title = entry.title,
                    channel = entry.channel,
                    thumbnailUrl = entry.thumbnailUrl,
                    onClick = { onOpenVideo(entry.videoId) }
                )
            }
        }

        item { SectionTitle("Downloads (${downloads.size})") }
        if (downloads.isEmpty()) {
            item {
                Text(
                    text = "Tap Download on a video to save it on this phone and watch it offline.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(downloads, key = { "dl-${it.videoId}" }) { entry ->
                val liveProgress = DownloadStore.progress[entry.videoId]
                val statusText = when (entry.status) {
                    "completed" -> "Downloaded, plays offline"
                    "downloading" -> "Downloading ${liveProgress ?: entry.progress}%"
                    "failed" -> "Download failed, retry from the watch page"
                    else -> entry.status
                }
                LibraryVideoRow(
                    title = entry.title,
                    channel = entry.channel,
                    thumbnailUrl = entry.thumbnailUrl,
                    extra = statusText,
                    onClick = { if (entry.status == "completed") onOpenVideo(entry.videoId) },
                    onRemove = {
                        scope.launch {
                            DownloadStore.deleteDownload(context, entry.videoId)
                            reload()
                        }
                    }
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Playlists (${playlists.size})", modifier = Modifier.weight(1f))
                Button(onClick = { showCreateDialog = true }) { Text("New") }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    text = "Create a playlist here, then save videos to it from any watch page.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(playlists, key = { "pl-${it.playlistId}" }) { playlist ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = playlist.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val next = if (expandedPlaylist == playlist.playlistId) null else playlist.playlistId
                                    expandedPlaylist = next
                                    if (next != null) {
                                        scope.launch {
                                            playlistItems = LibraryStore.playlistItems(next)
                                        }
                                    }
                                }
                        )
                        TextButton(onClick = {
                            scope.launch {
                                LibraryStore.deletePlaylist(playlist.playlistId)
                                if (expandedPlaylist == playlist.playlistId) expandedPlaylist = null
                                reload()
                            }
                        }) { Text("Delete") }
                    }
                    if (expandedPlaylist == playlist.playlistId) {
                        if (playlistItems.isEmpty()) {
                            Text(
                                text = "Empty playlist.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            playlistItems.forEach { entry ->
                                LibraryVideoRow(
                                    title = entry.title,
                                    channel = entry.channel,
                                    thumbnailUrl = entry.thumbnailUrl,
                                    onClick = { onOpenVideo(entry.videoId) },
                                    onRemove = {
                                        scope.launch {
                                            LibraryStore.removeFromPlaylist(playlist.playlistId, entry.videoId)
                                            playlistItems = LibraryStore.playlistItems(playlist.playlistId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            LibraryStore.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreateDialog = false
                            reload()
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
private fun LibraryAvatar(url: String?, label: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(text = label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryVideoRow(
    title: String,
    channel: String,
    thumbnailUrl: String,
    extra: String? = null,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(text = channel, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!extra.isNullOrBlank()) {
                Text(text = extra, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

internal fun formatLibraryTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

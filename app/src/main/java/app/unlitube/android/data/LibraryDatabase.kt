package app.unlitube.android.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Slice 5 local library (PLAN.md Section 7). Everything here lives in a
 * Room database on the phone only. No account, no server, no faked actions:
 * a subscription, like, history entry or playlist exists only because the
 * user tapped it in this app.
 */

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Long
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String,
    val channelId: String?,
    val thumbnailUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long
)

@Entity(tableName = "liked")
data class LikedEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String,
    val channelId: String?,
    val thumbnailUrl: String,
    val likedAt: Long
)

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String,
    val channelId: String?,
    val thumbnailUrl: String,
    val addedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Int = 0,
    val playlistId: String,
    val videoId: String,
    val title: String,
    val channel: String,
    val channelId: String?,
    val thumbnailUrl: String,
    val addedAt: Long
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    suspend fun subscriptions(): List<SubscriptionEntity>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE channelId = :channelId")
    suspend fun subscriptionCount(channelId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(item: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelId = :channelId")
    suspend fun deleteSubscription(channelId: String)

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 50")
    suspend fun history(): List<HistoryEntity>

    @Query("SELECT positionMs FROM history WHERE videoId = :videoId")
    suspend fun historyPosition(videoId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun deleteHistory(videoId: String)

    @Query("SELECT * FROM liked ORDER BY likedAt DESC")
    suspend fun liked(): List<LikedEntity>

    @Query("SELECT COUNT(*) FROM liked WHERE videoId = :videoId")
    suspend fun likedCount(videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiked(item: LikedEntity)

    @Query("DELETE FROM liked WHERE videoId = :videoId")
    suspend fun deleteLiked(videoId: String)

    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    suspend fun watchLater(): List<WatchLaterEntity>

    @Query("SELECT COUNT(*) FROM watch_later WHERE videoId = :videoId")
    suspend fun watchLaterCount(videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLater(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :videoId")
    suspend fun deleteWatchLater(videoId: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun playlists(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(item: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItems(playlistId: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    suspend fun playlistItems(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun playlistItemCount(playlistId: String, videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deletePlaylistItem(playlistId: String, videoId: String)
}

@Database(
    entities = [
        SubscriptionEntity::class,
        HistoryEntity::class,
        LikedEntity::class,
        WatchLaterEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UnliTubeDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}

object LibraryStore {
    private lateinit var dao: LibraryDao
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        dao = Room.databaseBuilder(
            context.applicationContext,
            UnliTubeDatabase::class.java,
            "unlitube_library.db"
        ).fallbackToDestructiveMigration().build().libraryDao()
    }

    suspend fun isSubscribed(channelId: String): Boolean =
        dao.subscriptionCount(channelId) > 0

    suspend fun toggleSubscribe(channelId: String, name: String, avatarUrl: String?): Boolean {
        return if (isSubscribed(channelId)) {
            dao.deleteSubscription(channelId)
            false
        } else {
            dao.insertSubscription(
                SubscriptionEntity(channelId, name, avatarUrl, System.currentTimeMillis())
            )
            true
        }
    }

    suspend fun subscriptions(): List<SubscriptionEntity> = dao.subscriptions()

    suspend fun saveHistory(video: VideoItem, positionMs: Long, durationMs: Long) {
        dao.insertHistory(
            HistoryEntity(
                videoId = video.id,
                title = video.title,
                channel = video.channel,
                channelId = video.channelId,
                thumbnailUrl = video.thumbnailUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun history(): List<HistoryEntity> = dao.history()

    suspend fun historyPosition(videoId: String): Long =
        dao.historyPosition(videoId) ?: 0L

    suspend fun removeHistory(videoId: String) {
        dao.deleteHistory(videoId)
    }

    suspend fun isLiked(videoId: String): Boolean = dao.likedCount(videoId) > 0

    suspend fun toggleLiked(video: VideoItem): Boolean {
        return if (isLiked(video.id)) {
            dao.deleteLiked(video.id)
            false
        } else {
            dao.insertLiked(
                LikedEntity(video.id, video.title, video.channel, video.channelId, video.thumbnailUrl, System.currentTimeMillis())
            )
            true
        }
    }

    suspend fun liked(): List<LikedEntity> = dao.liked()

    suspend fun isWatchLater(videoId: String): Boolean = dao.watchLaterCount(videoId) > 0

    suspend fun toggleWatchLater(video: VideoItem): Boolean {
        return if (isWatchLater(video.id)) {
            dao.deleteWatchLater(video.id)
            false
        } else {
            dao.insertWatchLater(
                WatchLaterEntity(video.id, video.title, video.channel, video.channelId, video.thumbnailUrl, System.currentTimeMillis())
            )
            true
        }
    }

    suspend fun watchLater(): List<WatchLaterEntity> = dao.watchLater()

    suspend fun playlists(): List<PlaylistEntity> = dao.playlists()

    suspend fun createPlaylist(name: String): String {
        val id = "pl_${System.currentTimeMillis()}"
        dao.insertPlaylist(PlaylistEntity(id, name, System.currentTimeMillis()))
        return id
    }

    suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylistItems(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun playlistItems(playlistId: String): List<PlaylistItemEntity> =
        dao.playlistItems(playlistId)

    suspend fun addToPlaylist(playlistId: String, video: VideoItem) {
        if (dao.playlistItemCount(playlistId, video.id) == 0) {
            dao.insertPlaylistItem(
                PlaylistItemEntity(
                    playlistId = playlistId,
                    videoId = video.id,
                    title = video.title,
                    channel = video.channel,
                    channelId = video.channelId,
                    thumbnailUrl = video.thumbnailUrl,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String) {
        dao.deletePlaylistItem(playlistId, videoId)
    }
}

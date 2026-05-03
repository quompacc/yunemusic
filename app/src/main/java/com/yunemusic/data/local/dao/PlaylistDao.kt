package com.yunemusic.data.local.dao

import androidx.room.*
import com.yunemusic.data.local.entities.PlaylistEntity
import com.yunemusic.data.local.entities.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int
)

@Dao
interface PlaylistDao {

    @Query("""
        SELECT p.id, p.name, p.createdAt, COUNT(pt.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
    """)
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>
}

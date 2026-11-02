package fr.vpm.yag.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbumWithPhotos(albumId: String): AlbumWithPhotos?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

package fr.vpm.yag.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE albumId = :albumId")
    suspend fun getPhotosByAlbum(albumId: String): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE albumId = :albumId")
    suspend fun deletePhotosByAlbum(albumId: String)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}

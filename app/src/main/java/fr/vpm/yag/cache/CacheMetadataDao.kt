package fr.vpm.yag.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheMetadataDao {

    @Query("SELECT value FROM cache_metadata WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(metadata: CacheMetadataEntity)

    @Query("DELETE FROM cache_metadata")
    suspend fun deleteAll()
}

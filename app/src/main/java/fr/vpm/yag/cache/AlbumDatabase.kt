package fr.vpm.yag.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlbumEntity::class, PhotoEntity::class, CacheMetadataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AlbumDatabase : RoomDatabase() {

    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao
    abstract fun cacheMetadataDao(): CacheMetadataDao

    companion object {
        const val DATABASE_NAME = "album_cache.db"
        const val CACHE_VERSION_KEY = "cache_version"
        const val LAST_REFRESH_KEY = "last_refresh_timestamp"
        const val CURRENT_CACHE_VERSION = "1"

        @Volatile
        private var instance: AlbumDatabase? = null

        fun getInstance(context: Context): AlbumDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlbumDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}

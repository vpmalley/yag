package fr.vpm.yag.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlbumEntity::class, PhotoEntity::class, CacheMetadataEntity::class],
    version = 2,
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
        const val CURRENT_CACHE_VERSION = "2"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN dateTaken INTEGER DEFAULT NULL")
            }
        }

        @Volatile
        private var instance: AlbumDatabase? = null

        fun getInstance(context: Context): AlbumDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlbumDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}

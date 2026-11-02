package fr.vpm.yag.cache

import android.util.Log
import fr.vpm.yag.device.model.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlbumCacheRepository(private val database: AlbumDatabase) {

    companion object {
        private const val TAG = "AlbumCacheRepository"
    }

    suspend fun loadCachedAlbums(): List<Album>? = withContext(Dispatchers.IO) {
        try {
            val version = database.cacheMetadataDao().getValue(AlbumDatabase.CACHE_VERSION_KEY)
            if (version != null && version != AlbumDatabase.CURRENT_CACHE_VERSION) {
                Log.w(TAG, "Cache version mismatch (stored=$version, current=${AlbumDatabase.CURRENT_CACHE_VERSION}), clearing cache")
                clearCache()
                return@withContext null
            }
            val albumEntities = database.albumDao().getAllAlbums()
            if (albumEntities.isEmpty()) return@withContext null
            albumEntities.map { albumEntity ->
                val photos = database.photoDao().getPhotosByAlbum(albumEntity.id)
                AlbumWithPhotos(albumEntity, photos).toAlbum()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached albums, falling back to MediaStore", e)
            null
        }
    }

    suspend fun saveAlbumsToCache(albums: Collection<Album>) = withContext(Dispatchers.IO) {
        try {
            database.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    database.albumDao().deleteAll()
                    database.photoDao().deleteAll()
                    val albumEntities = albums.map { it.toEntity() }
                    database.albumDao().insertAlbums(albumEntities)
                    val photoEntities = albums.flatMap { album ->
                        album.photos.filterIsInstance<fr.vpm.yag.device.model.DevicePhoto>()
                            .map { it.toEntity(album.id ?: "") }
                    }
                    if (photoEntities.isNotEmpty()) {
                        database.albumDao().insertPhotos(photoEntities)
                    }
                    database.cacheMetadataDao().setValue(
                        CacheMetadataEntity(
                            AlbumDatabase.CACHE_VERSION_KEY,
                            AlbumDatabase.CURRENT_CACHE_VERSION
                        )
                    )
                    database.cacheMetadataDao().setValue(
                        CacheMetadataEntity(
                            AlbumDatabase.LAST_REFRESH_KEY,
                            System.currentTimeMillis().toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save albums to cache", e)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            database.albumDao().deleteAll()
            database.photoDao().deleteAll()
            database.cacheMetadataDao().deleteAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    suspend fun getLastRefreshTimestamp(): Long? = withContext(Dispatchers.IO) {
        try {
            database.cacheMetadataDao().getValue(AlbumDatabase.LAST_REFRESH_KEY)?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last refresh timestamp", e)
            null
        }
    }

    suspend fun isCacheVersionValid(): Boolean = withContext(Dispatchers.IO) {
        try {
            val version = database.cacheMetadataDao().getValue(AlbumDatabase.CACHE_VERSION_KEY)
            version == null || version == AlbumDatabase.CURRENT_CACHE_VERSION
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check cache version", e)
            false
        }
    }
}

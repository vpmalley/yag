package fr.vpm.yag.device

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DeviceMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Date

class DeviceAlbumsRetriever {
    suspend fun getLocalAlbums(context: Context): Collection<Album> = coroutineScope {
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val imagesDeferred = async { queryMedia(context, imageCollection) }
        val videosDeferred = async { queryMedia(context, videoCollection) }

        val images = imagesDeferred.await()
        val videos = videosDeferred.await()

        val allMedia = images + videos
        val albumsById = groupMediaIntoAlbums(allMedia)

        Log.d("bg-media", "Got ${albumsById.size} albums")
        albumsById.values
    }

    private suspend fun queryMedia(context: Context, collection: android.net.Uri): List<DeviceMedia> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<DeviceMedia>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val bucketId = cursor.getString(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn)
                val data = cursor.getString(dataColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)

                mediaList.add(
                    DeviceMedia(
                        id,
                        contentUri,
                        data,
                        displayName,
                        Date(dateAdded * 1000),
                        bucketId,
                        bucketName
                    )
                )
            }
        }
        mediaList
    }

    private fun groupMediaIntoAlbums(mediaList: List<DeviceMedia>): Map<String, Album> {
        val albumsById = mutableMapOf<String, Album>()
        for (media in mediaList) {
            val bucketId = media.bucketId ?: "unknown"
            val album = albumsById.getOrPut(bucketId) {
                Album(bucketId, media.bucketName ?: "Unknown", null, mutableListOf(), Album.SOURCE_DEVICE)
            }
            album.addMedia(media)
        }
        return albumsById
    }
}
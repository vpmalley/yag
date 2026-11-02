package fr.vpm.yag.cache

import androidx.room.Embedded
import androidx.room.Relation

data class AlbumWithPhotos(
    @Embedded val album: AlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "albumId"
    )
    val photos: List<PhotoEntity>
)

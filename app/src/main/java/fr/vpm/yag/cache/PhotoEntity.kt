package fr.vpm.yag.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("albumId")]
)
data class PhotoEntity(
    @PrimaryKey val id: Long,
    val albumId: String,
    val uri: String?,
    val path: String?,
    val displayName: String?,
    val isFavorite: Boolean,
    val dateAdded: Long,
    val dateTaken: Long? = null
)

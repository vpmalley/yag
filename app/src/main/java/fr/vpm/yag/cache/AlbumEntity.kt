package fr.vpm.yag.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val photoCount: Int,
    val lastUpdated: Long
)

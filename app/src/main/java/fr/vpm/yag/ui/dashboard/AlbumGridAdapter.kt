package fr.vpm.yag.ui.dashboard

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import fr.vpm.yag.R
import fr.vpm.yag.databinding.ItemAlbumGridBinding
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto

class AlbumGridAdapter(
    private val onAlbumClick: (Album, View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<AlbumGridAdapter.AlbumViewHolder>() {

    private var albums: List<Album> = emptyList()

    inner class AlbumViewHolder(private val binding: ItemAlbumGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.albumName.text = album.name ?: "Unknown Album"

            // Set photo count
            val photoCount = album.photos.size
            binding.albumPhotoCount.text = photoCount.toString()

            // Get first photo or null
            val firstPhoto = album.photos.firstOrNull()

            if (firstPhoto != null) {
                // Cast to DevicePhoto to access uri property
                val devicePhoto = firstPhoto as? DevicePhoto
                val uri = devicePhoto?.uri

                if (uri != null) {
                    // Load image with Glide using memory-efficient settings
                    Glide.with(binding.root.context)
                        .load(uri)
                        .override(300, 300)  // Load at fixed size to reduce memory
                        .centerCrop()
                        .priority(Priority.LOW)  // Lower priority for thumbnails
                        .diskCacheStrategy(DiskCacheStrategy.ALL)  // Cache to avoid reloading
                        .placeholder(R.color.purple_200)  // Placeholder while loading
                        .error(R.color.purple_200)  // Placeholder on error
                        .into(binding.albumThumbnail)
                } else {
                    // No URI - set placeholder background
                    setPlaceholderBackground()
                }
            } else {
                // No photos - set placeholder background
                setPlaceholderBackground()
            }

            // Set click listener for the album
            binding.root.setOnClickListener {
                onAlbumClick(album, binding.root)
            }
        }

        private fun setPlaceholderBackground() {
            Glide.with(binding.root.context).clear(binding.albumThumbnail)
            binding.albumThumbnail.setImageDrawable(null)
            binding.albumThumbnail.setBackgroundColor(
                binding.root.context.getColor(R.color.purple_200)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(albums[position])
    }

    override fun getItemCount(): Int = albums.size

    fun submitList(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}

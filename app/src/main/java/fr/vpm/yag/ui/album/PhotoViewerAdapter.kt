package fr.vpm.yag.ui.album

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import fr.vpm.yag.databinding.ItemPhotoViewerBinding
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.device.model.Photo

class PhotoViewerAdapter : RecyclerView.Adapter<PhotoViewerAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()

    inner class PhotoViewHolder(private val binding: ItemPhotoViewerBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: Photo) {
            val devicePhoto = photo as? DevicePhoto
            val uri = devicePhoto?.uri
            
            if (uri != null) {
                Glide.with(binding.root.context)
                    .load(uri)
                    .apply(RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .fitCenter())
                    .into(binding.fullPhoto)
            } else {
                // No URI - set black background
                binding.fullPhoto.setImageDrawable(null)
                binding.fullPhoto.setBackgroundColor(
                    binding.root.context.getColor(android.R.color.black)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoViewerBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}

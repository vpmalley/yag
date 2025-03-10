package fr.vpm.yag.ui.home

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import fr.vpm.yag.R

class AlbumItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val photoImageView: ImageView
    private val overlayImageView: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.item_album_thumb, this, true)
        photoImageView = findViewById(R.id.photoImageView)
        overlayImageView = findViewById(R.id.overlayImageView)
    }

    fun setPhotoUri(uri: Uri) {
        Glide.with(context)
            .load(uri)
            .into(photoImageView)
    }

    fun setOverlayImage(drawableId: Int?) {
        if (drawableId != null) {
            overlayImageView.setImageResource(drawableId)
            overlayImageView.visibility = VISIBLE
        } else {
            overlayImageView.visibility = GONE
        }
    }
}
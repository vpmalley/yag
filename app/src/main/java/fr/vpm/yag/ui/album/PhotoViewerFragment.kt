package fr.vpm.yag.ui.album

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import fr.vpm.yag.MainActivity
import fr.vpm.yag.databinding.FragmentPhotoViewerBinding
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.device.model.isRawPhoto
import fr.vpm.yag.ui.home.DeviceAlbumViewModel
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    // Share DeviceAlbumViewModel with other fragments
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

    // Receive arguments
    private val args: PhotoViewerFragmentArgs by navArgs()

    // Full screen mode flag
    private var isFullScreen = false
    
    // Filter state
    private val filterEnabled: Boolean
        get() = args.filterEnabled

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val albumId = args.albumId
        val initialPosition = args.photoPosition
        val album = findAlbumById(albumId)

        if (album != null) {
            setupViewPager(album, initialPosition)
        }
    }

    override fun onResume() {
        super.onResume()
        // Restore full screen state if needed
        updateSystemUiVisibility(isFullScreen)
    }

    private fun findAlbumById(albumId: String): fr.vpm.yag.device.model.Album? {
        return deviceAlbumViewModel.albums.value?.firstOrNull { it.id == albumId }
    }

    private fun setupViewPager(album: fr.vpm.yag.device.model.Album, initialPosition: Int) {
        val viewPager = binding.viewPager

        // Filter photos if filter is enabled
        val photosToShow = if (filterEnabled) {
            album.photos.filterNot { photo ->
                photo.isRawPhoto()
            }
        } else {
            album.photos
        }

        // Create adapter with filtered photos and click listeners
        val adapter = PhotoViewerAdapter(
            onPhotoClick = {
                toggleFullScreen()
            },
            onFavoriteClick = { devicePhoto, newFavoriteState ->
                updateFavoriteInMediaStore(devicePhoto, newFavoriteState)
            }
        ).apply {
            submitList(photosToShow)
        }
        viewPager.adapter = adapter

        // Set initial position
        viewPager.setCurrentItem(initialPosition, false)
    }

    private fun updateFavoriteInMediaStore(devicePhoto: DevicePhoto, isFavorite: Boolean) {
        Log.d("PhotoViewer", "Updating favorite for photo ${devicePhoto.id} to $isFavorite")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentUri = devicePhoto.uri ?: return@launch

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_FAVORITE, if (isFavorite) 1 else 0)
                }

                requireContext().contentResolver.update(
                    contentUri,
                    values,
                    null,
                    null
                )

                Log.d("PhotoViewer", "Successfully updated IS_FAVORITE for photo ${devicePhoto.id}")
            } catch (e: Exception) {
                Log.e("PhotoViewer", "Failed to update IS_FAVORITE for photo ${devicePhoto.id}", e)
            }
        }
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        updateSystemUiVisibility(isFullScreen)

        // Also hide/show the Toolbar and BottomNavigationView
        val activity = requireActivity() as? MainActivity
        activity?.let { mainActivity ->
            mainActivity.setToolbarVisibility(!isFullScreen)
            mainActivity.setNavBarVisibility(!isFullScreen)
        }
    }

    private fun updateSystemUiVisibility(fullScreen: Boolean) {
        val activity = requireActivity()
        if (fullScreen) {
            // Hide system UI
            WindowInsetsControllerCompat(activity.window, binding.root).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Show system UI
            WindowInsetsControllerCompat(
                activity.window,
                binding.root
            ).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

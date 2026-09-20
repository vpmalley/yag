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

    // Share DeviceAlbumViewModel with other fragments for album list
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

    // Share AlbumDetailViewModel with AlbumDetailFragment for sorting/filtering
    private val albumDetailViewModel: AlbumDetailViewModel by activityViewModels()

    // Receive arguments
    private val args: PhotoViewerFragmentArgs by navArgs()

    // Full screen mode flag
    private var isFullScreen = false

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
        val filterEnabled = args.filterEnabled

        // Sync filter state with ViewModel
        albumDetailViewModel.setFilterEnabled(filterEnabled)

        val album = findAlbumById(albumId)

        if (album != null) {
            // Set the album in the ViewModel if not already set
            if (albumDetailViewModel.getAlbumId() != albumId) {
                albumDetailViewModel.setAlbum(album)
                albumDetailViewModel.setFilterEnabled(filterEnabled)
            }
            setupViewPager(initialPosition)
        }
    }

    override fun onResume() {
        super.onResume()
        // Restore full screen state if needed
        updateSystemUiVisibility(isFullScreen)
    }

    override fun onPause() {
        super.onPause()
        // Ensure we exit full screen mode when fragment is paused
        // This prevents the app from staying in full screen when navigating to another fragment
        if (isFullScreen) {
            isFullScreen = false
            updateSystemUiVisibility(false)
        }
    }

    private fun findAlbumById(albumId: String): fr.vpm.yag.device.model.Album? {
        return deviceAlbumViewModel.albums.value?.firstOrNull { it.id == albumId }
    }

    private fun setupViewPager(initialPosition: Int) {
        val viewPager = binding.viewPager

        // Get the sorted and filtered photos from the ViewModel
        val photosToShow = albumDetailViewModel.getDisplayPhotos()

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

        // Observe changes to display photos (in case filter/sort changes)
        albumDetailViewModel.displayPhotos.observe(viewLifecycleOwner) { photos ->
            adapter.submitList(photos)
        }
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
    }

    private fun updateSystemUiVisibility(fullScreen: Boolean) {
        val activityWindow = activity?.window
        if (activityWindow == null) {
            Log.e(ContentValues.TAG, "Activity window is null")
            return
        }
        // Also hide/show the Toolbar and BottomNavigationView
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.setToolbarVisibility(!isFullScreen)
            mainActivity.setNavBarVisibility(!isFullScreen)
        }
        if (fullScreen) {
            // Hide system UI
            WindowInsetsControllerCompat(activityWindow, binding.root).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Show system UI
            WindowInsetsControllerCompat(
                activityWindow,
                binding.root
            ).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure we exit full screen mode when fragment is destroyed
        // This prevents the app from staying in full screen when navigating back
        if (isFullScreen) {
            isFullScreen = false
            updateSystemUiVisibility(false)
        }
        _binding = null
    }
}

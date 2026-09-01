package fr.vpm.yag.ui.album

import android.os.Bundle
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
import fr.vpm.yag.ui.home.DeviceAlbumViewModel
import androidx.fragment.app.activityViewModels

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    // Share DeviceAlbumViewModel with other fragments
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

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
        
        // Create adapter with album photos and click listener
        val adapter = PhotoViewerAdapter(onPhotoClick = {
            toggleFullScreen()
        }).apply {
            submitList(album.photos)
        }
        viewPager.adapter = adapter
        
        // Set initial position
        viewPager.setCurrentItem(initialPosition, false)
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
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Show system UI
            WindowInsetsControllerCompat(activity.window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

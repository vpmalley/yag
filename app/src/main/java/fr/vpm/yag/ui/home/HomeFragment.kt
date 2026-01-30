package fr.vpm.yag.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import fr.vpm.yag.databinding.FragmentHomeBinding
import fr.vpm.yag.device.model.DeviceMedia

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val homeViewModel: HomeViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            onMediaPermissionsGranted()
        } else {
            onMediaPermissionsDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        homeViewModel.text.observe(viewLifecycleOwner) {
            _binding?.textHome?.text = it
        }
        homeViewModel.albumsToDisplay.observe(viewLifecycleOwner) {

            // Clear old views to avoid duplication
            _binding?.photoFlow?.referencedIds?.forEach { id ->
                _binding?.root?.findViewById<View>(id)?.let { view ->
                    _binding?.root?.removeView(view)
                }
            }
            _binding?.photoFlow?.referencedIds = intArrayOf()

            val photoItemViewIds = mutableListOf<Int>()
            for (album in it) {
                Log.d("ux-home", "For album ${album.name}")
                (album.media.firstOrNull() as? DeviceMedia)?.uri?.let { albumCoverUri ->
                    val photoItemView = AlbumItemView(requireContext())
                    photoItemView.id = View.generateViewId()
                    photoItemView.setPhotoUri(albumCoverUri)
                    _binding?.root?.addView(photoItemView)
                    _binding?.photoFlow?.addView(photoItemView)
                    photoItemViewIds.add(photoItemView.id)
                    Log.d("ux-home", "Found a photo to display ")
                }
            }

            _binding?.photoFlow?.referencedIds = photoItemViewIds.toIntArray()
            Log.d("ux-home", "Displaying ${photoItemViewIds.size} albums")
            _binding?.photoFlow?.setMaxElementsWrap(3)
        }
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkAndRequestMediaPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkAndRequestMediaPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allPermissionsGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            onMediaPermissionsGranted()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun onMediaPermissionsGranted() {
        homeViewModel.loadAlbums(requireContext())
    }

    private fun onMediaPermissionsDenied() {
        val snackbar = Snackbar.make(
            requireView(),
            "These permissions are required to access the media library",
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction("Ask") {
            checkAndRequestMediaPermissions()
        }
        snackbar.show()
    }
}
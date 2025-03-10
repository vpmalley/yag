package fr.vpm.yag.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import fr.vpm.yag.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        homeViewModel.text.observe(viewLifecycleOwner) {
            _binding?.textHome?.text = it
        }
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestMediaPermissions(onMediaPermissionsGranted, onMediaPermissionsDenied)
    }

    val onMediaPermissionsGranted = {
        homeViewModel.loadAlbums(requireActivity())
    }

    val onMediaPermissionsDenied = {
        val snackbar = Snackbar.make(
            requireView(),
            "These permissions are required to access the media library",
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction("Ask") {
            requestMediaPermissions(onMediaPermissionsGranted) {}
        }
        snackbar.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun requestMediaPermissions(onGranted: () -> Unit, onDenied: () -> Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                onGranted()
            } else {
                onDenied()
            }
        }

        val allPermissionsGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            onGranted()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
}
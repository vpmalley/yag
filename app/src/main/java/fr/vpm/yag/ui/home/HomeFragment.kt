package fr.vpm.yag.ui.home

import android.Manifest
import android.content.Context
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
import fr.vpm.yag.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()
    
    // Permission launcher for photo/video access
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            updatePermissionUi()
            deviceAlbumViewModel.fetchDeviceAlbums(requireContext().applicationContext)
        } else {
            updatePermissionUi()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        deviceAlbumViewModel.text.observe(viewLifecycleOwner) {
            // Only update text if we have permission
            if (hasPhotoPermission(requireContext())) {
                _binding?.textHome?.text = it
            }
        }

        deviceAlbumViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            _binding?.loadingIndicator?.visibility = if (isLoading) View.VISIBLE else View.GONE
            _binding?.buttonFetchMedia?.isEnabled = !isLoading
        }

        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Albums are already being scanned by MainActivity at startup
        // Just observe the existing state
//        deviceAlbumViewModel.loadAlbums(requireContext().applicationContext)
        
        // Check and update permission UI
        updatePermissionUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun hasPhotoPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPhotoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    private fun updatePermissionUi() {
        val context = requireContext()
        val hasPermission = hasPhotoPermission(context)
        
        if (hasPermission) {
            _binding?.textHome?.text = deviceAlbumViewModel.text.value
            _binding?.buttonFetchMedia?.text = "Scan Device"
            _binding?.buttonFetchMedia?.setOnClickListener {
                deviceAlbumViewModel.fetchDeviceAlbums(requireContext().applicationContext)
            }
        } else {
            _binding?.textHome?.text = "This app does not have access to photos and videos. Please grant permission to scan your device."
            _binding?.buttonFetchMedia?.text = "Request Permission"
            _binding?.buttonFetchMedia?.setOnClickListener {
                requestPhotoPermission()
            }
        }
    }
}

package fr.vpm.yag.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import fr.vpm.yag.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        deviceAlbumViewModel.text.observe(viewLifecycleOwner) {
            _binding?.textHome?.text = it
        }

        deviceAlbumViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            _binding?.loadingIndicator?.visibility = if (isLoading) View.VISIBLE else View.GONE
            _binding?.buttonFetchMedia?.isEnabled = !isLoading
        }

        _binding?.buttonFetchMedia?.setOnClickListener {
            deviceAlbumViewModel.fetchDeviceAlbums(requireContext().applicationContext)
        }

        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceAlbumViewModel.loadAlbums(requireContext().applicationContext)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
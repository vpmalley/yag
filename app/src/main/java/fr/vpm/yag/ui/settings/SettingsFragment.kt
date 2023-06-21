package fr.vpm.yag.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.pcloud.sdk.Authenticator
import com.pcloud.sdk.Authenticators
import com.pcloud.sdk.AuthorizationActivity
import com.pcloud.sdk.AuthorizationRequest
import com.pcloud.sdk.Call
import com.pcloud.sdk.Callback
import com.pcloud.sdk.PCloudSdk
import com.pcloud.sdk.RemoteFolder
import fr.vpm.yag.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val getAuthorization =
        activity?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("bg-login", "Received result from login activity")
            it.data?.let { resultIntent ->
                val authorization = AuthorizationActivity.getResult(resultIntent)
                Log.d("bg-login", authorization.toString())
                val authenticator = Authenticators.newOAuthAuthenticator(authorization.token)
                val apiClient = PCloudSdk.newClientBuilder().apiHost(authorization.apiHost)
                    .authenticator(authenticator).create()
                val call = apiClient.listFolder(RemoteFolder.ROOT_FOLDER_ID.toLong())
//                        call.enqueue(object : Callback<RemoteFolder> {
//                            override fun onResponse(
//                                call: Call<RemoteFolder>?,
//                                response: RemoteFolder?
//                            ) {
//                                TODO("Not yet implemented")
//                            }
//
//                            override fun onFailure(call: Call<RemoteFolder>?, t: Throwable?) {
//                                TODO("Not yet implemented")
//                            }
//                        })
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textNotifications
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pcloudLogin.setOnClickListener {
            val intent = AuthorizationActivity.createIntent(
                requireContext(),
                AuthorizationRequest.create().setType(AuthorizationRequest.Type.TOKEN).setClientId("Ri8SOlM0Csz").build()
            )
            Log.d("bg-login", "Created intent to launch")
//            getAuthorization?.launch(intent)
            activity?.startActivityForResult(intent, 101)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
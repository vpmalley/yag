package fr.vpm.yag.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcloud.sdk.Authenticators
import com.pcloud.sdk.AuthorizationData
import com.pcloud.sdk.PCloudSdk
import com.pcloud.sdk.RemoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListFolderViewModel : ViewModel() {


    fun fetchListFolder(authorization: AuthorizationData) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("bg-login", authorization.toString())
            val authenticator = Authenticators.newOAuthAuthenticator(authorization.token)
            val apiClient = PCloudSdk.newClientBuilder().apiHost(authorization.apiHost)
                .authenticator(authenticator).create()
            val call = apiClient.listFolder(RemoteFolder.ROOT_FOLDER_ID.toLong())
            val remoteFolder = try {
                val result = call.execute()
                Log.d(
                    "bg-api",
                    "Received a remote folder response ${result.folderId()} with children ${
                        result.children().joinToString { it.name() }
                    }"
                )
                result
            } catch (t: Throwable) {
                Log.d("bg-api", "Received an exception $t")
                null
            }
        }
    }
}
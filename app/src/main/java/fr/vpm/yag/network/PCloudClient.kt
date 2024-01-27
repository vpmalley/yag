package fr.vpm.yag.network

import android.content.Context
import com.pcloud.sdk.ApiClient
import com.pcloud.sdk.Authenticators
import com.pcloud.sdk.PCloudSdk
import kotlinx.coroutines.flow.firstOrNull

/**
 * To be scoped to an activity
 */
class PCloudClient {


    suspend fun getPCloudClient(): ApiClient? {
        return if (apiClient != null) {
            apiClient
        } else {
            val apiHost = pCloudRepository.getApiHostFlow().firstOrNull()
            val oAuthToken = pCloudRepository.getOAuthTokenFlow().firstOrNull()
            if (apiHost != null && oAuthToken != null) {
                val authenticator = Authenticators.newOAuthAuthenticator(oAuthToken)
                apiClient = PCloudSdk.newClientBuilder().apiHost(apiHost)
                    .authenticator(authenticator).create()
                apiClient
            } else {
                // TODO signed out error
                null
            }
        }
    }

    companion object {
        private var apiClient: ApiClient? = null
    }
}
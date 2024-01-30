package fr.vpm.yag.network

import android.content.Context
import android.util.Log
import com.pcloud.sdk.ApiClient
import com.pcloud.sdk.Authenticators
import com.pcloud.sdk.PCloudSdk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull

class PCloudClient(private val pCloudRepository: SettingsRepository) {


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

    suspend fun getPCloudClientAsFlow(): Flow<ApiClient> {
        return pCloudRepository.getOAuthTokenFlow()
            .combine(pCloudRepository.getApiHostFlow()) { oAuthToken: String, apiHost: String ->
                Log.d("bg-data", "Got token (${oAuthToken.isNotBlank()}) host $apiHost")
                val authenticator = Authenticators.newOAuthAuthenticator(oAuthToken)
                val latestApiClient = PCloudSdk.newClientBuilder().apiHost(apiHost)
                    .authenticator(authenticator).create()
                apiClient = latestApiClient
                latestApiClient
            }
    }


    companion object {
        private var apiClient: ApiClient? = null
    }
}
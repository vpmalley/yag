package fr.vpm.yag.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pcloud.sdk.AuthorizationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    suspend fun saveAuthorization(authorizationResult: AuthorizationData) {
        dataStore.edit { settings ->
            settings[stringPreferencesKey(KEY_OAUTH_TOKEN)] = authorizationResult.token
            settings[stringPreferencesKey(KEY_HOST)] = authorizationResult.apiHost
        }
    }

    fun getOAuthTokenFlow(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(KEY_OAUTH_TOKEN)] ?: ""
        }
    }

    fun getApiHostFlow(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(KEY_HOST)] ?: ""
        }
    }

    companion object {
        private const val KEY_OAUTH_TOKEN = "fr.vpm.yag.oauth_token"
        private const val KEY_HOST = "fr.vpm.yag.api_host"
    }
}
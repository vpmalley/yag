package fr.vpm.yag.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pcloud.sdk.AuthorizationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yagsettings")

    suspend fun saveAuthorization(context: Context, authorizationResult: AuthorizationData) {
        context.dataStore.edit { settings ->
            settings[stringPreferencesKey(KEY_OAUTH_TOKEN)] = authorizationResult.token
            settings[stringPreferencesKey(KEY_HOST)] = authorizationResult.apiHost
        }
    }

    fun getOAuthTokenFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(KEY_OAUTH_TOKEN)] ?: ""
        }
    }

    fun getApiHostFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(KEY_HOST)] ?: ""
        }
    }

    companion object {
        private const val KEY_OAUTH_TOKEN = "fr.vpm.yag.oauth_token"
        private const val KEY_HOST = "fr.vpm.yag.api_host"
    }
}
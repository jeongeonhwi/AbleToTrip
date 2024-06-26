package com.hungrybrothers.abletotrip.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "preferences")

object PreferencesManager {
    private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")

    fun isOnboardingComplete(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[ONBOARDING_COMPLETE_KEY] ?: false
            }
    }

    suspend fun setOnboardingComplete(
        context: Context,
        complete: Boolean,
    ) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE_KEY] = complete
        }
    }
}

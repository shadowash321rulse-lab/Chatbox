package com.scrapw.chatbox

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.scrapw.chatbox.data.UserPreferencesRepository

private const val USER_PREFERENCE_NAME = "user_preferences"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCE_NAME
)

class ChatboxApplication : Application() {

    companion object {
        lateinit var instance: ChatboxApplication
            private set
    }

    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        userPreferencesRepository = UserPreferencesRepository(dataStore)
    }
}

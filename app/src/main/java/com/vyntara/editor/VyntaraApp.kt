package com.vyntara.editor

import android.app.Application
import com.vyntara.editor.data.PreferencesRepository

class VyntaraApp : Application() {
    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
    }
}

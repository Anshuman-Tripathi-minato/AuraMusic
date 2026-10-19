package com.auramusic.app

import android.app.Application
import android.content.Context
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.download.DownloadCenter

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureInit(this)
        LibraryRepository.refresh()
        DownloadCenter.warmUp(this)
    }

    companion object {
        @Volatile private var initialized = false

        /** Idempotent; the playback service may start before the Application in rare cases. */
        @Synchronized
        fun ensureInit(context: Context) {
            if (initialized) return
            AuraPrefs.init(context)
            LibraryRepository.init(context)
            initialized = true
        }
    }
}

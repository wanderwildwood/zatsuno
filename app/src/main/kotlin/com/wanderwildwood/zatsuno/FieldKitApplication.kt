package com.wanderwildwood.zatsuno

import android.app.Application
import com.wanderwildwood.zatsuno.key.IncidentStore

/**
 * Whenever the app's process starts (the app opened, the alarm going off, the phone restarted
 * and the app used again): with no assessment open, no reminder alarm and no reminder
 * notification is left behind; with one open, its reminder is set again.
 */
class FieldKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { IncidentStore.sweep(this) }
    }
}

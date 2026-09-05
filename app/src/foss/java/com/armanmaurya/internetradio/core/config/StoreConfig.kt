package com.armanmaurya.internetradio.core.config

import android.app.Activity

object StoreConfig {
    val isPlayStoreBuild = false
    val storeName = "FOSS Edition"

    fun checkPlayStoreUpdate(activity: Activity, manualCheck: Boolean = false) {
        // No-op for FOSS. We use the GitHub API instead.
    }
}

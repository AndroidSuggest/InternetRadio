package com.armanmaurya.internetradio.core.config

import android.app.Activity
import androidx.annotation.StringRes
import com.armanmaurya.internetradio.R

object StoreConfig {
    val isPlayStoreBuild = false
    @StringRes val storeNameRes = R.string.settings_about_foss_edition

    fun checkPlayStoreUpdate(activity: Activity, manualCheck: Boolean = false) {
        // No-op for FOSS. We use the GitHub API instead.
    }
}

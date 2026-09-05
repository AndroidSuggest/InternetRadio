package com.armanmaurya.internetradio.core.config

import android.app.Activity
import android.widget.Toast
import androidx.annotation.StringRes
import com.armanmaurya.internetradio.R
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object StoreConfig {
    val isPlayStoreBuild = true
    @StringRes val storeNameRes = R.string.settings_about_play_edition

    fun checkPlayStoreUpdate(activity: Activity, manualCheck: Boolean = false) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlow(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            } else if (manualCheck) {
                activity.runOnUiThread {
                    Toast.makeText(activity, activity.getString(R.string.settings_update_up_to_date), Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            if (manualCheck) {
                activity.runOnUiThread {
                    Toast.makeText(activity, activity.getString(R.string.settings_update_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

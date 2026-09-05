package com.armanmaurya.internetradio.core.config

import android.app.Activity
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object StoreConfig {
    val isPlayStoreBuild = true
    val storeName = "Google Play Edition"

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
                    Toast.makeText(activity, "App is up to date", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            if (manualCheck) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

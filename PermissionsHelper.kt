package com.mangatranslator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

object PermissionsHelper {
    
    const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
    
    /**
     * Check if overlay permission is granted
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * Request overlay permission
     */
    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }
    
    /**
     * Check and request notification permission (Android 13+)
     */
    fun checkNotificationPermission(activity: Activity, onGranted: () -> Unit, onDenied: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
                onGranted()
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
                onGranted() // Proceed anyway
            }
        } else {
            // Notification permission not required on older Android versions
            onGranted()
        }
    }
    
    /**
     * Check all required permissions
     */
    fun checkAllPermissions(activity: Activity, onAllGranted: () -> Unit, onDenied: () -> Unit) {
        if (hasOverlayPermission(activity)) {
            checkNotificationPermission(activity, onAllGranted, onDenied)
        } else {
            onDenied()
        }
    }
}

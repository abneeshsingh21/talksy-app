package com.jengachat.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

/**
 * Permission Manager for Talksy
 * Handles runtime permission requests for Android 6.0+
 */
object PermissionManager {

    // Essential permissions that should be requested on first launch
    val essentialPermissions: List<String>
        get() = buildList {
            // Contacts
            add(Manifest.permission.READ_CONTACTS)
            
            // Camera & Microphone (for calls)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            
            // Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    // Media permissions based on Android version
    val mediaPermissions: List<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                // Android 12 and below
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    // All permissions combined
    val allPermissions: List<String>
        get() = essentialPermissions + mediaPermissions

    /**
     * Check if a specific permission is granted
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == 
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if all essential permissions are granted
     */
    fun areEssentialPermissionsGranted(context: Context): Boolean {
        return essentialPermissions.all { isPermissionGranted(context, it) }
    }

    /**
     * Check if contacts permission is granted
     */
    fun hasContactsPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.READ_CONTACTS)
    }

    /**
     * Check if camera permission is granted
     */
    fun hasCameraPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.CAMERA)
    }

    /**
     * Check if microphone permission is granted
     */
    fun hasMicrophonePermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Check if media permissions are granted
     */
    fun hasMediaPermissions(context: Context): Boolean {
        return mediaPermissions.all { isPermissionGranted(context, it) }
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Not needed for older Android versions
        }
    }

    /**
     * Get list of permissions that haven't been granted yet
     */
    fun getMissingPermissions(context: Context): List<String> {
        return allPermissions.filter { !isPermissionGranted(context, it) }
    }

    /**
     * Check if this is first time launching the app (for permission flow)
     */
    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences("Talksy_prefs", Context.MODE_PRIVATE)
        val isFirst = prefs.getBoolean("is_first_launch", true)
        if (isFirst) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
        }
        return isFirst
    }

    /**
     * Mark that permission request has been shown
     */
    fun markPermissionRequestShown(context: Context) {
        val prefs = context.getSharedPreferences("Talksy_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("permission_request_shown", true).apply()
    }

    /**
     * Check if permission request has been shown before
     */
    fun hasPermissionRequestBeenShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences("Talksy_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("permission_request_shown", false)
    }
}

/**
 * Composable for requesting multiple permissions
 */
@Composable
fun rememberMultiplePermissionsLauncher(
    onResult: (Map<String, Boolean>) -> Unit
): androidx.activity.result.ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onResult
    )
}

/**
 * Composable for requesting a single permission
 */
@Composable
fun rememberSinglePermissionLauncher(
    onResult: (Boolean) -> Unit
): androidx.activity.result.ActivityResultLauncher<String> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult
    )
}

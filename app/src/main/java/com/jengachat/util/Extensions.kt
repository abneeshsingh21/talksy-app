package com.jengachat.util

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Extension functions and utilities
 */

// Time formatting for Long timestamps (milliseconds)
fun Long?.formatTime(): String {
    if (this == null) return ""
    val date = Date(this)
    val now = Date()
    
    return when {
        DateUtils.isToday(date.time) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        DateUtils.isToday(date.time + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
    }
}

fun Long?.formatFullTime(): String {
    if (this == null) return ""
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(this))
}

fun Long.formatDuration(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun Long.formatCallDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

// File size formatting
fun Long.formatFileSize(): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    
    return when {
        this >= gb -> String.format(Locale.getDefault(), "%.1f GB", this.toFloat() / gb)
        this >= mb -> String.format(Locale.getDefault(), "%.1f MB", this.toFloat() / mb)
        this >= kb -> String.format(Locale.getDefault(), "%.1f KB", this.toFloat() / kb)
        else -> "$this B"
    }
}

// String extensions
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun String.isValidPhoneNumber(): Boolean {
    return this.length >= 10 && this.all { it.isDigit() || it == '+' }
}

fun String.isValidPassword(): Boolean {
    return this.length >= 6
}

fun String.getInitials(): String {
    return this.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
}

// Context extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.getFileNameFromUri(uri: Uri): String {
    var name = "file"
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        name = cursor.getString(nameIndex)
    }
    return name
}

fun Context.getFileSizeFromUri(uri: Uri): Long {
    var size = 0L
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        cursor.moveToFirst()
        size = cursor.getLong(sizeIndex)
    }
    return size
}

// Flow extensions
fun <T> Flow<T>.handleErrors(): Flow<T> = catch { e ->
    // Log error or handle it
    throw e
}

// Composable utilities
@Composable
fun ShowToast(message: String?) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
}

// Date utilities
fun Date.isSameDay(other: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = this@isSameDay }
    val cal2 = Calendar.getInstance().apply { time = other }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun Date.isYesterday(): Boolean {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return this.isSameDay(yesterday.time)
}

// List utilities
fun <T> List<T>.safeSubList(fromIndex: Int, toIndex: Int): List<T> {
    val safeFrom = fromIndex.coerceAtLeast(0)
    val safeTo = toIndex.coerceAtMost(size)
    return if (safeFrom >= safeTo) emptyList() else subList(safeFrom, safeTo)
}

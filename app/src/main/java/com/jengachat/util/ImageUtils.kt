package com.jengachat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Utility functions for image processing
 */
object ImageUtils {
    
    /**
     * Convert image Uri to Base64 string with compression
     * @param context Android context
     * @param uri Image Uri
     * @param maxWidth Maximum width (default 800px for profile photos)
     * @param quality JPEG compression quality (0-100)
     * @return Base64 encoded string or null if error
     */
    fun uriToBase64(
        context: Context,
        uri: Uri,
        maxWidth: Int = 800,
        quality: Int = 85
    ): String? {
        return try {
            // Open input stream
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return null
            
            // Decode bitmap with sample size to reduce memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate sample size
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxWidth)
            
            // Decode with sample size
            val inputStream2 = context.contentResolver.openInputStream(uri)
                ?: return null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()
            
            if (bitmap == null) return null
            
            // Scale down if still too large
            if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                val newHeight = (bitmap.height * ratio).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
            }
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            
            // Convert to Base64
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Calculate sample size for efficient bitmap loading
     */
    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int): Int {
        var sampleSize = 1
        if (width > maxWidth) {
            val halfWidth = width / 2
            while (halfWidth / sampleSize >= maxWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
    
    /**
     * Get file size from Uri
     */
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Check if file is an image
     */
    fun isImage(mimeType: String?): Boolean {
        return mimeType?.startsWith("image/") == true
    }
    
    /**
     * Get MIME type from Uri
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }
}

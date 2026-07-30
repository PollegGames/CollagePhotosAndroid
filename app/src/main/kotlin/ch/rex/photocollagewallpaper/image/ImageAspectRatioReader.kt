package ch.rex.photocollagewallpaper.image

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.IOException

class ImageAspectRatioReader(
    private val contentResolver: ContentResolver,
) {
    private val cache = LruCache<String, Float>(MAXIMUM_CACHE_ENTRIES)

    fun read(uri: Uri): Float? {
        val cacheKey = uri.toString()
        cache.get(cacheKey)?.let { cached ->
            return knownOrNull(cached)
        }

        val ratio = runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return@runCatching null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            val swapsDimensions = readOrientation(uri) in ROTATED_ORIENTATIONS
            val width = if (swapsDimensions) bounds.outHeight else bounds.outWidth
            val height = if (swapsDimensions) bounds.outWidth else bounds.outHeight
            width.toFloat() / height
        }.getOrNull()
            ?.takeIf { it.isFinite() && it > 0f }

        cache.put(cacheKey, ratio ?: Float.NaN)
        return ratio
    }

    fun clear() {
        cache.evictAll()
    }

    private fun readOrientation(uri: Uri): Int =
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (_: SecurityException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (_: IllegalArgumentException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun knownOrNull(value: Float): Float? =
        value.takeIf { it.isFinite() && it > 0f }

    private companion object {
        const val MAXIMUM_CACHE_ENTRIES = 512
        val ROTATED_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}

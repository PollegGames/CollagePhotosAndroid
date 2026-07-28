package ch.rex.photocollagewallpaper.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

class ScaledBitmapDecoder(
    private val contentResolver: ContentResolver,
    maximumCacheBytes: Int = defaultCacheSize(),
) {
    private val cache = object : LruCache<String, Bitmap>(maximumCacheBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
    }

    fun decode(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val safeWidth = targetWidth.coerceAtLeast(1)
        val safeHeight = targetHeight.coerceAtLeast(1)
        val cacheKey = "$uri@$safeWidth:$safeHeight"
        cache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                return cached
            }
        }

        val decoded = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(uri, safeWidth, safeHeight)
            } else {
                decodeWithBitmapFactory(uri, safeWidth, safeHeight)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            cache.evictAll()
            null
        }

        if (decoded != null) {
            cache.put(cacheKey, decoded)
        }
        return decoded
    }

    fun clear() {
        cache.evictAll()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sourceWidth = info.size.width.coerceAtLeast(1)
            val sourceHeight = info.size.height.coerceAtLeast(1)
            val scale = max(
                targetWidth / sourceWidth.toFloat(),
                targetHeight / sourceHeight.toFloat(),
            ).coerceAtMost(1f)

            decoder.setTargetSize(
                (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                (sourceHeight * scale).roundToInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            decoder.setOnPartialImageListener { false }
        }
    }

    private fun decodeWithBitmapFactory(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    internal fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        while (
            sourceWidth / (sampleSize * 2) >= targetWidth &&
            sourceHeight / (sampleSize * 2) >= targetHeight &&
            sampleSize <= 64
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private fun defaultCacheSize(): Int {
            val runtimeLimit = Runtime.getRuntime().maxMemory() / 8L
            return runtimeLimit
                .coerceAtMost(48L * 1024L * 1024L)
                .coerceAtLeast(8L * 1024L * 1024L)
                .toInt()
        }
    }
}

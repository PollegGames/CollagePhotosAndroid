package ch.rex.photocollagewallpaper.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import ch.rex.photocollagewallpaper.data.PhotoScaleMode
import ch.rex.photocollagewallpaper.util.PerformanceTrace
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

class ScaledBitmapDecoder(
    private val contentResolver: ContentResolver,
    maximumCacheBytes: Int = defaultCacheSize(),
) {
    private val cache =
        object : LruCache<DecodeCacheKey, Bitmap>(maximumCacheBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: DecodeCacheKey, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
        }

    fun decode(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        photoScaleMode: PhotoScaleMode = PhotoScaleMode.FILL,
    ): Bitmap? {
        val safeWidth = targetWidth.coerceAtLeast(1)
        val safeHeight = targetHeight.coerceAtLeast(1)
        val cacheKey = DecodeCacheKey(
            uri = uri.toString(),
            targetWidth = safeWidth,
            targetHeight = safeHeight,
            photoScaleMode = photoScaleMode,
        )
        cache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                return cached
            }
        }
        findReusableBitmap(
            uri = uri,
            targetWidth = safeWidth,
            targetHeight = safeHeight,
            photoScaleMode = photoScaleMode,
        )?.let { reusable ->
            return reusable
        }

        val decoded = PerformanceTrace.measure(
            section = "collage.bitmap.decode",
            slowLogThresholdMillis = 50L,
        ) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    decodeWithImageDecoder(uri, safeWidth, safeHeight, photoScaleMode)
                } else {
                    decodeWithBitmapFactory(uri, safeWidth, safeHeight, photoScaleMode)
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
        }

        if (decoded != null) {
            cache.put(cacheKey, decoded)
        }
        return decoded
    }

    fun clear() {
        cache.evictAll()
    }

    private fun findReusableBitmap(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        photoScaleMode: PhotoScaleMode,
    ): Bitmap? =
        cache.snapshot()
            .asSequence()
            .filter { (key, bitmap) ->
                key.uri == uri.toString() &&
                    !bitmap.isRecycled &&
                    requiredScale(
                        sourceWidth = bitmap.width,
                        sourceHeight = bitmap.height,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        photoScaleMode = photoScaleMode,
                    ) <= 1f
            }
            .map { (_, bitmap) -> bitmap }
            .minByOrNull { bitmap -> bitmap.allocationByteCount }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        photoScaleMode: PhotoScaleMode,
    ): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sourceWidth = info.size.width.coerceAtLeast(1)
            val sourceHeight = info.size.height.coerceAtLeast(1)
            val scale = targetScale(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                photoScaleMode = photoScaleMode,
            )

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
        photoScaleMode: PhotoScaleMode,
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

        val orientation = readExifOrientation(uri)
        val swapsDimensions = orientation in ROTATED_ORIENTATIONS
        val orientedWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
        val orientedHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
        val scale = targetScale(
            sourceWidth = orientedWidth,
            sourceHeight = orientedHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            photoScaleMode = photoScaleMode,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                sourceWidth = orientedWidth,
                sourceHeight = orientedHeight,
                targetWidth = (orientedWidth * scale).roundToInt().coerceAtLeast(1),
                targetHeight = (orientedHeight * scale).roundToInt().coerceAtLeast(1),
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        val oriented = applyExifOrientation(decoded, orientation)
        return scaleDownToTarget(oriented, targetWidth, targetHeight, photoScaleMode)
    }

    private fun readExifOrientation(uri: Uri): Int =
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

    private fun applyExifOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        val transformed = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
        if (transformed !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun scaleDownToTarget(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        photoScaleMode: PhotoScaleMode,
    ): Bitmap {
        val scale = targetScale(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            photoScaleMode = photoScaleMode,
        )
        if (scale >= 0.999f) {
            return bitmap
        }

        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun targetScale(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        photoScaleMode: PhotoScaleMode,
    ): Float = requiredScale(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        photoScaleMode = photoScaleMode,
    ).coerceAtMost(1f)

    private fun requiredScale(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        photoScaleMode: PhotoScaleMode,
    ): Float {
        val widthScale = targetWidth / sourceWidth.toFloat()
        val heightScale = targetHeight / sourceHeight.toFloat()
        return when (photoScaleMode) {
            PhotoScaleMode.FILL -> max(widthScale, heightScale)
            PhotoScaleMode.FIT -> minOf(widthScale, heightScale)
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
        private val ROTATED_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )

        private fun defaultCacheSize(): Int {
            val runtimeLimit = Runtime.getRuntime().maxMemory() / 8L
            return runtimeLimit
                .coerceAtMost(48L * 1024L * 1024L)
                .coerceAtLeast(8L * 1024L * 1024L)
                .toInt()
        }
    }

    private data class DecodeCacheKey(
        val uri: String,
        val targetWidth: Int,
        val targetHeight: Int,
        val photoScaleMode: PhotoScaleMode,
    )
}

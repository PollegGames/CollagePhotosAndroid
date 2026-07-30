package ch.rex.photocollagewallpaper.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import ch.rex.photocollagewallpaper.data.PhotoScaleMode
import ch.rex.photocollagewallpaper.domain.CenterCropCalculator
import ch.rex.photocollagewallpaper.domain.FitCenterCalculator
import ch.rex.photocollagewallpaper.domain.FloatRectangle
import ch.rex.photocollagewallpaper.domain.MosaicLayout
import ch.rex.photocollagewallpaper.domain.MosaicLayoutCalculator
import kotlin.math.ceil
import kotlin.math.floor

class CollageRenderer {
    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG,
    )
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusableSource = Rect()
    private val reusableDestination = RectF()
    private val destinationCache =
        object : LinkedHashMap<DestinationCacheKey, List<FloatRectangle>>(
            MAXIMUM_DESTINATION_CACHE_ENTRIES,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<DestinationCacheKey, List<FloatRectangle>>,
            ): Boolean = size > MAXIMUM_DESTINATION_CACHE_ENTRIES
        }

    /**
     * Draws the completed mosaic, then only the already revealed cells from the incoming
     * mosaic. The optional transition cell is blended over the exact region it will own;
     * the rest of the old wallpaper is left untouched.
     */
    fun drawFrame(
        canvas: Canvas,
        baseMosaic: MosaicBitmapSet?,
        incomingMosaic: MosaicBitmapSet? = null,
        revealedIncomingCells: Int = 0,
        transitioningCellIndex: Int? = null,
        transitionProgress: Float = 1f,
        gapPixels: Float,
        backgroundArgb: Long,
        photoScaleMode: PhotoScaleMode,
        showPlaceholder: Boolean = false,
    ) {
        canvas.drawColor(backgroundArgb.toInt())
        if (baseMosaic == null && incomingMosaic == null && showPlaceholder) {
            drawPlaceholder(canvas, gapPixels)
        }
        baseMosaic?.let { mosaic ->
            drawMosaic(
                canvas = canvas,
                mosaic = mosaic,
                gapPixels = gapPixels,
                maximumCellExclusive = mosaic.layout.photoCount,
                backgroundArgb = backgroundArgb,
                photoScaleMode = photoScaleMode,
            )
        }

        incomingMosaic?.let { mosaic ->
            drawMosaic(
                canvas = canvas,
                mosaic = mosaic,
                gapPixels = gapPixels,
                maximumCellExclusive = revealedIncomingCells.coerceIn(
                    minimumValue = 0,
                    maximumValue = mosaic.layout.photoCount,
                ),
                backgroundArgb = backgroundArgb,
                photoScaleMode = photoScaleMode,
            )

            val transitionIndex = transitioningCellIndex
            if (
                transitionIndex != null &&
                transitionIndex in 0 until mosaic.layout.photoCount
            ) {
                val destination = destinations(
                    canvasWidth = canvas.width,
                    canvasHeight = canvas.height,
                    layout = mosaic.layout,
                    gapPixels = gapPixels,
                ).getOrNull(transitionIndex)
                val bitmap = mosaic.bitmaps.getOrNull(transitionIndex)
                if (destination != null && bitmap != null) {
                    drawPhoto(
                        canvas = canvas,
                        bitmap = bitmap,
                        destination = destination,
                        alpha = (transitionProgress.coerceIn(0f, 1f) * 255f).toInt(),
                        backgroundArgb = backgroundArgb,
                        photoScaleMode = photoScaleMode,
                    )
                }
            }
        }
    }

    private fun drawPlaceholder(
        canvas: Canvas,
        gapPixels: Float,
    ) {
        val destinations = destinations(
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            layout = MosaicLayout.initial,
            gapPixels = gapPixels,
        )
        destinations.forEachIndexed { index, destination ->
            placeholderPaint.color = PLACEHOLDER_COLORS[index % PLACEHOLDER_COLORS.size]
            canvas.drawRect(
                destination.left,
                destination.top,
                destination.right,
                destination.bottom,
                placeholderPaint,
            )
        }
    }

    private fun drawMosaic(
        canvas: Canvas,
        mosaic: MosaicBitmapSet,
        gapPixels: Float,
        maximumCellExclusive: Int,
        backgroundArgb: Long,
        photoScaleMode: PhotoScaleMode,
    ) {
        val destinations = destinations(
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            layout = mosaic.layout,
            gapPixels = gapPixels,
        )
        val cellsToDraw = minOf(
            maximumCellExclusive,
            destinations.size,
            mosaic.bitmaps.size,
        )
        repeat(cellsToDraw) { index ->
            val bitmap = mosaic.bitmaps[index] ?: return@repeat
            drawPhoto(
                canvas = canvas,
                bitmap = bitmap,
                destination = destinations[index],
                alpha = 255,
                backgroundArgb = backgroundArgb,
                photoScaleMode = photoScaleMode,
            )
        }
    }

    private fun drawPhoto(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: FloatRectangle,
        alpha: Int,
        backgroundArgb: Long,
        photoScaleMode: PhotoScaleMode,
    ) {
        if (
            bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0 ||
            alpha <= 0
        ) {
            return
        }

        val source = when (photoScaleMode) {
            PhotoScaleMode.FILL -> CenterCropCalculator.sourceRectangle(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                destinationWidth = destination.width,
                destinationHeight = destination.height,
            )

            PhotoScaleMode.FIT -> FloatRectangle(
                left = 0f,
                top = 0f,
                right = bitmap.width.toFloat(),
                bottom = bitmap.height.toFloat(),
            )
        } ?: return
        val fittedDestination = when (photoScaleMode) {
            PhotoScaleMode.FILL -> destination
            PhotoScaleMode.FIT -> FitCenterCalculator.destinationRectangle(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                destination = destination,
            ) ?: return
        }
        val sourceLeft = floor(source.left).toInt().coerceIn(0, bitmap.width - 1)
        val sourceTop = floor(source.top).toInt().coerceIn(0, bitmap.height - 1)
        val sourceRight = ceil(source.right).toInt().coerceIn(sourceLeft + 1, bitmap.width)
        val sourceBottom = ceil(source.bottom).toInt().coerceIn(sourceTop + 1, bitmap.height)
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        if (photoScaleMode == PhotoScaleMode.FIT) {
            cellBackgroundPaint.color = backgroundArgb.toInt()
            cellBackgroundPaint.alpha = alpha.coerceIn(0, 255)
            canvas.drawRect(
                destination.left,
                destination.top,
                destination.right,
                destination.bottom,
                cellBackgroundPaint,
            )
        }

        reusableSource.set(sourceLeft, sourceTop, sourceRight, sourceBottom)
        reusableDestination.set(
            fittedDestination.left,
            fittedDestination.top,
            fittedDestination.right,
            fittedDestination.bottom,
        )
        canvas.drawBitmap(bitmap, reusableSource, reusableDestination, bitmapPaint)
    }

    private fun destinations(
        canvasWidth: Int,
        canvasHeight: Int,
        layout: MosaicLayout,
        gapPixels: Float = 0f,
    ): List<FloatRectangle> {
        val key = DestinationCacheKey(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            layout = layout,
            gapBits = gapPixels.toBits(),
        )
        return destinationCache.getOrPut(key) {
            MosaicLayoutCalculator.calculate(
                width = canvasWidth.toFloat(),
                height = canvasHeight.toFloat(),
                layout = layout,
                gap = gapPixels,
            )
        }
    }

    private data class DestinationCacheKey(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val layout: MosaicLayout,
        val gapBits: Int,
    )

    private companion object {
        const val MAXIMUM_DESTINATION_CACHE_ENTRIES = 12
        val PLACEHOLDER_COLORS = intArrayOf(
            0xFF263238.toInt(),
            0xFF37474F.toInt(),
            0xFF455A64.toInt(),
        )
    }
}

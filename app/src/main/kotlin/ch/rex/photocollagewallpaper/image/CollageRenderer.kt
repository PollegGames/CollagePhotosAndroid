package ch.rex.photocollagewallpaper.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import ch.rex.photocollagewallpaper.domain.CenterCropCalculator
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
            )

            val transitionIndex = transitioningCellIndex
            if (
                transitionIndex != null &&
                transitionIndex in 0 until mosaic.layout.photoCount
            ) {
                val destination = MosaicLayoutCalculator.calculate(
                    width = canvas.width.toFloat(),
                    height = canvas.height.toFloat(),
                    layout = mosaic.layout,
                    gap = gapPixels,
                ).getOrNull(transitionIndex)
                val bitmap = mosaic.bitmaps.getOrNull(transitionIndex)
                if (destination != null && bitmap != null) {
                    drawPhoto(
                        canvas = canvas,
                        bitmap = bitmap,
                        destination = destination,
                        alpha = (transitionProgress.coerceIn(0f, 1f) * 255f).toInt(),
                    )
                }
            }
        }
    }

    private fun drawPlaceholder(
        canvas: Canvas,
        gapPixels: Float,
    ) {
        val destinations = MosaicLayoutCalculator.calculate(
            width = canvas.width.toFloat(),
            height = canvas.height.toFloat(),
            layout = MosaicLayout.initial,
            gap = gapPixels,
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
    ) {
        val destinations = MosaicLayoutCalculator.calculate(
            width = canvas.width.toFloat(),
            height = canvas.height.toFloat(),
            layout = mosaic.layout,
            gap = gapPixels,
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
            )
        }
    }

    private fun drawPhoto(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: FloatRectangle,
        alpha: Int,
    ) {
        if (
            bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0 ||
            alpha <= 0
        ) {
            return
        }

        val source = CenterCropCalculator.sourceRectangle(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            destinationWidth = destination.width,
            destinationHeight = destination.height,
        ) ?: return
        val sourceLeft = floor(source.left).toInt().coerceIn(0, bitmap.width - 1)
        val sourceTop = floor(source.top).toInt().coerceIn(0, bitmap.height - 1)
        val sourceRight = ceil(source.right).toInt().coerceIn(sourceLeft + 1, bitmap.width)
        val sourceBottom = ceil(source.bottom).toInt().coerceIn(sourceTop + 1, bitmap.height)
        bitmapPaint.alpha = alpha.coerceIn(0, 255)

        canvas.drawBitmap(
            bitmap,
            Rect(sourceLeft, sourceTop, sourceRight, sourceBottom),
            RectF(destination.left, destination.top, destination.right, destination.bottom),
            bitmapPaint,
        )
    }

    private companion object {
        val PLACEHOLDER_COLORS = intArrayOf(
            0xFF263238.toInt(),
            0xFF37474F.toInt(),
            0xFF455A64.toInt(),
        )
    }
}

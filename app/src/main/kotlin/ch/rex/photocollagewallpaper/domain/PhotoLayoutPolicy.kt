package ch.rex.photocollagewallpaper.domain

import kotlin.math.max
import kotlin.math.min

object PhotoLayoutPolicy {
    private const val PORTRAIT_THRESHOLD = 0.85f
    private const val LANDSCAPE_THRESHOLD = 1f / PORTRAIT_THRESHOLD

    fun compatibleLayouts(
        canvasWidth: Int,
        canvasHeight: Int,
    ): List<MosaicLayout> {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return MosaicLayout.entries
        }

        return when (canvasWidth.toFloat() / canvasHeight) {
            in 0f..PORTRAIT_THRESHOLD -> listOf(
                MosaicLayout.THREE_LARGE_TOP,
                MosaicLayout.THREE_LARGE_BOTTOM,
            )

            in LANDSCAPE_THRESHOLD..Float.MAX_VALUE -> listOf(
                MosaicLayout.THREE_LARGE_LEFT,
                MosaicLayout.THREE_LARGE_RIGHT,
            )

            else -> MosaicLayout.entries
        }
    }
}

object PhotoFitScorer {
    fun score(
        photoAspectRatio: Float,
        cellAspectRatio: Float,
    ): Float {
        if (
            !photoAspectRatio.isFinite() ||
            !cellAspectRatio.isFinite() ||
            photoAspectRatio <= 0f ||
            cellAspectRatio <= 0f
        ) {
            return 0f
        }

        return min(photoAspectRatio, cellAspectRatio) /
            max(photoAspectRatio, cellAspectRatio)
    }
}

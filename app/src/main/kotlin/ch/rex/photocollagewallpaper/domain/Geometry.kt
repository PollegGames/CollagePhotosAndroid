package ch.rex.photocollagewallpaper.domain

data class FloatRectangle(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
}

object MosaicLayoutCalculator {
    private const val LARGE_REGION_FRACTION = 0.6f
    private const val SMALL_REGION_FRACTION = 1f - LARGE_REGION_FRACTION

    fun calculate(
        width: Float,
        height: Float,
        layout: MosaicLayout,
        gap: Float,
    ): List<FloatRectangle> {
        if (width <= 0f || height <= 0f) {
            return emptyList()
        }

        val safeGap = gap
            .coerceAtLeast(0f)
            .coerceAtMost(minOf(width, height) / 4f)
        return normalizedRectangles(layout).map { normalized ->
            FloatRectangle(
                left = normalized.left * width +
                    if (normalized.left > 0f) safeGap / 2f else 0f,
                top = normalized.top * height +
                    if (normalized.top > 0f) safeGap / 2f else 0f,
                right = normalized.right * width -
                    if (normalized.right < 1f) safeGap / 2f else 0f,
                bottom = normalized.bottom * height -
                    if (normalized.bottom < 1f) safeGap / 2f else 0f,
            )
        }
    }

    private fun normalizedRectangles(layout: MosaicLayout): List<FloatRectangle> =
        when (layout) {
            MosaicLayout.THREE_LARGE_TOP -> listOf(
                rectangle(0f, 0f, 1f, LARGE_REGION_FRACTION),
                rectangle(0f, LARGE_REGION_FRACTION, 0.5f, 1f),
                rectangle(0.5f, LARGE_REGION_FRACTION, 1f, 1f),
            )

            MosaicLayout.THREE_LARGE_BOTTOM -> listOf(
                rectangle(0f, SMALL_REGION_FRACTION, 1f, 1f),
                rectangle(0f, 0f, 0.5f, SMALL_REGION_FRACTION),
                rectangle(0.5f, 0f, 1f, SMALL_REGION_FRACTION),
            )

            MosaicLayout.THREE_LARGE_LEFT -> listOf(
                rectangle(0f, 0f, LARGE_REGION_FRACTION, 1f),
                rectangle(LARGE_REGION_FRACTION, 0f, 1f, 0.5f),
                rectangle(LARGE_REGION_FRACTION, 0.5f, 1f, 1f),
            )

            MosaicLayout.THREE_LARGE_RIGHT -> listOf(
                rectangle(SMALL_REGION_FRACTION, 0f, 1f, 1f),
                rectangle(0f, 0f, SMALL_REGION_FRACTION, 0.5f),
                rectangle(0f, 0.5f, SMALL_REGION_FRACTION, 1f),
            )
        }

    private fun rectangle(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = FloatRectangle(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )
}

object CenterCropCalculator {
    fun sourceRectangle(
        sourceWidth: Int,
        sourceHeight: Int,
        destinationWidth: Float,
        destinationHeight: Float,
    ): FloatRectangle? {
        if (
            sourceWidth <= 0 ||
            sourceHeight <= 0 ||
            destinationWidth <= 0f ||
            destinationHeight <= 0f
        ) {
            return null
        }

        val sourceAspectRatio = sourceWidth.toFloat() / sourceHeight
        val destinationAspectRatio = destinationWidth / destinationHeight

        return if (sourceAspectRatio > destinationAspectRatio) {
            val croppedWidth = sourceHeight * destinationAspectRatio
            val left = (sourceWidth - croppedWidth) / 2f
            FloatRectangle(
                left = left,
                top = 0f,
                right = left + croppedWidth,
                bottom = sourceHeight.toFloat(),
            )
        } else {
            val croppedHeight = sourceWidth / destinationAspectRatio
            val top = (sourceHeight - croppedHeight) / 2f
            FloatRectangle(
                left = 0f,
                top = top,
                right = sourceWidth.toFloat(),
                bottom = top + croppedHeight,
            )
        }
    }
}

object FitCenterCalculator {
    fun destinationRectangle(
        sourceWidth: Int,
        sourceHeight: Int,
        destination: FloatRectangle,
    ): FloatRectangle? {
        if (
            sourceWidth <= 0 ||
            sourceHeight <= 0 ||
            destination.width <= 0f ||
            destination.height <= 0f
        ) {
            return null
        }

        val scale = minOf(
            destination.width / sourceWidth,
            destination.height / sourceHeight,
        )
        val fittedWidth = sourceWidth * scale
        val fittedHeight = sourceHeight * scale
        val left = destination.left + (destination.width - fittedWidth) / 2f
        val top = destination.top + (destination.height - fittedHeight) / 2f
        return FloatRectangle(
            left = left,
            top = top,
            right = left + fittedWidth,
            bottom = top + fittedHeight,
        )
    }
}

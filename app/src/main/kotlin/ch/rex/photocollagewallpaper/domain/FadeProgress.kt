package ch.rex.photocollagewallpaper.domain

object FadeProgress {
    fun calculate(
        startFrameNanos: Long,
        currentFrameNanos: Long,
        durationNanos: Long,
    ): Float {
        if (durationNanos <= 0L) {
            return 1f
        }
        val elapsedNanos = (currentFrameNanos - startFrameNanos).coerceAtLeast(0L)
        return (elapsedNanos.toDouble() / durationNanos)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}

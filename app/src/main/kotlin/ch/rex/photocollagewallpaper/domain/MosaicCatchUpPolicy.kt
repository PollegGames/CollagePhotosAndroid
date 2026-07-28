package ch.rex.photocollagewallpaper.domain

object MosaicCatchUpPolicy {
    fun stepsToReveal(
        elapsedMillis: Long,
        intervalMillis: Long,
        remainingCells: Int,
        interruptedStepPending: Boolean,
    ): Int {
        val safeRemainingCells = remainingCells.coerceAtLeast(0)
        if (safeRemainingCells == 0) {
            return 0
        }

        val safeIntervalMillis = intervalMillis.coerceAtLeast(1L)
        val elapsedSteps = (
            elapsedMillis.coerceAtLeast(0L) / safeIntervalMillis
            )
            .coerceAtMost(safeRemainingCells.toLong())
            .toInt()
        val minimumSteps = if (interruptedStepPending) 1 else 0

        return maxOf(elapsedSteps, minimumSteps)
            .coerceAtMost(safeRemainingCells)
    }
}

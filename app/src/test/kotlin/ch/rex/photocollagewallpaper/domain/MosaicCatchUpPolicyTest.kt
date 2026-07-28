package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MosaicCatchUpPolicyTest {
    private val minute = 60_000L

    @Test
    fun `one missed interval reveals one photo`() {
        assertEquals(1, steps(elapsedMillis = minute))
    }

    @Test
    fun `two missed intervals reveal two photos`() {
        assertEquals(2, steps(elapsedMillis = 2 * minute))
    }

    @Test
    fun `three or more missed intervals reveal only one complete mosaic`() {
        assertEquals(3, steps(elapsedMillis = 3 * minute))
        assertEquals(3, steps(elapsedMillis = 10 * minute))
    }

    @Test
    fun `partial mosaic is only completed`() {
        assertEquals(
            2,
            steps(
                elapsedMillis = 10 * minute,
                remainingCells = 2,
            ),
        )
        assertEquals(
            1,
            steps(
                elapsedMillis = 10 * minute,
                remainingCells = 1,
            ),
        )
    }

    @Test
    fun `unfinished interrupted step is resumed even before next interval`() {
        assertEquals(
            1,
            steps(
                elapsedMillis = 10_000L,
                interruptedStepPending = true,
            ),
        )
    }

    @Test
    fun `no complete interval produces no change`() {
        assertEquals(0, steps(elapsedMillis = minute - 1L))
    }

    private fun steps(
        elapsedMillis: Long,
        remainingCells: Int = 3,
        interruptedStepPending: Boolean = false,
    ): Int = MosaicCatchUpPolicy.stepsToReveal(
        elapsedMillis = elapsedMillis,
        intervalMillis = minute,
        remainingCells = remainingCells,
        interruptedStepPending = interruptedStepPending,
    )
}

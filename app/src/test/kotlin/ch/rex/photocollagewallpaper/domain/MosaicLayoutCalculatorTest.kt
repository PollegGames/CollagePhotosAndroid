package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicLayoutCalculatorTest {
    @Test
    fun `three photo top layout gives half the screen to the large photo`() {
        val cells = MosaicLayoutCalculator.calculate(
            width = 100f,
            height = 200f,
            layout = MosaicLayout.THREE_LARGE_TOP,
            gap = 0f,
        )

        assertEquals(3, cells.size)
        assertRectangle(cells[0], 0f, 0f, 100f, 100f)
        assertRectangle(cells[1], 0f, 100f, 50f, 200f)
        assertRectangle(cells[2], 50f, 100f, 100f, 200f)
    }

    @Test
    fun `three photo left layout gives half the screen to the large photo`() {
        val cells = MosaicLayoutCalculator.calculate(
            width = 100f,
            height = 300f,
            layout = MosaicLayout.THREE_LARGE_LEFT,
            gap = 0f,
        )

        assertEquals(3, cells.size)
        assertRectangle(cells[0], 0f, 0f, 50f, 300f)
        assertRectangle(cells[1], 50f, 0f, 100f, 150f)
        assertRectangle(cells[2], 50f, 150f, 100f, 300f)
    }

    @Test
    fun `every asymmetric layout covers the complete canvas without gaps`() {
        MosaicLayout.entries.forEach { layout ->
            val cells = MosaicLayoutCalculator.calculate(
                width = 90f,
                height = 180f,
                layout = layout,
                gap = 0f,
            )

            assertEquals(layout.photoCount, cells.size)
            assertEquals(
                90f * 180f,
                cells.sumOf { it.width.toDouble() * it.height.toDouble() }.toFloat(),
                0.01f,
            )
        }
    }

    @Test
    fun `gap appears only between cells`() {
        val cells = MosaicLayoutCalculator.calculate(
            width = 110f,
            height = 220f,
            layout = MosaicLayout.THREE_LARGE_TOP,
            gap = 10f,
        )

        assertRectangle(cells[0], 0f, 0f, 110f, 105f)
        assertRectangle(cells[1], 0f, 115f, 50f, 220f)
        assertRectangle(cells[2], 60f, 115f, 110f, 220f)
    }

    @Test
    fun `invalid canvas size returns no cells`() {
        assertTrue(
            MosaicLayoutCalculator.calculate(
                width = 0f,
                height = 100f,
                layout = MosaicLayout.THREE_LARGE_TOP,
                gap = 0f,
            ).isEmpty(),
        )
    }

    private fun assertRectangle(
        actual: FloatRectangle,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        assertEquals(left, actual.left, 0.0001f)
        assertEquals(top, actual.top, 0.0001f)
        assertEquals(right, actual.right, 0.0001f)
        assertEquals(bottom, actual.bottom, 0.0001f)
    }
}

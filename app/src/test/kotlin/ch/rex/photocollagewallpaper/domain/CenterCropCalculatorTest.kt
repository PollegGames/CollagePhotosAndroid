package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CenterCropCalculatorTest {
    @Test
    fun `wide source is cropped equally on left and right`() {
        val crop = requireNotNull(
            CenterCropCalculator.sourceRectangle(
                sourceWidth = 400,
                sourceHeight = 200,
                destinationWidth = 100f,
                destinationHeight = 100f,
            ),
        )

        assertRectangle(crop, 100f, 0f, 300f, 200f)
    }

    @Test
    fun `tall source is cropped equally on top and bottom`() {
        val crop = requireNotNull(
            CenterCropCalculator.sourceRectangle(
                sourceWidth = 200,
                sourceHeight = 400,
                destinationWidth = 100f,
                destinationHeight = 100f,
            ),
        )

        assertRectangle(crop, 0f, 100f, 200f, 300f)
    }

    @Test
    fun `matching aspect ratio keeps the complete source`() {
        val crop = requireNotNull(
            CenterCropCalculator.sourceRectangle(
                sourceWidth = 400,
                sourceHeight = 200,
                destinationWidth = 200f,
                destinationHeight = 100f,
            ),
        )

        assertRectangle(crop, 0f, 0f, 400f, 200f)
    }

    @Test
    fun `invalid dimensions return null`() {
        assertNull(
            CenterCropCalculator.sourceRectangle(
                sourceWidth = 0,
                sourceHeight = 200,
                destinationWidth = 100f,
                destinationHeight = 100f,
            ),
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

package ch.rex.photocollagewallpaper.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSelectorTest {
    @Test
    fun `selection contains no duplicate when enough images exist`() {
        val available = (1..100).toList()

        val selected = ImageSelector.select(
            availableItems = available,
            requestedCount = 9,
            random = Random(42),
        )

        assertEquals(9, selected.size)
        assertEquals(9, selected.distinct().size)
        assertTrue(selected.all { it in available })
    }

    @Test
    fun `selection repeats only after every available image was used`() {
        val selected = ImageSelector.select(
            availableItems = listOf("a", "b"),
            requestedCount = 6,
            random = Random(3),
        )

        assertEquals(6, selected.size)
        assertEquals(setOf("a", "b"), selected.take(2).toSet())
        assertEquals(selected[0], selected[2])
        assertEquals(selected[1], selected[3])
    }

    @Test
    fun `empty input returns an empty selection`() {
        assertTrue(
            ImageSelector.select(
                availableItems = emptyList<String>(),
                requestedCount = 6,
                random = Random(1),
            ).isEmpty(),
        )
    }

    @Test
    fun `non-positive requested count returns an empty selection`() {
        assertTrue(
            ImageSelector.select(
                availableItems = listOf("a"),
                requestedCount = 0,
                random = Random(1),
            ).isEmpty(),
        )
    }
}

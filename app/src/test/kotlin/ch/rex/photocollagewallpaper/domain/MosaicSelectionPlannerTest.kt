package ch.rex.photocollagewallpaper.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicSelectionPlannerTest {
    @Test
    fun `empty folder has no plan`() {
        assertNull(
            MosaicSelectionPlanner.plan(
                availableItems = emptyList<String>(),
                excludedItems = emptySet(),
            ),
        )
    }

    @Test
    fun `every plan contains three cells`() {
        val plan = requireNotNull(
            MosaicSelectionPlanner.plan(
                availableItems = List(10) { "photo-$it" },
                excludedItems = emptySet(),
                random = Random(7),
            ),
        )

        assertEquals(3, plan.layout.photoCount)
        assertEquals(3, plan.candidateItemsByCell.size)
        assertTrue(plan.candidateItemsByCell.all(List<String>::isNotEmpty))
    }

    @Test
    fun `primary choices are unique when enough photos exist`() {
        val plan = requireNotNull(
            MosaicSelectionPlanner.plan(
                availableItems = List(8) { "photo-$it" },
                excludedItems = emptySet(),
                random = Random(11),
            ),
        )

        val primaryChoices = plan.candidateItemsByCell.map(List<String>::first)
        assertEquals(3, primaryChoices.distinct().size)
    }

    @Test
    fun `old mosaic is excluded when three new photos exist`() {
        val excluded = setOf("old-1", "old-2", "old-3")
        val plan = requireNotNull(
            MosaicSelectionPlanner.plan(
                availableItems = (excluded + setOf("new-1", "new-2", "new-3", "new-4")).toList(),
                excludedItems = excluded,
                random = Random(19),
            ),
        )

        val primaryChoices = plan.candidateItemsByCell.map(List<String>::first)
        assertFalse(primaryChoices.any(excluded::contains))
    }

    @Test
    fun `one readable photo is reused for all cells`() {
        val plan = requireNotNull(
            MosaicSelectionPlanner.plan(
                availableItems = listOf("only-photo"),
                excludedItems = emptySet(),
                random = Random(3),
            ),
        )

        assertEquals(
            listOf("only-photo", "only-photo", "only-photo"),
            plan.candidateItemsByCell.map(List<String>::first),
        )
    }

    @Test
    fun `layout is selected only from the compatible choices`() {
        val compatibleLayouts = listOf(
            MosaicLayout.THREE_LARGE_TOP,
            MosaicLayout.THREE_LARGE_BOTTOM,
        )

        repeat(20) { seed ->
            val plan = requireNotNull(
                MosaicSelectionPlanner.plan(
                    availableItems = List(6) { "photo-$it" },
                    excludedItems = emptySet(),
                    random = Random(seed),
                    availableLayouts = compatibleLayouts,
                ),
            )

            assertTrue(plan.layout in compatibleLayouts)
        }
    }
}

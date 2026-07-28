package ch.rex.photocollagewallpaper.domain

import kotlin.random.Random

data class MosaicSelectionPlan<T>(
    val layout: MosaicLayout,
    val candidateItemsByCell: List<List<T>>,
)

/**
 * Selects three photos and a random asymmetric layout without opening any image file.
 *
 * Every cell receives a different first choice when enough photos are available. Extra
 * candidates are retained as fallbacks so one corrupt image cannot make the wallpaper
 * stay empty.
 */
object MosaicSelectionPlanner {
    fun <T> plan(
        availableItems: List<T>,
        excludedItems: Set<T>,
        random: Random = Random.Default,
        maximumCandidatesPerCell: Int = 24,
    ): MosaicSelectionPlan<T>? {
        val uniqueItems = availableItems.distinct()
        if (uniqueItems.isEmpty()) {
            return null
        }

        val newItems = uniqueItems.filterNot(excludedItems::contains).shuffled(random)
        val previousItems = uniqueItems.filter(excludedItems::contains).shuffled(random)
        val preferredItems = if (newItems.size >= ProgressiveMosaicPolicy.PHOTO_COUNT) {
            newItems
        } else {
            newItems + previousItems
        }
        if (preferredItems.isEmpty()) {
            return null
        }

        val safeMaximum = maximumCandidatesPerCell.coerceAtLeast(1)
        val fallbackItems = preferredItems
            .take(safeMaximum)
            .ifEmpty { uniqueItems.take(safeMaximum) }
        val primaryItems = List(ProgressiveMosaicPolicy.PHOTO_COUNT) { index ->
            preferredItems[index % preferredItems.size]
        }
        val candidatesByCell = primaryItems.map { primary ->
            (listOf(primary) + fallbackItems)
                .distinct()
                .take(safeMaximum)
        }

        return MosaicSelectionPlan(
            layout = MosaicLayout.entries[random.nextInt(MosaicLayout.entries.size)],
            candidateItemsByCell = candidatesByCell,
        )
    }
}

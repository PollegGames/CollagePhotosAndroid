package ch.rex.photocollagewallpaper.domain

import kotlin.random.Random

object ImageSelector {
    fun <T> select(
        availableItems: List<T>,
        requestedCount: Int,
        random: Random = Random.Default,
    ): List<T> {
        if (requestedCount <= 0 || availableItems.isEmpty()) {
            return emptyList()
        }

        val shuffledUniqueItems = availableItems.distinct().shuffled(random)
        if (shuffledUniqueItems.isEmpty()) {
            return emptyList()
        }

        if (shuffledUniqueItems.size >= requestedCount) {
            return shuffledUniqueItems.take(requestedCount)
        }

        return List(requestedCount) { index ->
            shuffledUniqueItems[index % shuffledUniqueItems.size]
        }
    }
}

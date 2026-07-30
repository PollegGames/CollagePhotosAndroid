package ch.rex.photocollagewallpaper.image

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import ch.rex.photocollagewallpaper.data.FolderAccessState
import ch.rex.photocollagewallpaper.data.FolderImageRepository
import ch.rex.photocollagewallpaper.data.PhotoScaleMode
import ch.rex.photocollagewallpaper.domain.MosaicLayout
import ch.rex.photocollagewallpaper.domain.MosaicLayoutCalculator
import ch.rex.photocollagewallpaper.domain.MosaicSelectionPlanner
import ch.rex.photocollagewallpaper.domain.PhotoFitScorer
import ch.rex.photocollagewallpaper.domain.PhotoLayoutPolicy
import kotlin.math.ceil
import kotlin.random.Random

data class MosaicBitmapSet(
    val layout: MosaicLayout,
    val bitmaps: List<Bitmap?>,
    val imageUris: List<Uri?>,
) {
    val isComplete: Boolean
        get() = bitmaps.size == layout.photoCount &&
            imageUris.size == layout.photoCount &&
            bitmaps.all { it != null } &&
            imageUris.all { it != null }
}

data class PlannedMosaicCell(
    val candidateUris: List<Uri>,
)

data class PreparedMosaic(
    val layout: MosaicLayout,
    val cells: List<PlannedMosaicCell>,
)

data class PreparedMosaicResult(
    val mosaic: PreparedMosaic?,
    val discoveredImageCount: Int,
    val folderName: String,
    val accessState: FolderAccessState,
)

data class DecodedMosaicCell(
    val bitmap: Bitmap,
    val imageUri: Uri,
)

data class LoadedCollage(
    val mosaic: MosaicBitmapSet?,
    val discoveredImageCount: Int,
    val folderName: String,
    val accessState: FolderAccessState,
)

class CollageBitmapLoader(
    private val folderImageRepository: FolderImageRepository,
    private val bitmapDecoder: ScaledBitmapDecoder,
    private val aspectRatioReader: ImageAspectRatioReader,
) {
    /**
     * Builds a lightweight plan without opening any image file. The actual decoder is
     * the only source of truth, so a provider that cannot expose cheap metadata can no
     * longer make every photo disappear.
     */
    fun prepare(
        folderUri: String?,
        excludedImageUris: Set<Uri>,
        canvasWidth: Int,
        canvasHeight: Int,
        randomSeed: Long,
    ): PreparedMosaicResult {
        val scan = folderImageRepository.scan(folderUri)
        if (scan.imageUris.isEmpty()) {
            return PreparedMosaicResult(
                mosaic = null,
                discoveredImageCount = 0,
                folderName = scan.folderName,
                accessState = scan.accessState,
            )
        }

        val random = Random(seedFrom(randomSeed))
        val plan = MosaicSelectionPlanner.plan(
            availableItems = scan.imageUris,
            excludedItems = excludedImageUris,
            random = random,
            maximumCandidatesPerCell = MAXIMUM_DECODE_CANDIDATES_PER_CELL,
            availableLayouts = PhotoLayoutPolicy.compatibleLayouts(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
            ),
        ) ?: return PreparedMosaicResult(
            mosaic = null,
            discoveredImageCount = scan.imageUris.size,
            folderName = scan.folderName,
            accessState = scan.accessState,
        )

        val plannedCells = plan.candidateItemsByCell.map { candidateUris ->
            PlannedMosaicCell(
                candidateUris = candidateUris,
            )
        }

        return PreparedMosaicResult(
            mosaic = PreparedMosaic(
                layout = plan.layout,
                cells = plannedCells,
            ),
            discoveredImageCount = scan.imageUris.size,
            folderName = scan.folderName,
            accessState = scan.accessState,
        )
    }

    fun decodeCell(
        preparedMosaic: PreparedMosaic,
        cellIndex: Int,
        usedImageUris: Set<Uri>,
        canvasWidth: Int,
        canvasHeight: Int,
        gapPixels: Float,
        photoScaleMode: PhotoScaleMode,
    ): DecodedMosaicCell? {
        val plannedCell = preparedMosaic.cells.getOrNull(cellIndex) ?: return null
        val destination = MosaicLayoutCalculator.calculate(
            width = canvasWidth.toFloat(),
            height = canvasHeight.toFloat(),
            layout = preparedMosaic.layout,
            gap = gapPixels,
        ).getOrNull(cellIndex) ?: return null
        val targetWidth = ceil(destination.width).toInt().coerceAtLeast(1)
        val targetHeight = ceil(destination.height).toInt().coerceAtLeast(1)

        val unusedCandidates = plannedCell.candidateUris.filterNot(usedImageUris::contains)
        val candidates = unusedCandidates.ifEmpty { plannedCell.candidateUris }
        val rankedCandidates = if (photoScaleMode == PhotoScaleMode.FILL) {
            rankCandidatesForCell(
                candidates = candidates,
                destinationAspectRatio = destination.width / destination.height,
            )
        } else {
            candidates
        }
        for (uri in rankedCandidates) {
            val bitmap = bitmapDecoder.decode(
                uri = uri,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                photoScaleMode = photoScaleMode,
            ) ?: continue
            return DecodedMosaicCell(
                bitmap = bitmap,
                imageUri = uri,
            )
        }
        return null
    }

    fun loadComplete(
        folderUri: String?,
        excludedImageUris: Set<Uri>,
        canvasWidth: Int,
        canvasHeight: Int,
        gapPixels: Float,
        photoScaleMode: PhotoScaleMode,
        randomSeed: Long,
    ): LoadedCollage {
        val preparedResult = prepare(
            folderUri = folderUri,
            excludedImageUris = excludedImageUris,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            randomSeed = randomSeed,
        )
        val preparedMosaic = preparedResult.mosaic
            ?: return LoadedCollage(
                mosaic = null,
                discoveredImageCount = preparedResult.discoveredImageCount,
                folderName = preparedResult.folderName,
                accessState = preparedResult.accessState,
            )

        val bitmaps = MutableList<Bitmap?>(preparedMosaic.layout.photoCount) { null }
        val imageUris = MutableList<Uri?>(preparedMosaic.layout.photoCount) { null }
        preparedMosaic.cells.indices.forEach { cellIndex ->
            val decoded = decodeCell(
                preparedMosaic = preparedMosaic,
                cellIndex = cellIndex,
                usedImageUris = imageUris.filterNotNull().toSet(),
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                gapPixels = gapPixels,
                photoScaleMode = photoScaleMode,
            ) ?: return@forEach
            bitmaps[cellIndex] = decoded.bitmap
            imageUris[cellIndex] = decoded.imageUri
        }

        val mosaic = if (bitmaps.all { it != null } && imageUris.all { it != null }) {
            MosaicBitmapSet(
                layout = preparedMosaic.layout,
                bitmaps = bitmaps,
                imageUris = imageUris,
            )
        } else {
            null
        }
        return LoadedCollage(
            mosaic = mosaic,
            discoveredImageCount = preparedResult.discoveredImageCount,
            folderName = preparedResult.folderName,
            accessState = preparedResult.accessState,
        )
    }

    /**
     * Decodes the last completed mosaic without enumerating the selected folder.
     */
    fun decodeSavedMosaic(
        imageUriValues: List<String>,
        layout: MosaicLayout,
        canvasWidth: Int,
        canvasHeight: Int,
        gapPixels: Float,
        photoScaleMode: PhotoScaleMode,
    ): MosaicBitmapSet? {
        val destinations = MosaicLayoutCalculator.calculate(
            width = canvasWidth.toFloat(),
            height = canvasHeight.toFloat(),
            layout = layout,
            gap = gapPixels,
        )
        val bitmaps = MutableList<Bitmap?>(layout.photoCount) { null }
        val imageUris = MutableList<Uri?>(layout.photoCount) { null }
        imageUriValues
            .asSequence()
            .mapNotNull { value -> runCatching { value.toUri() }.getOrNull() }
            .take(layout.photoCount)
            .forEachIndexed { index, uri ->
                val destination = destinations[index]
                val bitmap = bitmapDecoder.decode(
                    uri = uri,
                    targetWidth = ceil(destination.width).toInt().coerceAtLeast(1),
                    targetHeight = ceil(destination.height).toInt().coerceAtLeast(1),
                    photoScaleMode = photoScaleMode,
                ) ?: return@forEachIndexed
                bitmaps[index] = bitmap
                imageUris[index] = uri
            }

        if (bitmaps.none { it != null }) {
            return null
        }
        return MosaicBitmapSet(
            layout = layout,
            bitmaps = bitmaps,
            imageUris = imageUris,
        )
    }

    private fun seedFrom(value: Long): Int = (value xor (value ushr 32)).toInt()

    private fun rankCandidatesForCell(
        candidates: List<Uri>,
        destinationAspectRatio: Float,
    ): List<Uri> {
        val inspected = candidates
            .take(MAXIMUM_ASPECT_CANDIDATES_PER_CELL)
            .mapIndexed { index, uri ->
                RankedCandidate(
                    uri = uri,
                    originalIndex = index,
                    fitScore = aspectRatioReader.read(uri)?.let { photoAspectRatio ->
                        PhotoFitScorer.score(
                            photoAspectRatio = photoAspectRatio,
                            cellAspectRatio = destinationAspectRatio,
                        )
                    } ?: UNKNOWN_ASPECT_SCORE,
                )
            }
            .sortedWith(
                compareByDescending<RankedCandidate>(RankedCandidate::fitScore)
                    .thenBy(RankedCandidate::originalIndex),
            )
            .map(RankedCandidate::uri)
        return inspected + candidates.drop(inspected.size)
    }

    private data class RankedCandidate(
        val uri: Uri,
        val originalIndex: Int,
        val fitScore: Float,
    )

    private companion object {
        const val MAXIMUM_DECODE_CANDIDATES_PER_CELL = 24
        const val MAXIMUM_ASPECT_CANDIDATES_PER_CELL = 6
        const val UNKNOWN_ASPECT_SCORE = 0.5f
    }
}

package ch.rex.photocollagewallpaper.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rex.photocollagewallpaper.data.FolderAccessState
import ch.rex.photocollagewallpaper.data.MAX_INTERVAL_MINUTES
import ch.rex.photocollagewallpaper.data.MIN_INTERVAL_MINUTES
import ch.rex.photocollagewallpaper.data.intervalMinutes
import ch.rex.photocollagewallpaper.domain.MosaicLayout
import ch.rex.photocollagewallpaper.domain.MosaicLayoutCalculator
import ch.rex.photocollagewallpaper.wallpaper.PhotoCollageWallpaperService
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: WallpaperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoCollageTheme {
                WallpaperConfigurationScreen(viewModel)
            }
        }
    }
}

@Composable
private fun WallpaperConfigurationScreen(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.selectFolder(uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Collage Photos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Un fond d’écran local, sans compte, publicité ni accès Internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { folderPicker.launch(null) },
            ) {
                Text("Choisir le dossier de photos")
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = state.folderName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = folderStatusText(state.folderAccessState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            MosaicDiagramCard(backgroundArgb = state.settings.backgroundArgb)

            HorizontalDivider()

            Text(
                text = "Mosaïque asymétrique",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Toujours 3 photos : une grande et deux petites. La grande case est placée aléatoirement en haut, en bas, à gauche ou à droite.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IntervalSetting(
                intervalMillis = state.settings.intervalMillis,
                onIntervalSaved = viewModel::setIntervalMinutes,
            )

            GapSetting(
                savedGapDp = state.settings.gapDp,
                onGapSaved = viewModel::setGapDp,
            )

            BackgroundSetting(
                savedArgb = state.settings.backgroundArgb,
                onColorSaved = viewModel::setBackgroundArgb,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fondu simple",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Transition courte uniquement sur la nouvelle photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.settings.fadeEnabled,
                    onCheckedChange = viewModel::setFadeEnabled,
                )
            }

            Text(
                text = "La nouvelle mosaïque se construit photo après photo au-dessus de l’ancienne. L’ancienne disparaît seulement lorsque les 3 nouvelles photos sont prêtes.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Au retour sur l’écran, jusqu’à 3 changements manqués sont rattrapés pour terminer une seule mosaïque.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.folderAccessState == FolderAccessState.AVAILABLE,
                onClick = { openLiveWallpaperPreview(context) },
            ) {
                Text("Aperçu et définition du fond d’écran")
            }

            Text(
                text = "Les photos réelles sont chargées uniquement dans l’aperçu officiel Android et dans le fond d’écran. Elles restent sur ton téléphone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MosaicDiagramCard(backgroundArgb: Long) {
    val layout = remember { MosaicLayout.entries.random() }
    val cellColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.toArgb(),
        MaterialTheme.colorScheme.secondaryContainer.toArgb(),
        MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
    )
    val labelColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
        MaterialTheme.colorScheme.onSecondaryContainer.toArgb(),
        MaterialTheme.colorScheme.onTertiaryContainer.toArgb(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Schéma de composition",
            style = MaterialTheme.typography.titleSmall,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                val cells = MosaicLayoutCalculator.calculate(
                    width = size.width,
                    height = size.height,
                    layout = layout,
                    gap = 6.dp.toPx(),
                )
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    canvas.drawColor(backgroundArgb.toInt())
                    cells.forEachIndexed { index, cell ->
                        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = cellColors[index]
                            style = Paint.Style.FILL
                        }
                        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = labelColors[index]
                            textAlign = Paint.Align.CENTER
                            textSize = 16.sp.toPx()
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        val rectangle = RectF(cell.left, cell.top, cell.right, cell.bottom)
                        canvas.drawRect(rectangle, fillPaint)
                        val textY = rectangle.centerY() -
                            (labelPaint.ascent() + labelPaint.descent()) / 2f
                        canvas.drawText(
                            "Photo ${index + 1}",
                            rectangle.centerX(),
                            textY,
                            labelPaint,
                        )
                    }
                }
            }
        }
        Text(
            text = "Ce schéma n’ouvre aucune photo. La disposition réelle sera choisie aléatoirement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntervalSetting(
    intervalMillis: Long,
    onIntervalSaved: (Int) -> Unit,
) {
    val savedMinutes = intervalMinutes(intervalMillis)
    var draftMinutes by remember(savedMinutes) {
        mutableFloatStateOf(savedMinutes.toFloat())
    }
    val roundedMinutes = draftMinutes.roundToInt()

    Column {
        Text(
            text = if (roundedMinutes == 1) {
                "Changement : chaque minute"
            } else {
                "Changement : toutes les $roundedMinutes minutes"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = draftMinutes,
            onValueChange = { draftMinutes = it },
            onValueChangeFinished = { onIntervalSaved(roundedMinutes) },
            valueRange = MIN_INTERVAL_MINUTES.toFloat()..MAX_INTERVAL_MINUTES.toFloat(),
            steps = MAX_INTERVAL_MINUTES - MIN_INTERVAL_MINUTES - 1,
        )
        Text(
            text = "Une mosaïque complète se construit en environ ${roundedMinutes * 3} minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GapSetting(
    savedGapDp: Float,
    onGapSaved: (Float) -> Unit,
) {
    var draftGap by remember(savedGapDp) { mutableFloatStateOf(savedGapDp) }

    Column {
        Text(
            text = "Espace : ${draftGap.toInt()} dp",
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = draftGap,
            onValueChange = { draftGap = it },
            onValueChangeFinished = { onGapSaved(draftGap) },
            valueRange = 0f..12f,
            steps = 11,
        )
    }
}

@Composable
private fun BackgroundSetting(
    savedArgb: Long,
    onColorSaved: (Long) -> Unit,
) {
    var customHex by rememberSaveable(savedArgb) {
        mutableStateOf(String.format(Locale.ROOT, "#%06X", savedArgb and 0xFFFFFFL))
    }
    val parsedCustomColor = remember(customHex) { parseRgbColor(customHex) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Couleur de fond",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = savedArgb == 0xFF000000L,
                onClick = { onColorSaved(0xFF000000L) },
                label = { Text("Noir") },
            )
            FilterChip(
                selected = savedArgb == 0xFFFFFFFFL,
                onClick = { onColorSaved(0xFFFFFFFFL) },
                label = { Text("Blanc") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = customHex,
                onValueChange = { customHex = it.take(7) },
                label = { Text("Couleur personnalisée") },
                placeholder = { Text("#1A1A1A") },
                singleLine = true,
                isError = customHex.length >= 7 && parsedCustomColor == null,
            )
            OutlinedButton(
                enabled = parsedCustomColor != null,
                onClick = { parsedCustomColor?.let(onColorSaved) },
            ) {
                Text("Appliquer")
            }
        }
    }
}

private fun folderStatusText(accessState: FolderAccessState): String = when (accessState) {
    FolderAccessState.NO_FOLDER -> "Choisis un dossier pour continuer."
    FolderAccessState.AVAILABLE -> "Dossier prêt. Les photos seront chargées dans l’aperçu Android."
    FolderAccessState.EMPTY -> "Le dossier ne contient aucune image compatible."
    FolderAccessState.INACCESSIBLE -> "Le dossier n’est plus accessible."
}

private fun parseRgbColor(value: String): Long? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
        return null
    }
    return normalized.toLongOrNull(radix = 16)?.let { rgb ->
        0xFF000000L or rgb
    }
}

private fun openLiveWallpaperPreview(context: Context) {
    val component = ComponentName(context, PhotoCollageWallpaperService::class.java)
    val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }

    try {
        context.startActivity(directIntent)
    } catch (_: ActivityNotFoundException) {
        val chooserIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        if (context !is Activity) {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }
}

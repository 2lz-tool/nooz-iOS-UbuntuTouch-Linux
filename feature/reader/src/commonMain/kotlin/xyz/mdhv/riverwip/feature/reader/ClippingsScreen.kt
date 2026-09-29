package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.paperGrain
import xyz.mdhv.riverwip.design.topFadingEdge
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.Clipping
import xyz.mdhv.riverwip.model.PaperGrain
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class ClippingsViewModel(private val repo: ClippingRepository) : ViewModel() {
    val clippings: StateFlow<List<Clipping>> =
        repo.observeClippings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun remove(itemId: String) = viewModelScope.launch { repo.remove(itemId) }

    class Factory(private val repo: ClippingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = ClippingsViewModel(repo) as T
    }
}

private val CLIP_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The Clippings shelf (owner's #2: "out is like a board with these physical
 * looking clippings"): each clipping torn-edged, faintly rotated, on the
 * app's own paper tone regardless of the active theme, since a clipping is
 * paper first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClippingsScreen(
    vm: ClippingsViewModel,
    paperGrain: PaperGrain,
    onBack: () -> Unit,
) {
    val clippings by vm.clippings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val sharer = rememberClippingSharer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.clippings_screen_title), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (clippings.isEmpty()) {
                EmptyState(
                    title = stringResource(Res.string.clippings_none_yet),
                    body = "Tap the bookmark on any article to tear it out and keep it on this board.",
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().topFadingEdge(listState.canScrollBackward),
                    contentPadding = PaddingValues(Tokens.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.lg),
                ) {
                    items(clippings, key = { it.itemId }) { clip ->
                        TornClippingCard(
                            clip = clip,
                            paperGrain = paperGrain,
                            onOpen = { uriHandler.openUri(clip.url) },
                            onShare = { sharer.share(clip.title, clip.sourceTitle, clip.author, clip.url) },
                            onRemove = { vm.remove(clip.itemId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One clipping, cut from the page (owner's #2, reference photos of scattered
 * physical clippings): a jagged top/bottom tear via [tornEdgeShape], a small
 * deterministic rotation seeded from the clipping's own id — stable across
 * recompositions, never re-rolled — and the app's own paper tone regardless
 * of the active theme, since a clipping is paper first.
 */
@Composable
private fun TornClippingCard(
    clip: Clipping,
    paperGrain: PaperGrain,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    val seed = remember(clip.itemId) { clip.itemId.hashCode().toLong() }
    val angle = remember(seed) { ((seed % 5) - 2) * 0.9f }
    val shape = remember(seed) { tornEdgeShape(seed) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = angle }
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(Tokens.Palette.paperField)
            .paperGrain(paperGrain, Tokens.Palette.paperInkDim)
            .clickable(onClickLabel = stringResource(Res.string.clippings_open_in_browser), onClick = onOpen)
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (clip.sourceTitle ?: "Nooz").uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = Tokens.Palette.paperInkDim,
                modifier = Modifier.weight(1f),
            )
            Text(
                clip.topic.placeholderLabel,
                style = MaterialTheme.typography.labelMedium,
                color = clip.topic.toComposeColor(),
            )
        }
        HorizontalDivider(color = Tokens.Palette.paperInkFaint)
        Text(clip.title, style = MaterialTheme.typography.headlineSmall, color = Tokens.Palette.paperInk, maxLines = 3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                byline(clip),
                style = MaterialTheme.typography.bodySmall,
                color = Tokens.Palette.paperInkDim,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.reader_share_clipping), tint = Tokens.Palette.paperInkDim, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Bookmark, contentDescription = stringResource(Res.string.clippings_remove), tint = Tokens.Palette.paperInkDim, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A jagged, deterministic tear along the top and bottom edges only — sides stay straight, as a torn newspaper column would. */
private fun tornEdgeShape(seed: Long) = GenericShape { size, _ ->
    val rnd = Random(seed)
    val notch = (size.height * 0.015f).coerceIn(2f, 10f)
    val step = (size.width / 16f).coerceAtLeast(8f)

    moveTo(0f, 0f)
    var x = 0f
    while (x < size.width) {
        val nx = (x + step).coerceAtMost(size.width)
        lineTo(nx, (rnd.nextFloat() - 0.5f) * 2 * notch)
        x = nx
    }
    lineTo(size.width, size.height)
    x = size.width
    while (x > 0f) {
        val nx = (x - step).coerceAtLeast(0f)
        lineTo(nx, size.height + (rnd.nextFloat() - 0.5f) * 2 * notch)
        x = nx
    }
    close()
}

private fun byline(clip: Clipping): String {
    val saved = CLIP_DATE.format(Instant.ofEpochMilli(clip.savedAt).atZone(ZoneId.systemDefault()))
    val who = listOfNotNull(clip.author?.takeIf { it.isNotBlank() }, clip.sourceTitle).joinToString(" | ")
    return if (who.isEmpty()) "Clipped $saved" else "$who · clipped $saved"
}

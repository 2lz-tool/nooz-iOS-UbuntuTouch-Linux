package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.AppSearchBar
import xyz.mdhv.riverwip.design.NoResultsState
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.topFadingEdge
import xyz.mdhv.riverwip.model.BiasLexicon

/**
 * Advanced settings: the reading lens's word list (owner's ask, 2026-07), in
 * exactly the two sections asked for — **Default**, the shipped
 * [BiasLexicon] terms grouped by category, each individually switchable off;
 * and **Custom**, a reader's own added words/phrases, addable and deletable.
 * Both write straight through [SettingsViewModel] to [AppSettings]'s
 * `lensDisabledDefaultTerms`/`lensCustomTerms` — see
 * `AffectSpanDetector.detect`'s two matching parameters for how they're
 * actually consulted wherever the lens runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensWordListScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Search (this polish pass, 2026-07): filters both Default and Custom
    // below as you type, case-insensitively, by substring — the same shared
    // bar and matching idiom already used for Sources/the Stand, just docked
    // to the top of this list instead of the bottom.
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filteredDefaultTerms = if (q.isEmpty()) {
        BiasLexicon.terms
    } else {
        BiasLexicon.terms
            .mapValues { (_, words) -> words.filter { it.lowercase().contains(q) } }
            .filterValues { it.isNotEmpty() }
    }
    val filteredCustomTerms = settings.lensCustomTerms.sorted().filter { q.isEmpty() || it.lowercase().contains(q) }
    val noMatches = q.isNotEmpty() && filteredDefaultTerms.isEmpty() && filteredCustomTerms.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.word_list_title), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppSearchBar(query = query, onQueryChange = { query = it }, placeholder = stringResource(Res.string.word_list_search))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .topFadingEdge(listState.canScrollBackward),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Tokens.Spacing.md,
                    vertical = Tokens.Spacing.md,
                ),
            ) {
                item {
                    Text(
                        stringResource(Res.string.word_list_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Tokens.Spacing.md),
                    )
                }

                if (filteredDefaultTerms.isNotEmpty()) {
                    item { SectionHeading(stringResource(Res.string.word_list_default), modifier = Modifier.padding(bottom = Tokens.Spacing.xs)) }
                    for ((category, words) in filteredDefaultTerms) {
                        item {
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Tokens.Spacing.sm, bottom = Tokens.Spacing.xxs),
                            )
                        }
                        items(words, key = { "default:$it" }) { word ->
                            WordToggleRow(
                                word = word,
                                checked = word !in settings.lensDisabledDefaultTerms,
                                onCheckedChange = { vm.setLensTermEnabled(word, it) },
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = Tokens.Spacing.md),
                    )
                    SectionHeading(stringResource(Res.string.word_list_custom), modifier = Modifier.padding(bottom = Tokens.Spacing.xs))
                    AddCustomWordRow(onAdd = { vm.addLensCustomTerm(it) })
                }

                if (noMatches) {
                    item { NoResultsState(fill = false, modifier = Modifier.fillMaxWidth()) }
                } else if (filteredCustomTerms.isEmpty()) {
                    item {
                        Text(
                            if (q.isEmpty()) "No custom words yet." else "No custom words match \"$query\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Tokens.Spacing.sm),
                        )
                    }
                } else {
                    items(filteredCustomTerms, key = { "custom:$it" }) { word ->
                        CustomWordRow(word = word, onRemove = { vm.removeLensCustomTerm(word) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WordToggleRow(word: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = Tokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Type-a-word-tap-add, the same idiom as EditScreen.kt's "Add by URL" (a plain text field + trailing Add icon, no modal). */
@Composable
private fun AddCustomWordRow(onAdd: (String) -> Unit) {
    // Hoisted: a semantics block is not a composable scope.
    val addHint = stringResource(Res.string.word_list_add_hint)
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle.Default.merge(
                MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = addHint },
        )
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text)
                    text = ""
                }
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.word_list_add))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CustomWordRow(word: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.word_list_remove, word),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

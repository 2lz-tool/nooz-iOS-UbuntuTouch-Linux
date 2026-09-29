package xyz.mdhv.riverwip.inference

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.inference.byok.AndroidKeyValueStore
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.inference.byok.ByokProvider
import xyz.mdhv.riverwip.inference.local.LocalLlamaProvider
import xyz.mdhv.riverwip.inference.urbana.UrbanaProvider

/**
 * Builds the default provider fallback order (brief §5: Urbana → local llama.cpp
 * → ML Kit). This `foss`-flavor file has no ML Kit reference at all — the `full`
 * flavor's sibling file appends [xyz.mdhv.riverwip.inference.mlkit.MlKitProvider]
 * to the same list. `:app`'s composition root calls this one function; which
 * variant it resolves to is decided by Android's variant-aware dependency
 * resolution (same flavor dimension name on both modules), not by any branching
 * here.
 */
object ProviderFactory {
    fun build(context: Context, modelDir: File): List<InferenceProvider> = listOf(
        ByokProvider(ByokConfigStore(AndroidKeyValueStore(context))),
        UrbanaProvider(context),
        LocalLlamaProvider(context, modelDir),
    )
}

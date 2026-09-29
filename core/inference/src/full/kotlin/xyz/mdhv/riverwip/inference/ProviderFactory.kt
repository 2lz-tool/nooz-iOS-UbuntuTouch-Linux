package xyz.mdhv.riverwip.inference

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.inference.byok.AndroidKeyValueStore
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.inference.byok.ByokProvider
import xyz.mdhv.riverwip.inference.local.LocalLlamaProvider
import xyz.mdhv.riverwip.inference.mlkit.MlKitProvider
import xyz.mdhv.riverwip.inference.urbana.UrbanaProvider

/**
 * `full`-flavor provider order (brief §5: BYOK → Urbana → local llama.cpp →
 * ML Kit) — a user-configured key ([ByokProvider]) takes precedence when
 * present, then [MlKitProvider] trails the two `foss`-safe providers. See the
 * `foss` sibling file for how the flavor resolution works.
 */
object ProviderFactory {
    fun build(context: Context, modelDir: File): List<InferenceProvider> = listOf(
        ByokProvider(ByokConfigStore(AndroidKeyValueStore(context))),
        UrbanaProvider(context),
        LocalLlamaProvider(context, modelDir),
        MlKitProvider(),
    )
}

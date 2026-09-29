package xyz.mdhv.riverwip.inference

/**
 * User-ordered fallback across providers (brief §5, default order: Urbana →
 * local llama.cpp → ML Kit). Tries each provider in [order] until one is
 * available and returns a result; a provider that reports unavailable is
 * skipped silently (that's its contract), but a provider that *fails* while
 * attempting a rewrite stops the chain and surfaces the failure rather than
 * masking it by falling through — total inspectability (brief §3) means the
 * user sees why a rewrite didn't happen, not just that it didn't.
 */
class InferenceRouter(private val order: List<InferenceProvider>) {

    /** The order the router will actually try, i.e. [order] as given (user-configurable upstream). */
    val providerOrder: List<String> get() = order.map { it.id }

    /**
     * A cheap upfront capability check — true once any provider in [order] is
     * currently usable, without attempting a real rewrite/digest call. Lets a
     * caller show a "not configured yet" state proactively (owner's ask for
     * Nooz Flash) instead of only discovering it after the reader taps and
     * the request comes back [DigestResult.Failed]/[RewriteResult.Failed].
     */
    suspend fun hasAvailableProvider(): Boolean = order.any { it.isAvailable() }

    // User-facing failures never enumerate internal provider ids (owner: it's
    // unseemly to surface "tried: local-llama, byok"). The order the router
    // tried is still inspectable via [providerOrder]; the message stays plain.
    private val NONE_AVAILABLE = "no reader-intelligence provider is available"

    suspend fun rewrite(request: RewriteRequest): RewriteResult {
        if (order.isEmpty()) return RewriteResult.Failed(NONE_AVAILABLE)
        for (provider in order) {
            if (!provider.isAvailable()) continue
            return provider.rewrite(request)
        }
        return RewriteResult.Failed(NONE_AVAILABLE)
    }

    suspend fun digest(request: DigestRequest): DigestResult {
        if (order.isEmpty()) return DigestResult.Failed(NONE_AVAILABLE)
        for (provider in order) {
            if (!provider.isAvailable()) continue
            return provider.digest(request)
        }
        return DigestResult.Failed(NONE_AVAILABLE)
    }
}

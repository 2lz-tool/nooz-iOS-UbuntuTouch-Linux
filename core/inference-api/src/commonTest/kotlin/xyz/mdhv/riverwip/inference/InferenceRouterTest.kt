package xyz.mdhv.riverwip.inference

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeProvider(
    override val id: String,
    private val available: Boolean,
    private val result: RewriteResult = RewriteResult.Failed("not configured"),
    private val digestResult: DigestResult = DigestResult.Failed("not configured"),
) : InferenceProvider {
    var rewriteCalls = 0
        private set
    var digestCalls = 0
        private set
    override suspend fun isAvailable(): Boolean = available
    override suspend fun rewrite(request: RewriteRequest): RewriteResult {
        rewriteCalls++
        return result
    }
    override suspend fun digest(request: DigestRequest): DigestResult {
        digestCalls++
        return digestResult
    }
}

class InferenceRouterTest {

    private val req = RewriteRequest("The minister slammed the bill.", "slammed", 15, 22)

    @Test fun triesProvidersInOrderAndUsesFirstAvailable() = runTest {
        val urbana = FakeProvider("urbana", available = false)
        val local = FakeProvider("local-llama", available = true, result = RewriteResult.Success("The minister criticized the bill.", Provenance.NATIVE))
        val mlkit = FakeProvider("mlkit", available = true, result = RewriteResult.Success("wrong one", Provenance.NATIVE))
        val router = InferenceRouter(listOf(urbana, local, mlkit))

        val result = router.rewrite(req)
        assertTrue(result is RewriteResult.Success)
        assertEquals("The minister criticized the bill.", (result as RewriteResult.Success).rewrittenSentence)
        assertEquals(0, urbana.rewriteCalls) // unavailable — skipped, never called
        assertEquals(1, local.rewriteCalls)
        assertEquals(0, mlkit.rewriteCalls) // never reached — local already answered
    }

    @Test fun stopsAtFirstAvailableProviderEvenIfItFails() = runTest {
        // A provider that IS available but fails should not fall through to the
        // next one — total inspectability means the user sees *why* it failed,
        // not a silently-masked retry.
        val local = FakeProvider("local-llama", available = true, result = RewriteResult.Failed("model not downloaded"))
        val mlkit = FakeProvider("mlkit", available = true, result = RewriteResult.Success("should not be used", Provenance.NATIVE))
        val router = InferenceRouter(listOf(local, mlkit))

        val result = router.rewrite(req)
        assertTrue(result is RewriteResult.Failed)
        assertEquals("model not downloaded", (result as RewriteResult.Failed).reason)
        assertEquals(0, mlkit.rewriteCalls)
    }

    @Test fun allUnavailableFailsCleanlyWithoutLeakingInternalIds() = runTest {
        // Inspectability moved to providerOrder; the user-facing message never
        // enumerates internal provider ids (owner: surfacing "tried: urbana,
        // local-llama" is unseemly).
        val router = InferenceRouter(listOf(FakeProvider("urbana", false), FakeProvider("local-llama", false)))
        val result = router.rewrite(req)
        assertTrue(result is RewriteResult.Failed)
        val reason = (result as RewriteResult.Failed).reason
        assertFalse(reason.contains("urbana"))
        assertFalse(reason.contains("local-llama"))
        assertEquals(listOf("urbana", "local-llama"), router.providerOrder)
    }

    @Test fun emptyOrderFailsImmediately() = runTest {
        val result = InferenceRouter(emptyList()).rewrite(req)
        assertTrue(result is RewriteResult.Failed)
    }

    @Test fun providerOrderReflectsConfiguredSequence() {
        val router = InferenceRouter(listOf(FakeProvider("urbana", true), FakeProvider("local-llama", true)))
        assertEquals(listOf("urbana", "local-llama"), router.providerOrder)
    }

    @Test fun digestTriesProvidersInOrderAndUsesFirstAvailable() = runTest {
        val digestReq = DigestRequest(listOf("Storm hits coast", "Markets steady", "Vote passes"))
        val local = FakeProvider("local-llama", available = false)
        val byok = FakeProvider(
            "byok",
            available = true,
            digestResult = DigestResult.Success("Storm strikes as vote passes, markets hold.", Provenance.CLOUD),
        )
        val router = InferenceRouter(listOf(local, byok))

        val result = router.digest(digestReq)
        assertTrue(result is DigestResult.Success)
        assertEquals("Storm strikes as vote passes, markets hold.", (result as DigestResult.Success).flash)
        assertEquals(0, local.digestCalls)
        assertEquals(1, byok.digestCalls)
    }

    @Test fun digestAllUnavailableFailsCleanlyWithoutLeakingInternalIds() = runTest {
        val router = InferenceRouter(listOf(FakeProvider("local-llama", false), FakeProvider("byok", false)))
        val result = router.digest(DigestRequest(listOf("Headline")))
        assertTrue(result is DigestResult.Failed)
        val reason = (result as DigestResult.Failed).reason
        assertFalse(reason.contains("local-llama"))
        assertFalse(reason.contains("byok"))
        assertEquals(listOf("local-llama", "byok"), router.providerOrder)
    }

    @Test fun hasAvailableProviderTrueWhenAnyProviderIsAvailable() = runTest {
        val router = InferenceRouter(listOf(FakeProvider("urbana", false), FakeProvider("local-llama", true)))
        assertTrue(router.hasAvailableProvider())
    }

    @Test fun hasAvailableProviderFalseWhenNoneAreAvailable() = runTest {
        val router = InferenceRouter(listOf(FakeProvider("urbana", false), FakeProvider("local-llama", false)))
        assertFalse(router.hasAvailableProvider())
    }

    @Test fun hasAvailableProviderFalseForEmptyOrder() = runTest {
        assertFalse(InferenceRouter(emptyList()).hasAvailableProvider())
    }
}

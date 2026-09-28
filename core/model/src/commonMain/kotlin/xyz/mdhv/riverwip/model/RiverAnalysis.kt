package xyz.mdhv.riverwip.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * The river's cross-section metrics (brief §P4). Pure math, unit-verified —
 * every number here is tap-explainable by its formula (brief §3), and the
 * supply-vs-drift decomposition (the analytical differentiator) sums exactly to
 * the observed change.
 *
 * Conventions: counts are per-topic. "stream" = what flowed from the user's
 * chosen sources; "read" = what the user opened. Shares are normalized to sum 1.
 */
object RiverAnalysis {

    /** counts → shares (normalized to sum 1). Empty/zero total → all-zero shares. */
    fun shares(counts: Map<Topic, Int>): Map<Topic, Double> {
        val total = counts.values.sum()
        if (total == 0) return counts.mapValues { 0.0 }
        return counts.mapValues { it.value.toDouble() / total }
    }

    /** coverage = items read / items flowed. Overall, in [0,1]. */
    fun coverage(streamCounts: Map<Topic, Int>, readCounts: Map<Topic, Int>): Double {
        val flowed = streamCounts.values.sum()
        if (flowed == 0) return 0.0
        val read = readCounts.values.sum()
        return read.toDouble() / flowed
    }

    /** Per-topic coverage = read_i / flowed_i. Topics with zero flow are omitted. */
    fun coverageByTopic(streamCounts: Map<Topic, Int>, readCounts: Map<Topic, Int>): Map<Topic, Double> =
        streamCounts.filterValues { it > 0 }
            .mapValues { (t, s) -> (readCounts[t] ?: 0).toDouble() / s }

    /**
     * Per-topic over/under ratio = your topic share ÷ stream topic share.
     * >1 you over-read that topic relative to supply; <1 you under-read it.
     * Topics with zero stream share are omitted (ratio undefined).
     */
    fun overUnderRatio(streamCounts: Map<Topic, Int>, readCounts: Map<Topic, Int>): Map<Topic, Double> {
        val s = shares(streamCounts)
        val r = shares(readCounts)
        return s.filterValues { it > 0.0 }.mapValues { (t, si) -> (r[t] ?: 0.0) / si }
    }

    /**
     * Breadth = exp(Shannon entropy) of a distribution — "effective number of
     * topics". A reader who splits evenly across k topics scores k; one who reads
     * a single topic scores 1.
     */
    fun breadth(counts: Map<Topic, Int>): Double {
        val p = shares(counts).values.filter { it > 0.0 }
        if (p.isEmpty()) return 0.0
        val h = -p.sumOf { it * ln(it) }
        return exp(h)
    }

    /** One topic's decomposition of the change in the user's intake share. */
    data class TopicDecomposition(
        val topic: Topic,
        val observedDelta: Double,
        /** Change attributable to the stream's mix shifting (selection held). */
        val supply: Double,
        /** Change attributable to the user selecting differently (supply held). */
        val selection: Double,
    ) {
        /** supply + selection reproduces observedDelta (exactly, up to float epsilon). */
        val residual: Double get() = observedDelta - (supply + selection)

        /** Percent of the change attributable to supply, when the split is coherent (same sign). */
        val supplyPercent: Double?
            get() = if (abs(observedDelta) < EPS) null else supply / observedDelta * 100.0
        val selectionPercent: Double?
            get() = if (abs(observedDelta) < EPS) null else selection / observedDelta * 100.0
    }

    /**
     * Supply-vs-drift decomposition (Laspeyres-style, exact). For each topic,
     * model the user's intake share as r_i = s_i · a_i where s_i is the stream
     * share and a_i = r_i/s_i is the user's selection intensity (the over/under
     * ratio). Then the change between periods splits exactly:
     *
     *   Δr_i = a0_i·(s1_i − s0_i)   [supply: mix shifted, selection at baseline]
     *        + s1_i·(a1_i − a0_i)   [selection: intensity shifted, supply current]
     *
     * The two terms sum to Δr_i identically (unit-tested). This exactness relies
     * on the domain invariant that **reads are drawn from the stream** — a topic
     * with zero stream in a period has zero reads (you cannot read what did not
     * flow), which the aggregate pipeline guarantees. Under it, a is defined as 0
     * where stream share is 0 and the identity stays exact.
     */
    fun decompose(
        stream0: Map<Topic, Int>, read0: Map<Topic, Int>,
        stream1: Map<Topic, Int>, read1: Map<Topic, Int>,
    ): List<TopicDecomposition> {
        val s0 = shares(stream0); val r0 = shares(read0)
        val s1 = shares(stream1); val r1 = shares(read1)
        val topics = (s0.keys + s1.keys + r0.keys + r1.keys).toSortedSet(compareBy { it.ordinal })
        return topics.map { t ->
            val s0i = s0[t] ?: 0.0; val s1i = s1[t] ?: 0.0
            val r0i = r0[t] ?: 0.0; val r1i = r1[t] ?: 0.0
            val a0 = if (s0i > 0.0) r0i / s0i else 0.0
            val a1 = if (s1i > 0.0) r1i / s1i else 0.0
            val supply = a0 * (s1i - s0i)
            val selection = s1i * (a1 - a0)
            TopicDecomposition(
                topic = t,
                observedDelta = r1i - r0i,
                supply = supply,
                selection = selection,
            )
        }
    }

    const val EPS = 1e-9
}

package xyz.mdhv.riverwip.model

/**
 * The deterministic fidelity guard (brief §P5) — why the tap-to-defuse rewrite is
 * safe. Small models measurably fabricate numbers, entities, and negations, so a
 * rewrite is **rejected** if, relative to the source sentence:
 *   (a) a number appears that isn't in the source, or a source number disappears;
 *   (b) a capitalized named entity appears or disappears;
 *   (c) negation parity changes (no / not / never / n't … count differs).
 *
 * Pure and deterministic. Rejected rewrites carry their reason — nothing silent
 * (brief §3). The gate: the adversarial corpus must be caught 100%.
 */
object FidelityGuard {

    enum class Kind { NUMBER_ADDED, NUMBER_REMOVED, ENTITY_ADDED, ENTITY_REMOVED, NEGATION_CHANGED }

    data class Violation(val kind: Kind, val detail: String)

    data class Verdict(val accepted: Boolean, val violations: List<Violation>) {
        val reason: String get() = if (accepted) "preserves numbers, entities, and negation" else
            violations.joinToString("; ") { it.detail }
    }

    fun check(source: String, rewrite: String): Verdict {
        val violations = ArrayList<Violation>()
        checkNumbers(source, rewrite, violations)
        checkEntities(source, rewrite, violations)
        checkNegation(source, rewrite, violations)
        return Verdict(violations.isEmpty(), violations)
    }

    // ---- numbers ---------------------------------------------------------

    // Integers/decimals with optional thousands separators; keeps a trailing % as
    // part of the token so "50%" != "50".
    private val NUMBER = Regex("\\d[\\d,]*(?:\\.\\d+)?%?")

    private fun numbers(text: String): List<String> =
        NUMBER.findAll(text).map { it.value.replace(",", "") }.toList()

    private fun checkNumbers(source: String, rewrite: String, out: MutableList<Violation>) {
        val src = numbers(source).groupingBy { it }.eachCount()
        val rew = numbers(rewrite).groupingBy { it }.eachCount()
        for ((n, c) in rew) if (c > (src[n] ?: 0)) {
            out.add(Violation(Kind.NUMBER_ADDED, "number '$n' appears in the rewrite but not the source"))
        }
        for ((n, c) in src) if (c > (rew[n] ?: 0)) {
            out.add(Violation(Kind.NUMBER_REMOVED, "number '$n' from the source is missing in the rewrite"))
        }
    }

    // ---- entities --------------------------------------------------------

    // Capitalized token (proper nouns, acronyms like US/UN/BJP), allowing
    // internal apostrophes/periods/hyphens (O'Brien, U.S., Jean-Luc).
    private val CAP = Regex("\\b[A-Z][A-Za-z'’.\\-]*[A-Za-z]|\\b[A-Z]\\b")

    // Capitalized function/common words that carry no entity meaning. We do NOT
    // special-case the sentence-initial token: the lens replaces only the flagged
    // span (mid-sentence), so the start is preserved and a leading entity that is
    // dropped/substituted must still be caught (recall over false-alarm here).
    private val CAP_STOP = setOf(
        "The", "A", "An", "This", "That", "These", "Those", "It", "He", "She",
        "They", "We", "I", "You", "His", "Her", "Its", "Their", "Our",
        "In", "On", "At", "But", "And", "Or", "If", "When", "While", "After",
        "Before", "As", "For", "To", "Of", "With", "By", "From", "Is", "Are",
        "Was", "Were", "Be", "Been", "Has", "Have", "Had", "Not", "No",
    )

    private fun entities(text: String): Map<String, Int> {
        return CAP.findAll(text).map { it.value }
            .filter { it !in CAP_STOP }
            .groupingBy { it }.eachCount()
    }

    private fun checkEntities(source: String, rewrite: String, out: MutableList<Violation>) {
        val src = entities(source)
        val rew = entities(rewrite)
        for ((e, c) in rew) if (c > (src[e] ?: 0)) {
            out.add(Violation(Kind.ENTITY_ADDED, "named entity '$e' appears in the rewrite but not the source"))
        }
        for ((e, c) in src) if (c > (rew[e] ?: 0)) {
            out.add(Violation(Kind.ENTITY_REMOVED, "named entity '$e' from the source is missing in the rewrite"))
        }
    }

    // ---- negation --------------------------------------------------------

    private val NEG_WORD = Regex("\\b(?:no|not|never|none|neither|nor|cannot|without)\\b", RegexOption.IGNORE_CASE)
    private val NEG_CONTRACTION = Regex("n[''`]t\\b", RegexOption.IGNORE_CASE)

    private fun negationCount(text: String): Int =
        NEG_WORD.findAll(text).count() + NEG_CONTRACTION.findAll(text).count()

    private fun checkNegation(source: String, rewrite: String, out: MutableList<Violation>) {
        val s = negationCount(source)
        val r = negationCount(rewrite)
        if (s != r) {
            out.add(Violation(Kind.NEGATION_CHANGED, "negation count changed ($s → $r); a fact may have flipped"))
        }
    }
}

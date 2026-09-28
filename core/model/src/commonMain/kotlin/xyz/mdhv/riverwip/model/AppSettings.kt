package xyz.mdhv.riverwip.model

/**
 * App-level display preferences (owner's Settings mocks, 2026-07). The three
 * literal surface tints — White, Paper, Dark — are the owner's own three-theme
 * reference image. [SYSTEM] was added on top of them in 2026-08 (see D34) after
 * a Galaxy S26+ reader reported unreadable text in dark mode: every tint here
 * was *manual*, so a phone in dark mode still got the light Paper app while the
 * system bars — which did follow the OS — drew their light-mode icons over it.
 * The tints themselves are unchanged; this only adds the option to let the
 * phone pick between two of them.
 *
 * The struck-through "Show Article Progress" row remains deliberately
 * unimplemented.
 */
enum class ThemeMode(val key: String) {
    /** Follow the phone: Paper by day, the charcoal Dark tint by night. */
    SYSTEM("system"),
    WHITE("white"),
    PAPER("paper"),
    DARK("dark");

    /**
     * The literal tint this mode actually paints, given the phone's current
     * night setting. Every consumer resolves through here rather than reading
     * [ThemeMode] directly, so "which surface am I on" has exactly one answer —
     * the bug above came from the app and the system bars answering it
     * differently.
     */
    fun resolve(systemDark: Boolean): ThemeMode = when (this) {
        SYSTEM -> if (systemDark) DARK else PAPER
        else -> this
    }

    /** Whether the resolved surface is a dark one — drives system-bar icon contrast. */
    fun isDarkSurface(systemDark: Boolean): Boolean = resolve(systemDark) == DARK

    /**
     * One two-finger flick in the reader = the next tint. Deliberately cycles
     * the three literal tints only: a flick is a request for a *particular*
     * surface, so landing on "follow the phone" mid-read would hand the tint
     * back to the OS instead of changing it. Flicking while on [SYSTEM] steps
     * off onto an explicit tint that differs from whatever is on screen, which
     * is why it needs to know what the phone currently resolves to.
     */
    fun next(systemDark: Boolean = false): ThemeMode = when (this) {
        SYSTEM -> if (systemDark) WHITE else DARK
        WHITE -> PAPER
        PAPER -> DARK
        DARK -> WHITE
    }

    companion object {
        fun fromKey(key: String?): ThemeMode = when (key) {
            "white" -> WHITE
            "dark" -> DARK
            "paper" -> PAPER
            // "system" was a key from the first theme iteration that resolved
            // paper-toned in practice; it now means what it always said it
            // meant. "light" stays a paper-toned legacy alias.
            "system" -> SYSTEM
            else -> PAPER
        }
    }
}

/**
 * Three reading voices — Hyle's own three internal families the owner named
 * (2026-07): the two sans, **Hyle Grotesk Classic** and **Hyle Grotesk Plus**,
 * and the one serif, **Hyle Print**. Verified against the Hyle Design System's
 * `fonts/` source-of-truth and bundled as OFL TTFs. Hyle Deco Pro is
 * deliberately excluded — "don't use Hyle Deco," per the owner.
 */
enum class ReaderFont(val key: String, val label: String) {
    GROTESK_CLASSIC("grotesk-classic", "Grotesk Classic"),
    GROTESK_PLUS("grotesk-plus", "Grotesk Plus"),
    PRINT("print", "Print");

    companion object {
        private val byKey = entries.associateBy(ReaderFont::key)
        fun fromKey(key: String?): ReaderFont = when (key) {
            // Legacy keys from the pre-Hyle-families iterations.
            "serif" -> PRINT
            "sans", "system" -> GROTESK_CLASSIC
            "mono" -> GROTESK_PLUS
            else -> byKey[key] ?: GROTESK_CLASSIC
        }
    }
}

/**
 * Three-step reading size (owner's Settings mock). The names are the owner's
 * own — Rice, Peanut, Almond — real objects in ascending relative size.
 * Multiplies the type scale.
 */
enum class TextScale(val key: String, val multiplier: Float, val label: String) {
    RICE("rice", 0.9f, "Rice"),
    PEANUT("peanut", 1.0f, "Peanut"),
    ALMOND("almond", 1.15f, "Almond");

    companion object {
        private val byKey = entries.associateBy(TextScale::key)
        fun fromKey(key: String?): TextScale = when (key) {
            // Legacy keys from the plain-label iteration.
            "small" -> RICE
            "standard" -> PEANUT
            "large" -> ALMOND
            else -> byKey[key] ?: PEANUT
        }
    }
}

/**
 * Paper texture (owner's ask, 2026-07): three fixed steps, not a slider —
 * "simplistic and minimal but serving a wide range." Applies to Paper's
 * background and Clippings' torn cards; None (the default) draws nothing.
 */
enum class PaperGrain(val key: String, val label: String) {
    NONE("none", "None"),
    FINE("fine", "Fine"),
    COARSE("coarse", "Coarse");

    companion object {
        private val byKey = entries.associateBy(PaperGrain::key)
        fun fromKey(key: String?): PaperGrain = byKey[key] ?: NONE
    }
}

/**
 * How a read article marks itself in the Stand's list (owner's ask, 2026-07):
 * dimmed to a quieter grey (the default), or struck through — never a third,
 * separate "read" icon; the title itself carries the mark.
 */
enum class ReadMarkStyle(val key: String, val label: String) {
    GREYED("greyed", "Greyed"),
    STRIKETHROUGH("strikethrough", "Strikethrough");

    companion object {
        private val byKey = entries.associateBy(ReadMarkStyle::key)
        fun fromKey(key: String?): ReadMarkStyle = byKey[key] ?: GREYED
    }
}

/**
 * How a feed image renders (owner's ask, 2026-07): plain colour, a tasteful
 * duotone black & white (not a flat desaturation), or a halftone dot-print
 * stylization — newsprint's own reproduction technique, in keeping with this
 * app's whole newspaper metaphor. Mutually exclusive: the owner's ask reads
 * as "BW, *or* a dot-print stylization," a style choice, not stacked filters.
 */
enum class ImageStyle(val key: String, val label: String) {
    COLOR("color", "Color"),
    BLACK_AND_WHITE("bw", "Black & white"),
    HALFTONE("halftone", "Halftone");

    companion object {
        private val byKey = entries.associateBy(ImageStyle::key)
        fun fromKey(key: String?): ImageStyle = byKey[key] ?: HALFTONE
    }
}

/**
 * The reading aside (owner's ask): every so often while actually reading, a
 * real sentence pulled from the article open, never fabricated -- an
 * editorial break like a newspaper's own pull-quote, not a reward. Two
 * presentations, mirroring the web reader's identical Settings choice: a
 * small pull-quote block (the default), or a single wire-style dateline line.
 */
enum class ReadingAsideStyle(val key: String, val label: String) {
    QUOTE("quote", "Found quote"),
    DATELINE("dateline", "Dateline aside");

    companion object {
        private val byKey = entries.associateBy(ReadingAsideStyle::key)
        fun fromKey(key: String?): ReadingAsideStyle = byKey[key] ?: QUOTE
    }
}

data class AppSettings(
    /**
     * Follow the phone by default (D34). The owner's mock checks the middle
     * tint, and [ThemeMode.SYSTEM] still lands on exactly that tint on a
     * light-mode phone — so a Paper default is what almost every new reader
     * sees. What changes is the reader whose phone is already in dark mode:
     * they now open into the Dark tint instead of a light app they never asked
     * for. Only new installs are affected; an existing reader's persisted
     * choice is read straight back out of DataStore and left alone.
     */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val readerFont: ReaderFont = ReaderFont.GROTESK_CLASSIC,
    val showReadingTime: Boolean = true,
    val textScale: TextScale = TextScale.PEANUT,
    /**
     * The lens's loaded-language highlighting (owner: the reader's eye toggle
     * was confusing and off-mock). Off by default; a single Settings switch
     * turns it on. Detection is real; the rewrite stays honestly stubbed.
     */
    val highlightLoadedLanguage: Boolean = false,
    /**
     * Reader chrome (owner: the back control must be obvious by default, with an
     * immersive mode as the opt-in). `false` shows the visible back affordance
     * and utility bar; `true` hides them for a gestures-only page. Introduced in
     * onboarding; toggleable in Settings.
     */
    val immersiveReader: Boolean = false,
    /**
     * Configurable two-finger reader gestures (owner's #11: defaults are fine,
     * but they must be optional — second-level). Both default on; a reader for
     * whom they misfire can switch either off in Settings › Gestures.
     */
    val twoFingerBrightness: Boolean = true,
    val twoFingerThemeFlick: Boolean = true,
    /** First-run onboarding has been completed or skipped (owner's #19). */
    val onboarded: Boolean = false,
    /**
     * Nooz Flash (owner's #6): today's flowed headlines compressed to a line of
     * 10 words or fewer, with a "go deeper" expansion. Off by default, like the
     * rest of the reader-intelligence tools — it's a standing generation over
     * everything that flowed, not a one-off the reader asked for.
     */
    val noozFlashEnabled: Boolean = false,
    /**
     * Nooz Cast (owner's ask): the full article read aloud in a natural
     * on-device voice, never the robotic system TTS. Gated independently of
     * Flash — its own model (Kokoro), its own download prompt, since one
     * being ready says nothing about the other. Off by default, same as
     * every reader-intelligence tool.
     */
    val noozCastEnabled: Boolean = false,
    /**
     * Today in History (owner's ask, 2026-08): a short dated column above the
     * day's stories, from Wikipedia's own curated "On this day" set. The app's
     * usual omission question, asked across time instead of across sources.
     *
     * Off by default, and for a sharper reason than the tools above: it is the
     * only fetch this app makes to a destination the reader didn't add
     * themselves (see the manifest's "no other egress" note, and
     * `TodayInHistoryRepository`'s own doc comment). Turning it on is a
     * decision the reader makes, never a default they'd have to discover.
     */
    val todayInHistoryEnabled: Boolean = false,
    val paperGrain: PaperGrain = PaperGrain.NONE,
    /** How a read article marks itself in the list (owner's ask). */
    val readMarkStyle: ReadMarkStyle = ReadMarkStyle.GREYED,
    /**
     * Immersive read-filter gesture on the Stand's list (owner's ask): pinch
     * in to show only unread, pinch out to show everything again. On by
     * default, like the reader's other two-finger gestures — an opt-out, not
     * an opt-in.
     */
    val unreadPinchFilter: Boolean = true,
    /**
     * Feed images (owner's ask, 2026-07): on by default once a feed supplies
     * one (enclosure / Media RSS / Atom image link / a first `<img>` in the
     * body — see [FeedParser]). An opt-out, not an opt-in.
     */
    val showFeedImages: Boolean = true,
    /**
     * NSFW image filtering (owner's ask): this is **not** an on-device
     * classifier — it's not this app's judgment to make at all. It hides an
     * item's image only when the *source's own feed* declared it adult/
     * explicit, via the two real conventions feeds actually use for this —
     * Media RSS's `<media:rating>` and the podcast `<itunes:explicit>` tag
     * (see [Item.declaredNsfw]/[FeedParser]). A source that declares nothing
     * is never touched or guessed at; absence always means "not flagged,"
     * exactly as both of those specs themselves define it.
     */
    val hideNsfwImages: Boolean = false,
    val imageStyle: ImageStyle = ImageStyle.HALFTONE,
    /**
     * Advanced settings (owner's ask): the reading lens's default loaded-word
     * / editorial-hedging terms a reader has individually turned off, by the
     * exact [BiasLexicon] term string (case-sensitive key, matched
     * case-insensitively at detect time same as everywhere else). Empty means
     * every default term is active, i.e. today's unqualified behaviour.
     */
    val lensDisabledDefaultTerms: Set<String> = emptySet(),
    /** Advanced settings: a reader's own added words/phrases, detected the same way as any default term. */
    val lensCustomTerms: Set<String> = emptySet(),
    /** How the reading aside presents itself (owner's ask) -- see [ReadingAsideStyle]. */
    val readingAsideStyle: ReadingAsideStyle = ReadingAsideStyle.QUOTE,
)

/**
 * The reader's standing filter (owner's Region & Topics flow): a longitude
 * sector picked on the globe plus an optional topic subset. An empty
 * [topicKeys] means all topics — the default the nav spec names
 * ("default global, all topics").
 */
data class ReaderFilter(
    val region: Region = Region.GLOBAL,
    val topicKeys: Set<String> = emptySet(),
) {
    val allTopics: Boolean get() = topicKeys.isEmpty()

    fun matchesTopic(topic: Topic): Boolean = allTopics || topic.key in topicKeys

    /**
     * Which sources flow under this region. Globally-tagged sources cover every
     * region (a world feed carries every sector's news); regionally-tagged
     * sources flow only in their own sector.
     */
    fun includesSource(sourceRegionTag: String?): Boolean {
        if (region == Region.GLOBAL) return true
        val tagged = Region.forSourceTag(sourceRegionTag)
        return tagged == Region.GLOBAL || tagged == region
    }

    /** Header summary, e.g. "Global | All" or "South Asia | Politics +2". */
    fun summary(): String {
        val topics = when {
            allTopics -> "All"
            topicKeys.size == 1 -> Topic.fromKey(topicKeys.first()).placeholderLabel
            else -> "${Topic.fromKey(topicKeys.first()).placeholderLabel} +${topicKeys.size - 1}"
        }
        return "${region.label} | $topics"
    }
}

/**
 * The globe's longitude sectors (owner's region-picker reference). Labels are
 * the owner's own sector names from that artifact — geography, not the
 * RESERVED taxonomy.
 */
enum class Region(val key: String, val label: String, val fromLon: Double, val toLon: Double) {
    GLOBAL("global", "Global", -180.0, 180.0),
    AMERICAS("americas", "Americas", -170.0, -30.0),
    EUROPE_AFRICA("europe-africa", "Europe & Africa", -30.0, 45.0),
    MIDEAST_CASIA("mideast-casia", "Middle East & C. Asia", 45.0, 72.0),
    SOUTH_ASIA("south-asia", "South Asia", 72.0, 95.0),
    EAST_SE_ASIA("east-se-asia", "East & SE Asia", 95.0, 148.0),
    AUSTRALIA_PACIFIC("australia-pacific", "Australia & Pacific", 148.0, 180.0);

    companion object {
        private val byKey = entries.associateBy(Region::key)
        fun fromKey(key: String?): Region = byKey[key] ?: GLOBAL

        /** Sector containing a (wrapped) longitude. The Australia/Pacific band wraps the antimeridian. */
        fun forLongitude(lon: Double): Region {
            val l = GlobeModel.wrapLon(lon)
            for (r in entries) {
                if (r == GLOBAL) continue
                if (r.fromLon <= r.toLon) {
                    if (l >= r.fromLon && l < r.toLon) return r
                } else if (l >= r.fromLon || l < r.toLon) {
                    return r
                }
            }
            // Left edge of the Americas band ([-180, -170) wraps into Australia & Pacific).
            return AUSTRALIA_PACIFIC
        }

        /** Map a source's declared region tag (catalogue schema: "global" | "india" | …) to a sector. */
        fun forSourceTag(tag: String?): Region = when (tag?.lowercase()) {
            null, "", "global" -> GLOBAL
            "india" -> SOUTH_ASIA
            // The catalogue's depth expansion split Europe and Africa into their
            // own tags; both fold back into the EUROPE_AFRICA display sector.
            "europe", "africa" -> EUROPE_AFRICA
            else -> byKey[tag.lowercase()] ?: GLOBAL
        }

        /**
         * Every sector a band centred on [centerLonDeg] with half-width
         * [bandHalfDeg] touches, widest-first-hit order (owner's #8: widening
         * the globe's pinch band used to have no effect at all short of the
         * full-global snap, so the picker only ever showed one sector or
         * "Global" — nothing in between). Built by sampling [forLongitude]
         * across the band rather than re-deriving sector-boundary/wraparound
         * math a second time, so it can never disagree with the single-point
         * lookup every other caller already relies on.
         */
        fun forBand(centerLonDeg: Double, bandHalfDeg: Double): List<Region> {
            if (bandHalfDeg >= GlobeModel.GLOBAL_BAND_THRESHOLD) return listOf(GLOBAL)
            val hit = LinkedHashSet<Region>()
            val step = 2.0
            var d = -bandHalfDeg
            while (d <= bandHalfDeg) {
                hit.add(forLongitude(centerLonDeg + d))
                d += step
            }
            return hit.toList()
        }
    }
}

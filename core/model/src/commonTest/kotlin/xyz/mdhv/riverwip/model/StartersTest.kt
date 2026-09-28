package xyz.mdhv.riverwip.model

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

class StartersTest {

    @Test fun everyVerifiedFeedHasResolvableKindAndUrl() {
        for (s in Starters.verifiedFeeds) {
            assertNotNull(s.sourceKind, "kind resolves: ${s.id}")
            assertTrue(!s.url.isNullOrBlank(), "has url: ${s.id}")
            // Every feed carries a real ISO verification date (feeds were verified
            // across more than one run — 2026-07-07 seed, 2026-07-11 all-region expansion).
            assertTrue(s.verifiedAt?.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) == true, "verified stamp: ${s.id}")
            assertNotNull(s.toSourceOrNull(addedAt = 1L), "addable: ${s.id}")
        }
    }

    @Test fun starterIdsAreUnique() {
        val all = Starters.seed.services.map { it.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun regionallyBalancedGlobalAndIndia() {
        val byRegion = Starters.feedsByRegion()
        assertTrue((byRegion["global"]?.size ?: 0) >= 5, "has global")
        assertTrue((byRegion["india"]?.size ?: 0) >= 5, "has india")
    }

    @Test fun indiaCoversTheMajorRegionalLanguages() {
        val india = Starters.feedsByRegion()["india"].orEmpty()
        val ids = india.map { it.id }.toSet()
        // One representative id per language, so the pack can grow or swap a
        // publisher without this test turning into a list that must be edited
        // in lockstep — but losing a whole language still fails.
        val perLanguage = mapOf(
            "Telugu" to "tv9-telugu-ap",
            "Tamil" to "thanthi-tv-tamil",
            "Kannada" to "tv9-kannada",
            "Malayalam" to "mathrubhumi-malayalam",
            "Marathi" to "tv9-marathi",
            "Gujarati" to "tv9-gujarati",
            "Bengali" to "abp-ananda-bengali",
            "Punjabi" to "abp-sanjha-punjabi",
            "Odia" to "dharitri-odia",
            "Urdu" to "siasat-urdu",
            "Hindi" to "abp-hindi",
        )
        for ((language, id) in perLanguage) {
            assertTrue(id in ids, "$language coverage (missing $id)")
        }
    }

    @Test fun indiaCoversHindiStateDesks() {
        val ids = Starters.feedsByRegion()["india"].orEmpty().map { it.id }.toSet()
        val states = ids.filter { it.startsWith("abp-hindi-") }
        assertTrue(states.size >= 13, "state desks present, was ${states.size}")
    }

    @Test fun languageNamesStayInTitlesSoSourceSearchCanFindThem() {
        // The sources search matches ServiceDef.title as a plain substring
        // (EditScreen), so a reader typing their language only finds these if
        // the language name is actually in the title. That coupling is easy to
        // break by "tidying" a title, so pin it.
        val titles = Starters.verifiedFeeds.map { it.title }
        for (language in listOf("Telugu", "Tamil", "Kannada", "Malayalam", "Marathi", "Gujarati", "Bengali", "Punjabi", "Odia", "Urdu", "Hindi")) {
            assertTrue(titles.any { it.contains(language, ignoreCase = true) }, "a title mentions $language")
        }
    }

    @Test fun noStarterUrlIsDuplicated() {
        // Two ids pointing at one endpoint is a catalogue bug that shows up as a
        // duplicated source the reader can add twice.
        val urls = Starters.verifiedFeeds.mapNotNull { it.url }
        val dupes = urls.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(dupes.isEmpty(), "duplicate feed urls: $dupes")
    }

    @Test fun coversAtLeastThreeSourceKindsEndToEnd() {
        // Gate: connect >=3 source kinds. The seed exposes rss + the builder kinds.
        val kinds = Starters.seed.services.mapNotNull { it.sourceKind }.toSet()
        assertTrue(kinds.contains(SourceKind.RSS))
        assertTrue(kinds.contains(SourceKind.GOOGLE_NEWS))
        assertTrue(kinds.contains(SourceKind.GDELT))
        assertTrue(kinds.contains(SourceKind.MASTODON))
        assertTrue(kinds.size >= 4, "at least 4 kinds seeded")
    }

    @Test fun keyedProvidersAreHonestlyLabeled() {
        val guardian = Starters.builders.first { it.id == "guardian-open-platform" }
        assertTrue(guardian.requiresKey)
        assertNotNull(guardian.keySignupUrl)
        val gnews = Starters.tierBReference.first { it.id == "gnews" }
        assertTrue(gnews.requiresKey)
        assertEquals(SourceKind.API, gnews.sourceKind)
    }
}

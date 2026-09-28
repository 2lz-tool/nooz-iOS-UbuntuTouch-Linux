package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartersTest {

    @Test fun everyVerifiedFeedHasResolvableKindAndUrl() {
        for (s in Starters.verifiedFeeds) {
            assertNotNull("kind resolves: ${s.id}", s.sourceKind)
            assertTrue("has url: ${s.id}", !s.url.isNullOrBlank())
            // Every feed carries a real ISO verification date (feeds were verified
            // across more than one run — 2026-07-07 seed, 2026-07-11 all-region expansion).
            assertTrue("verified stamp: ${s.id}", s.verifiedAt?.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) == true)
            assertNotNull("addable: ${s.id}", s.toSourceOrNull(addedAt = 1L))
        }
    }

    @Test fun starterIdsAreUnique() {
        val all = Starters.seed.services.map { it.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun regionallyBalancedGlobalAndIndia() {
        val byRegion = Starters.feedsByRegion()
        assertTrue("has global", (byRegion["global"]?.size ?: 0) >= 5)
        assertTrue("has india", (byRegion["india"]?.size ?: 0) >= 5)
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
            assertTrue("$language coverage (missing $id)", id in ids)
        }
    }

    @Test fun indiaCoversHindiStateDesks() {
        val ids = Starters.feedsByRegion()["india"].orEmpty().map { it.id }.toSet()
        val states = ids.filter { it.startsWith("abp-hindi-") }
        assertTrue("state desks present, was ${states.size}", states.size >= 13)
    }

    @Test fun languageNamesStayInTitlesSoSourceSearchCanFindThem() {
        // The sources search matches ServiceDef.title as a plain substring
        // (EditScreen), so a reader typing their language only finds these if
        // the language name is actually in the title. That coupling is easy to
        // break by "tidying" a title, so pin it.
        val titles = Starters.verifiedFeeds.map { it.title }
        for (language in listOf("Telugu", "Tamil", "Kannada", "Malayalam", "Marathi", "Gujarati", "Bengali", "Punjabi", "Odia", "Urdu", "Hindi")) {
            assertTrue("a title mentions $language", titles.any { it.contains(language, ignoreCase = true) })
        }
    }

    @Test fun noStarterUrlIsDuplicated() {
        // Two ids pointing at one endpoint is a catalogue bug that shows up as a
        // duplicated source the reader can add twice.
        val urls = Starters.verifiedFeeds.mapNotNull { it.url }
        val dupes = urls.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("duplicate feed urls: $dupes", dupes.isEmpty())
    }

    @Test fun coversAtLeastThreeSourceKindsEndToEnd() {
        // Gate: connect >=3 source kinds. The seed exposes rss + the builder kinds.
        val kinds = Starters.seed.services.mapNotNull { it.sourceKind }.toSet()
        assertTrue(kinds.contains(SourceKind.RSS))
        assertTrue(kinds.contains(SourceKind.GOOGLE_NEWS))
        assertTrue(kinds.contains(SourceKind.GDELT))
        assertTrue(kinds.contains(SourceKind.MASTODON))
        assertTrue("at least 4 kinds seeded", kinds.size >= 4)
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

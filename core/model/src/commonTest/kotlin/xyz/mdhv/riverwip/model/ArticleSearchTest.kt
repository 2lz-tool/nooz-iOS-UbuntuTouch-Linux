package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSearchTest {

    @Test fun tokenisesOnPunctuationAndWhitespace() {
        assertEquals(listOf("nepal", "floods"), ArticleSearch.terms("Nepal, floods."))
        assertEquals(listOf("upi", "123pay"), ArticleSearch.terms("UPI 123Pay"))
        // Hyphens split, and the "in" of "mail-in" is a function word — what
        // survives is the pair that actually narrows the search.
        assertEquals(listOf("mail", "voting"), ArticleSearch.terms("mail-in voting"))
        assertTrue(
            ArticleSearch.matchesPrefixes(
                "Democratic states sue to block postal service from carrying out Trump's mail-in voting order",
                ArticleSearch.terms("mail-in voting"),
            ),
        )
    }

    @Test fun dropsSingleCharacterAndEmptyTokens() {
        // A one-letter term matches most of the corpus; it is noise, not a filter.
        assertEquals(listOf("australia"), ArticleSearch.terms("a of   Australia"))
        assertEquals(emptyList<String>(), ArticleSearch.terms("   "))
        assertEquals(emptyList<String>(), ArticleSearch.terms("!!! ??"))
    }

    @Test fun dropsFunctionWordsThatWouldOtherwiseExcludeTheAnswer() {
        // Terms are ANDed, so a surviving "in" would demand a word starting
        // "in" — which "Flash floods on the Nepal-Tibet border" hasn't got.
        assertEquals(listOf("floods", "nepal"), ArticleSearch.terms("floods in nepal"))
        assertTrue(
            ArticleSearch.matchesPrefixes(
                "Flash floods on the Nepal-Tibet border leave scores dead",
                ArticleSearch.terms("floods in nepal"),
            ),
        )
    }

    @Test fun aQueryThatIsOnlyFunctionWordsStillSearchesForThem() {
        // Dropping every term would turn a real query into "no query", which
        // shows the whole stand — the opposite of what was asked for.
        assertEquals(listOf("the"), ArticleSearch.terms("the"))
        assertEquals("the*", ArticleSearch.toMatchQuery("The"))
    }

    @Test fun deduplicatesAndCapsTermCount() {
        assertEquals(listOf("flood"), ArticleSearch.terms("flood FLOOD Flood"))
        val many = ArticleSearch.terms((1..30).joinToString(" ") { "word$it" })
        assertEquals(8, many.size)
    }

    @Test fun matchQueryPrefixesEveryTermAndAndsThem() {
        assertEquals("nepal* floods*", ArticleSearch.toMatchQuery("Nepal floods"))
        assertEquals("austral*", ArticleSearch.toMatchQuery("austral"))
    }

    @Test fun termsAreJoinedByWhitespaceBecauseThatIsHowFtsSpellsAnd() {
        // SQLite builds FTS4 with standard query syntax unless compiled with
        // SQLITE_ENABLE_FTS3_PARENTHESIS. There, whitespace already means AND
        // and the word "AND" is just another term to match — so joining with
        // " AND " demands the article contain the literal word "and". Most
        // prose does, which is why this would have hidden rather than failed.
        val q = ArticleSearch.toMatchQuery("dozen lawsuit")!!
        assertEquals("dozen* lawsuit*", q)
        assertFalse("no bare AND operator in the expression", q.contains(" AND "))
    }

    @Test fun matchQueryIsNullWhenThereIsNothingToSearchFor() {
        // Null means "no query", never "match nothing" — an empty search box
        // must show everything, not an empty stand.
        assertNull(ArticleSearch.toMatchQuery(""))
        assertNull(ArticleSearch.toMatchQuery("   "))
        assertNull(ArticleSearch.toMatchQuery("- ! ?"))
        assertNull(ArticleSearch.toMatchQuery("a"))
    }

    @Test fun ftsOperatorSyntaxCannotReachTheQuery() {
        // Everything non-alphanumeric is a separator, so operators are stripped
        // structurally rather than escaped — there is no quoting to get wrong.
        // These would otherwise be FTS syntax (or a syntax error).
        assertEquals("nepal* flood*", ArticleSearch.toMatchQuery(""" "nepal" OR flood* """))
        assertEquals("title* nepal*", ArticleSearch.toMatchQuery("title:nepal"))
        assertEquals("nepal* near* india*", ArticleSearch.toMatchQuery("nepal NEAR/5 india"))
        assertEquals("flood*", ArticleSearch.toMatchQuery("^flood"))
        assertEquals("flood*", ArticleSearch.toMatchQuery("-flood"))
        // A lone quote used to be the classic way to blow up an FTS query.
        assertEquals("flood*", ArticleSearch.toMatchQuery("flood\""))
    }

    @Test fun tokenisesNonLatinScriptsWithoutBreakingCharacterClusters() {
        // Indic scripts write vowels and the virama as combining marks, which
        // are NOT isLetterOrDigit(). Splitting on them tore these words into
        // fragments — search was broken for exactly the languages the
        // catalogue just gained feeds in.
        assertEquals(listOf("ఆంధ్ర", "ప్రదేశ్"), ArticleSearch.terms("ఆంధ్ర ప్రదేశ్"))
        assertEquals(listOf("मोदी", "पुतिन"), ArticleSearch.terms("मोदी, पुतिन"))
        assertEquals(listOf("ଓଡ଼ିଶା"), ArticleSearch.terms("ଓଡ଼ିଶା"))
        assertEquals("ఆంధ్ర* ప్రదేశ్*", ArticleSearch.toMatchQuery("ఆంధ్ర ప్రదేశ్"))
        // ...and the two halves of the search still agree with each other.
        assertTrue(ArticleSearch.matchesPrefixes("ఆంధ్ర ప్రదేశ్ వార్తలు", ArticleSearch.terms("ఆంధ్ర")))
    }

    @Test fun snippetShowsWhySomethingMatched() {
        val body = "Two dozen states and the District of Columbia filed a fresh lawsuit on Wednesday " +
            "to block Donald Trump's executive order restricting mail voting in this year's midterm elections."
        val snippet = ArticleSearch.snippet(body, listOf("lawsuit"), radius = 30)!!
        assertTrue("contains the term: $snippet", snippet.contains("lawsuit"))
        assertTrue("elided at the start: $snippet", snippet.startsWith("…"))
    }

    @Test fun snippetNeverCutsMidWord() {
        val body = "alpha bravo charlie delta echo foxtrot golf hotel india juliett kilo lima mike"
        val snippet = ArticleSearch.snippet(body, listOf("hotel"), radius = 12)!!
        val words = snippet.trim('…').trim().split(" ")
        for (w in words) {
            assertTrue("whole word, got '$w' in: $snippet", body.contains(w))
        }
    }

    @Test fun snippetPicksTheEarliestMatchingTerm() {
        val body = "Kathmandu first, then Sydney much later in the piece."
        val snippet = ArticleSearch.snippet(body, listOf("sydney", "kathmandu"), radius = 10)!!
        assertTrue("anchored on the earliest hit: $snippet", snippet.contains("Kathmandu"))
        assertFalse("did not start at the later hit: $snippet", snippet.startsWith("…"))
    }

    @Test fun snippetCollapsesWhitespaceSoExtractedPagesStayOneLine() {
        // Extracted bodies are paragraph-joined with "\n\n"; a snippet spanning
        // a paragraph break must not render as a gap in the results list.
        val body = "The first paragraph ends here.\n\n   The second one mentions monsoon rain."
        val snippet = ArticleSearch.snippet(body, listOf("monsoon"))!!
        assertFalse("no newlines: $snippet", snippet.contains("\n"))
        assertFalse("no double spaces: $snippet", snippet.contains("  "))
    }

    @Test fun snippetIsNullWhenNothingMatchesSoCallersCanFallBack() {
        assertNull(ArticleSearch.snippet("nothing relevant here", listOf("monsoon")))
        assertNull(ArticleSearch.snippet("", listOf("monsoon")))
        assertNull(ArticleSearch.snippet("some text", emptyList()))
    }

    @Test fun prefixMatchingMirrorsWhatFtsIsAsked() {
        val title = "Flash floods on the Nepal-Tibet border leave scores dead"
        assertTrue(ArticleSearch.matchesPrefixes(title, listOf("flood")))
        assertTrue(ArticleSearch.matchesPrefixes(title, listOf("nepal", "border")))
        // Prefix, not substring: FTS matches the start of a word, so "ibet"
        // must not match "Tibet" here either, or the two halves of the search
        // would disagree about the same query.
        assertFalse(ArticleSearch.matchesPrefixes(title, listOf("ibet")))
        // All terms must be present — AND, matching toMatchQuery.
        assertFalse(ArticleSearch.matchesPrefixes(title, listOf("nepal", "monsoon")))
        assertFalse(ArticleSearch.matchesPrefixes(title, emptyList()))
    }

    @Test fun prefixMatchingIsCaseInsensitiveBothWays() {
        assertTrue(ArticleSearch.matchesPrefixes("MONSOON RAIN", listOf("monsoon")))
        assertTrue(ArticleSearch.matchesPrefixes("monsoon rain", ArticleSearch.terms("MONSOON")))
    }
}

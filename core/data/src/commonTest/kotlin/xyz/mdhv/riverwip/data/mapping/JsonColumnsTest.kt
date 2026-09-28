package xyz.mdhv.riverwip.data.mapping

import kotlin.test.assertEquals
import kotlin.test.Test
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.TopicEvidence

class JsonColumnsTest {

    @Test fun topicEvidenceRoundTrips() {
        val evidence = listOf(
            TopicEvidence(Topic.POLITICS, "lexicon:politics", listOf("election", "parliament")),
            TopicEvidence(Topic.SPORT, "feedcat:sport", listOf("Sports")),
        )
        val json = TopicEvidenceJson.encode(evidence)
        val decoded = TopicEvidenceJson.decode(json)
        assertEquals(evidence, decoded)
    }

    @Test fun emptyEvidenceRoundTrips() {
        assertEquals(emptyList<TopicEvidence>(), TopicEvidenceJson.decode(TopicEvidenceJson.encode(emptyList())))
    }

    @Test fun malformedEvidenceJsonYieldsEmpty() {
        assertEquals(emptyList<TopicEvidence>(), TopicEvidenceJson.decode("not json"))
        assertEquals(emptyList<TopicEvidence>(), TopicEvidenceJson.decode(""))
    }

    @Test fun countMapRoundTrips() {
        val map = mapOf("politics" to 5, "sport" to 12, "other" to 0)
        assertEquals(map, CountMapJson.decode(CountMapJson.encode(map)))
    }

    @Test fun emptyCountMapRoundTrips() {
        assertEquals(emptyMap<String, Int>(), CountMapJson.decode(CountMapJson.encode(emptyMap())))
    }

    @Test fun malformedCountMapJsonYieldsEmpty() {
        assertEquals(emptyMap<String, Int>(), CountMapJson.decode("{not json"))
        assertEquals(emptyMap<String, Int>(), CountMapJson.decode(""))
    }
}

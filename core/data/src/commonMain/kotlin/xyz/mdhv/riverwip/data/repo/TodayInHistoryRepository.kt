package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.IoDispatcher
import xyz.mdhv.riverwip.data.DataPlatform
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.model.HistoricalEvent
import xyz.mdhv.riverwip.model.TodayInHistory
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** The one shape this repository persists — [HistoricalEvent] itself stays annotation-free in `:core:model`. */
@Serializable
private data class CachedEvent(val year: Int, val text: String, val url: String? = null)

// Wikimedia's own response, narrowed to the three fields this app reads. Every
// other field it returns (thumbnails, extracts, revision ids, coordinates —
// the bulk of a ~140KB payload) is ignored rather than modelled.
@Serializable
private data class OnThisDayResponse(val selected: List<SelectedEvent> = emptyList())

@Serializable
private data class SelectedEvent(
    val year: Int? = null,
    val text: String = "",
    val pages: List<EventPage> = emptyList(),
)

@Serializable
private data class EventPage(@Suppress("PropertyName") val content_urls: ContentUrls? = null)

@Serializable
private data class ContentUrls(val desktop: DesktopUrls? = null)

@Serializable
private data class DesktopUrls(val page: String? = null)

/**
 * Today in History's data half (owner's ask, 2026-08). Reads Wikipedia's own
 * curated "On this day" set for the current date; [TodayInHistory] does the
 * selecting and tidying.
 *
 * **This is the app's first fetch to somewhere the reader didn't choose.**
 * Every other request this app makes goes to a feed the reader added
 * themselves (see the manifest's own note: "The user's own fetches to their
 * own chosen sources. No other egress"). That's why the feature ships off by
 * default and names Wikipedia plainly in its settings row: turning it on is
 * the reader deciding to add one destination, not something that happens to
 * them silently.
 *
 * Fetches at most once per calendar day. The column doesn't change between
 * one glance and the next, so re-fetching every time the Stand scrolls back
 * to the top would be pure waste against someone else's servers.
 */
class TodayInHistoryRepository(
    private val platform: DataPlatform,
    // A UA that identifies the app and offers a way to be reached, per
    // Wikimedia's user-agent policy — their servers are entitled to know who
    // is calling and to have somewhere to complain to.
    private val http: HttpClient = HttpClient(userAgent = USER_AGENT),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val eventListSerializer = ListSerializer(CachedEvent.serializer())

    private object Keys {
        val CACHED_DATE = stringPreferencesKey("cached_date")
        val CACHED_EVENTS = stringPreferencesKey("cached_events")
    }

    /**
     * The column for [date], from cache when it was already fetched today.
     * Failure is returned rather than thrown or silently swallowed, so the
     * card can say so plainly instead of pretending the day had no history.
     */
    suspend fun column(date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Result<List<HistoricalEvent>> =
        withContext(IoDispatcher) {
            cached(date)?.let { return@withContext Result.success(TodayInHistory.column(it)) }
            runCatching {
                val url = "$BASE_URL/${date.monthNumber.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')}"
                val response = http.get(url)
                if (!response.isSuccess) error("HTTP ${response.code}")

                val events = json.decodeFromString(OnThisDayResponse.serializer(), response.body).selected
                    .mapNotNull { event ->
                        val year = event.year ?: return@mapNotNull null
                        val text = TodayInHistory.clean(event.text)
                        if (text.isBlank()) return@mapNotNull null
                        HistoricalEvent(
                            year = year,
                            text = text,
                            url = event.pages.firstNotNullOfOrNull { it.content_urls?.desktop?.page },
                        )
                    }
                if (events.isEmpty()) error("no events for this date")
                store(date, events)
                TodayInHistory.column(events)
            }
        }

    private suspend fun cached(date: LocalDate): List<HistoricalEvent>? {
        val prefs: Preferences = runCatching { platform.stores.todayInHistory.data.firstOrNull() }
            .getOrNull() ?: return null
        if (prefs[Keys.CACHED_DATE] != date.toString()) return null
        val raw = prefs[Keys.CACHED_EVENTS] ?: return null
        // A cache that no longer parses (a shape change across an app update)
        // reads as absent and refetches, rather than failing the whole card.
        return runCatching {
            json.decodeFromString(eventListSerializer, raw).map { HistoricalEvent(it.year, it.text, it.url) }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun store(date: LocalDate, events: List<HistoricalEvent>) {
        val payload = json.encodeToString(eventListSerializer, events.map { CachedEvent(it.year, it.text, it.url) })
        platform.stores.todayInHistory.edit { prefs ->
            prefs[Keys.CACHED_DATE] = date.toString()
            prefs[Keys.CACHED_EVENTS] = payload
        }
    }

    companion object {
        /**
         * Wikipedia's own editorially curated highlights for a date, not the
         * unfiltered `events` list (hundreds of entries, most of them minor).
         * A newspaper's column is a chosen handful, and this endpoint is
         * already exactly that.
         */
        const val BASE_URL = "https://api.wikimedia.org/feed/v1/wikipedia/en/onthisday/selected"

        const val USER_AGENT = "Nooz/0.2 (+https://asystemofcells.com/contact)"

        /** Wikipedia text is CC BY-SA licensed, so the card credits it in view, not buried in an About screen. */
        const val ATTRIBUTION = "Wikipedia, CC BY-SA 4.0"
        const val SOURCE_URL = "https://en.wikipedia.org/wiki/Wikipedia:On_this_day"
    }
}

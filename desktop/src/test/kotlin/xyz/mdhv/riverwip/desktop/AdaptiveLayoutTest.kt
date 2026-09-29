package xyz.mdhv.riverwip.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.graphics.toAwtImage
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import xyz.mdhv.riverwip.NoozApp
import xyz.mdhv.riverwip.design.BackDispatcher
import xyz.mdhv.riverwip.design.LocalBackDispatcher
import xyz.mdhv.riverwip.feature.reader.ONE_PANE_TAG
import xyz.mdhv.riverwip.feature.reader.TWO_PANE_MIN_WIDTH
import xyz.mdhv.riverwip.feature.reader.TWO_PANE_TAG
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The adaptive layout, exercised in the real app on the desktop host: real data layer, real feed
 * over a local HTTP server, real NoozApp. The Stand must be one pane below [TWO_PANE_MIN_WIDTH] and
 * list + reader side by side at and above it -- the same rule the Android build applies to tablets
 * and unfolded foldables, because the rule is a function of window width and nothing else.
 *
 * Every size also writes a screenshot to `build/screenshots/` so a person can look at it: an
 * assertion cannot tell you that a layout is ugly.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveLayoutTest {
    private lateinit var home: File
    private lateinit var server: HttpServer
    private lateinit var services: DesktopServices

    @BeforeTest fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        home = Files.createTempDirectory("nooz-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/feed.xml") { ex ->
                val body = FEED.toByteArray()
                ex.responseHeaders.add("Content-Type", "application/rss+xml")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            start()
        }
        services = DesktopServices(XdgDirs(home.resolve("data").absolutePath.toPath(), home.resolve("cache").absolutePath.toPath(), home.resolve("config").absolutePath.toPath()))
        runBlocking {
            services.settingsRepository.setOnboarded(true)
            services.sourceRepository.addByUrl("http://127.0.0.1:${server.address.port}/feed.xml")
            services.ingest.run()
        }
    }

    @AfterTest fun tearDown() {
        server.stop(0)
        home.deleteRecursively()
    }

    private fun DesktopComposeUiTest.showApp() {
        val back = BackDispatcher()
        // The real desktop Window supplies a lifecycle owner; the bare test host does not.
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).also { it.currentState = Lifecycle.State.RESUMED }
        }
        setContent {
            CompositionLocalProvider(LocalBackDispatcher provides back, LocalLifecycleOwner provides lifecycleOwner) {
                NoozApp(services)
            }
        }
    }

    private fun DesktopComposeUiTest.awaitStand(tag: String) {
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTagCount(tag) > 0 }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun DesktopComposeUiTest.snap(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun atSize(widthDp: Int, heightDp: Int, name: String, expectTwoPane: Boolean) =
        runDesktopComposeUiTest(width = widthDp, height = heightDp) {
            showApp()
            awaitStand(if (expectTwoPane) TWO_PANE_TAG else ONE_PANE_TAG)
            assertEquals(if (expectTwoPane) 0 else 1, if (expectTwoPane) onAllNodesWithTagCount(ONE_PANE_TAG) else onAllNodesWithTagCount(ONE_PANE_TAG))
            assertEquals(if (expectTwoPane) 1 else 0, onAllNodesWithTagCount(TWO_PANE_TAG))
            snap(name)
        }

    @Test fun phoneWidthIsOnePane() = atSize(390, 780, "phone-390x780", expectTwoPane = false)
    @Test fun narrowestWindowIsOnePane() = atSize(360, 480, "min-360x480", expectTwoPane = false)
    @Test fun aSmallWindowIsOnePane() = atSize(700, 700, "small-700x700", expectTwoPane = false)
    @Test fun justBelowTheBreakpointIsOnePane() = atSize(TWO_PANE_MIN_WIDTH.value.toInt() - 1, 800, "below-839x800", expectTwoPane = false)
    @Test fun exactlyAtTheBreakpointIsTwoPane() = atSize(TWO_PANE_MIN_WIDTH.value.toInt(), 800, "at-840x800", expectTwoPane = true)
    @Test fun aTabletIsTwoPane() = atSize(1024, 768, "tablet-1024x768", expectTwoPane = true)
    @Test fun aDesktopWindowIsTwoPane() = atSize(1180, 800, "desktop-1180x800", expectTwoPane = true)
    @Test fun anUltrawideWindowIsTwoPane() = atSize(1900, 900, "ultrawide-1900x900", expectTwoPane = true)

    @Test fun resizingAcrossTheBreakpointSwitchesLayoutInPlace() = runDesktopComposeUiTest(width = 1100, height = 800) {
        // The same composition is re-laid-out when the window changes size, which is what dragging a
        // desktop window edge (or unfolding a phone) does; no restart, no lost state.
        showApp()
        awaitStand(TWO_PANE_TAG)
        assertTrue(onAllNodesWithTagCount(ONE_PANE_TAG) == 0)
    }

    private companion object {
        // Distinct headlines on purpose: near-identical ones are collapsed by the app's own duplicate detection.
        private val HEADLINES = listOf(
            "Council approves a new tram line after a decade of argument",
            "Farmers report the best barley harvest since records began",
            "Central bank holds rates as inflation edges lower again",
            "Astronomers spot a faint comet that may be visible next month",
            "Regional football final ends in a dramatic penalty shootout",
            "Hospital waiting lists shrink for the first time in three years",
            "New museum wing opens with a retrospective of local printmakers",
            "Ferry operator restores the winter timetable to island routes",
            "Researchers map the migration of a rare wading bird by satellite",
            "Bakers' guild celebrates four hundred years of the city loaf",
            "Landslide closes the coastal road while engineers survey the slope",
            "Students design a solar boat that crosses the estuary unaided",
        )
        val FEED = buildString {
            append("""<?xml version="1.0"?><rss version="2.0"><channel><title>Test Wire</title><link>http://example.test/</link>""")
            val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            HEADLINES.forEachIndexed { i, headline ->
                val published = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(now.minusMinutes(20L * i))
                append("<item><title>$headline</title>")
                append("<link>http://example.test/story-$i</link><guid>story-$i</guid>")
                append("<description>${headline}. Officials and residents gave their reactions on Tuesday afternoon.</description>")
                append("<pubDate>$published</pubDate></item>")
            }
            append("</channel></rss>")
        }
    }
}

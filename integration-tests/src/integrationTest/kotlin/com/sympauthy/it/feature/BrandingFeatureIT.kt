package com.sympauthy.it.feature

import com.sun.net.httpserver.HttpServer
import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import com.sympauthy.testcontainers.SympauthyContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.testcontainers.Testcontainers
import org.testcontainers.images.builder.Transferable
import java.net.InetSocketAddress
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Feature scenario — **a deployment's brand is answered under `/branding` from its configuration, and the
 * browser only ever asks this server for it.**
 *
 * With nothing configured the paths answer SympAuthy's own tokens and marks. A configured colour is written over
 * its default, a configured file is answered as it is, and a configured URL is fetched once and answered from
 * memory, so the host hears nothing as pages are read. A host that is down degrades the image's condition without
 * taking the server down, and an answer that is no image is not an outage at all.
 *
 * No unit test reaches this. Whether the YAML binds to the roles, whether the routes answer anonymously with the
 * headers they set, and whether the tokens and marks are in the native image are what the running server says,
 * and a fetch leaves the container for a host on the machine running the test.
 *
 * Issue: [#200](https://github.com/sympauthy/sympauthy/issues/200), and `docs/design/branding.md`.
 */
@Tag("feature")
class BrandingFeatureIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "answers the default brand with nothing configured on {0}")
    @EnumSource(Database::class)
    fun answersSympAuthysBrandWithNothingConfigured(database: Database) {
        withBrandedContainer(database, emptyMap()) { sympauthy ->
            val stylesheet = get(sympauthy, "/branding/tokens.css")
            assertEquals(200, stylesheet.statusCode())
            assertEquals("text/css", stylesheet.headers().firstValue("Content-Type").get())
            assertTrue("--primary:" in stylesheet.body(), "the design system's tokens are in the native image")
            assertTrue("--space-" in stylesheet.body(), "the tokens that cannot be changed are answered too")

            listOf("icon", "logo", "logo-dark").forEach { image ->
                val answer = get(sympauthy, "/branding/$image")
                assertEquals(200, answer.statusCode(), "$image answers anonymously")
                assertTrue(answer.headers().firstValue("Content-Type").get().startsWith("image/"))
                assertTrue(answer.body().isNotEmpty(), "$image is SympAuthy's mark")
                assertEquals(
                    "default-src 'none'; style-src 'unsafe-inline'; sandbox",
                    answer.headers().firstValue("Content-Security-Policy").get(),
                )
                assertEquals("nosniff", answer.headers().firstValue("X-Content-Type-Options").get())
            }

            val brand = get(sympauthy, "/branding/brand.json")
            assertEquals("*", brand.headers().firstValue("Access-Control-Allow-Origin").get())
            assertTrue(""""dark_mode":"auto"""" in brand.body(), brand.body())

            val etag = stylesheet.headers().firstValue("ETag").get()
            assertEquals("no-cache", stylesheet.headers().firstValue("Cache-Control").get())
            assertEquals(304, get(sympauthy, "/branding/tokens.css", "If-None-Match" to etag).statusCode())
        }
    }

    @ParameterizedTest(name = "writes the configured colours and answers the configured file on {0}")
    @EnumSource(Database::class)
    fun answersTheDeploymentsColoursAndFile(database: Database) {
        val config = uiConfig(
            "display-name" to "Acme",
            "logo" to LOGO_PATH,
            "theme" to mapOf(
                "light" to mapOf("primary" to "#0f766e", "ink-50" to "#111111", "accent" to "#222222"),
                "dark" to mapOf("primary" to "#84c2ba"),
            ),
        )
        withBrandedContainer(database, config) { sympauthy ->
            val stylesheet = get(sympauthy, "/branding/tokens.css").body()
            assertTrue("--primary: #0f766e;" in stylesheet)
            assertTrue("--ink-50: #111111;" in stylesheet, "a role whose key no getter hyphenates to binds")
            assertTrue("--gold: #222222;" in stylesheet)
            assertTrue("--primary-hover:" in stylesheet, "a role left out is still answered")
            assertFalse("--primary-hover: #0f766e;" in stylesheet, "a role left out is not derived from primary")
            assertTrue("--primary: #84c2ba;" in stylesheet)
            assertTrue("@media (prefers-color-scheme: dark)" in stylesheet)

            assertEquals(SVG, get(sympauthy, "/branding/logo").body())
            assertEquals(SVG, get(sympauthy, "/branding/logo-dark").body(), "the logo stands in for the dark one")
            assertEquals(
                """{"display_name":"Acme","dark_mode":"auto"}""",
                get(sympauthy, "/branding/brand.json").body(),
            )
        }
    }

    @ParameterizedTest(name = "leaves the dark roles out under dark-mode off on {0}")
    @EnumSource(Database::class)
    fun leavesTheDarkRolesOutUnderDarkModeOff(database: Database) {
        withBrandedContainer(database, uiConfig("theme" to mapOf("dark-mode" to "off"))) { sympauthy ->
            val stylesheet = get(sympauthy, "/branding/tokens.css").body()
            assertFalse("prefers-color-scheme" in stylesheet)
            assertFalse("""data-theme="dark"""" in stylesheet)
            assertTrue(""""dark_mode":"off"""" in get(sympauthy, "/branding/brand.json").body())
        }
    }

    @ParameterizedTest(name = "fetches an image once and answers it from memory on {0}")
    @EnumSource(Database::class)
    fun fetchesAnImageOnceAndAnswersItFromMemory(database: Database) {
        val requests = AtomicInteger()
        withImageHost(status = { requests.incrementAndGet(); 200 }) { url ->
            withBrandedContainer(database, uiConfig("logo" to url)) { sympauthy ->
                val logo = awaitBody(sympauthy, "/branding/logo") { it == SVG }
                assertEquals(SVG, logo)
                repeat(3) { get(sympauthy, "/branding/logo") }
                assertEquals(1, requests.get(), "the host hears nothing as pages are read")
            }
        }
    }

    @ParameterizedTest(name = "an image host being down degrades and recovers on {0}")
    @EnumSource(Database::class)
    fun anImageHostBeingDownDegradesAndRecovers(database: Database) {
        val answering = AtomicBoolean(false)
        withImageHost(status = { if (answering.get()) 200 else 503 }) { url ->
            withBrandedContainer(database, uiConfig("logo" to url)) { sympauthy ->
                assertNotEquals(SVG, get(sympauthy, "/branding/logo").body(), "the logo answers as if not configured")
                val health = awaitBody(sympauthy, "/health") { "DEGRADED" in it }
                assertTrue(""""logo"""" in health, health)
                assertEquals(200, get(sympauthy, "/health").statusCode())

                answering.set(true)

                awaitBody(sympauthy, "/branding/logo") { it == SVG }
                awaitBody(sympauthy, "/health") { "DEGRADED" !in it }
            }
        }
    }

    @ParameterizedTest(name = "an answer that is no image is no outage on {0}")
    @EnumSource(Database::class)
    fun anAnswerThatIsNoImageIsNoOutage(database: Database) {
        val requests = AtomicInteger()
        withImageHost(status = { requests.incrementAndGet(); 404 }) { url ->
            withBrandedContainer(database, uiConfig("logo" to url)) { sympauthy ->
                await { requests.get() > 0 }
                val logo = get(sympauthy, "/branding/logo")
                assertEquals(200, logo.statusCode())
                assertNotEquals(SVG, logo.body(), "the logo answers as if it were not configured")
                val health = get(sympauthy, "/health")
                assertEquals(200, health.statusCode())
                assertFalse("DEGRADED" in health.body(), health.body())
            }
        }
    }

    private fun uiConfig(vararg ui: Pair<String, Any>): Map<String, Any> = mapOf("ui" to mapOf(*ui))

    private fun withBrandedContainer(
        database: Database,
        config: Map<String, Any>,
        block: (SympauthyContainer) -> Unit,
    ) = withCustomContainer(
        database,
        build = { fixture, registry ->
            container(fixture, config(registry) and ADMIN_TURNED_OFF and config, registry)
                .withCopyToContainer(Transferable.of(SVG), LOGO_PATH)
        },
        block = { sympauthy, _ -> block(sympauthy) },
    )

    /**
     * Runs [block] with the address of a host on this machine, reachable from the containers started in it, which
     * answers [SVG] under the status [status] gives.
     */
    private fun withImageHost(status: () -> Int, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/logo.svg") { exchange ->
            val body = SVG.toByteArray()
            exchange.sendResponseHeaders(status(), body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            Testcontainers.exposeHostPorts(server.address.port)
            block("http://host.testcontainers.internal:${server.address.port}/logo.svg")
        } finally {
            server.stop(0)
        }
    }

    private fun get(
        sympauthy: SympauthyContainer,
        path: String,
        vararg headers: Pair<String, String>,
    ): HttpResponse<String> = httpGet("${sympauthy.baseUrl}$path", mapOf("Accept" to "*/*", *headers))

    /**
     * The body of [path] once [accepted] holds of it: an image is fetched after the server is up, and a condition
     * recovers over a retry and a quiet period.
     */
    private fun awaitBody(sympauthy: SympauthyContainer, path: String, accepted: (String) -> Boolean): String {
        var body: String? = null
        await {
            body = get(sympauthy, path).body()
            accepted(body.orEmpty())
        }
        return body!!
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + AWAIT_LIMIT.toNanos()
        while (!condition()) {
            assertTrue(System.nanoTime() < deadline, "not reached within $AWAIT_LIMIT")
            Thread.sleep(AWAIT_INTERVAL.toMillis())
        }
    }

    private companion object {
        /**
         * A deployment whose configuration is otherwise whole, so that `/health` says what the images did: the
         * brand answers with the admin turned off as well.
         */
        val ADMIN_TURNED_OFF: Map<String, Any> = mapOf(
            "admin" to mapOf("enabled" to false, "integrated-ui" to false, "audience" to "none"),
        )

        const val LOGO_PATH = "/branding/logo.svg"
        const val SVG = """<svg xmlns="http://www.w3.org/2000/svg"><title>Acme</title></svg>"""

        /**
         * A retry and a quiet period, each thirty seconds, and a second for the job ending the quiet period.
         */
        val AWAIT_LIMIT: Duration = Duration.ofSeconds(90)
        val AWAIT_INTERVAL: Duration = Duration.ofMillis(500)
    }
}

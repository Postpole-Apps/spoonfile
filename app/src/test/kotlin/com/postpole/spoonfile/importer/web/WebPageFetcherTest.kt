package com.postpole.spoonfile.importer.web

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The server is plain http on localhost, so these tests build the fetcher with the https check off;
 * [httpsOnlyByDefault] covers the real configuration.
 */
class WebPageFetcherTest {
    private lateinit var server: MockWebServer
    private val fetcher = WebPageFetcher(OkHttpClient(), requireHttps = false, timeoutMillis = 5_000)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)

    private fun page(path: String = "/") = runBlocking { fetcher.fetchPage(server.url(path).toString()) }

    @Test
    fun fetchesHtmlWithBrowserHeadersAndNoCookies() {
        server.enqueue(html("<html>hi</html>").setHeader("Set-Cookie", "a=b"))
        server.enqueue(html("<html>again</html>"))
        val first = page() as FetchResult.Page
        assertEquals("<html>hi</html>", first.html)
        page()
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Mozilla/5.0"))
        assertTrue(request.getHeader("Accept")!!.startsWith("text/html"))
        server.takeRequest().let { assertNull("cookies must not be stored", it.getHeader("Cookie")) }
    }

    @Test
    fun followsRedirectsAndReportsTheFinalUrl() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/next"))
        server.enqueue(html("<html>final</html>"))
        val result = page() as FetchResult.Page
        assertEquals("<html>final</html>", result.html)
        assertTrue(result.finalUrl.endsWith("/next"))
    }

    @Test
    fun givesUpAfterFiveRedirects() {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/loop")) }
        assertEquals(FetchResult.HttpError(null), page())
        assertEquals(6, server.requestCount)
    }

    @Test
    fun fiveRedirectsStillSucceed() {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/hop")) }
        server.enqueue(html("<html>ok</html>"))
        assertTrue(page() is FetchResult.Page)
    }

    @Test
    fun httpErrorsCarryTheStatus() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(FetchResult.HttpError(404), page())
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.HttpError(403), page())
    }

    @Test
    fun rejectsNonHtmlContent() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF"))
        assertEquals(FetchResult.NotHtml, page())
    }

    @Test
    fun acceptsXhtml() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/xhtml+xml").setBody("<html/>"))
        assertTrue(page() is FetchResult.Page)
    }

    @Test
    fun rejectsBodiesOverFiveMegabytes() {
        val big = Buffer().write(ByteArray(WebPageFetcher.MAX_PAGE_BYTES + 1))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(big))
        assertEquals(FetchResult.TooBig, page())
    }

    @Test
    fun rejectsOversizedChunkedBodiesWithoutALength() {
        val big = Buffer().write(ByteArray(WebPageFetcher.MAX_PAGE_BYTES + 1))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setChunkedBody(big, 64 * 1024))
        assertEquals(FetchResult.TooBig, page())
    }

    @Test
    fun readsTheCharsetFromTheHeader() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=iso-8859-1")
                .setBody(Buffer().write("café".toByteArray(Charsets.ISO_8859_1))),
        )
        assertEquals("café", (page() as FetchResult.Page).html)
    }

    @Test
    fun silentServerTimesOut() {
        val quick = WebPageFetcher(OkHttpClient(), requireHttps = false, timeoutMillis = 300)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(FetchResult.Timeout, runBlocking { quick.fetchPage(server.url("/").toString()) })
    }

    @Test
    fun defaultTimeoutIsFifteenSeconds() {
        assertEquals(15L, WebPageFetcher.TIMEOUT_SECONDS)
    }

    @Test
    fun httpsOnlyByDefault() {
        val strict = WebPageFetcher()
        val result = runBlocking { strict.fetchPage(server.url("/").toString()) }
        assertEquals(FetchResult.Insecure, result)
        assertEquals("nothing may be requested over http", 0, server.requestCount)
    }

    @Test
    fun fetchesImages() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes)))
        assertArrayEquals(bytes, runBlocking { fetcher.fetchImage(server.url("/p.jpg").toString()) })
    }

    @Test
    fun imageMustBeAnImage() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html/>"))
        assertNull(runBlocking { fetcher.fetchImage(server.url("/p.jpg").toString()) })
    }

    @Test
    fun imageFailuresNeverThrow() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(runBlocking { fetcher.fetchImage(server.url("/p.jpg").toString()) })
        assertNull(runBlocking { WebPageFetcher().fetchImage("http://example.com/p.jpg") })
    }

    @Test
    fun imageOverTenMegabytesIsDropped() {
        val big = Buffer().write(ByteArray(WebPageFetcher.MAX_IMAGE_BYTES + 1))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(big))
        assertNull(runBlocking { fetcher.fetchImage(server.url("/big.png").toString()) })
    }
}

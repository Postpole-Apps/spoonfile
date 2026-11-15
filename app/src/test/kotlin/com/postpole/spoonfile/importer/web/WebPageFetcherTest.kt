package com.postpole.spoonfile.importer.web

import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.TimeUnit

class WebPageFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetcher(httpsOnly: Boolean = false, client: OkHttpClient = OkHttpClient()) =
        WebPageFetcher(client, Dispatchers.IO, httpsOnly)

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)

    @Test
    fun fetchesHtml_withBrowserHeaders() = runBlocking {
        server.enqueue(html("<html>hello</html>"))
        val result = fetcher().fetchPage(server.url("/r").toString())
        assertEquals("<html>hello</html>", (result as PageResult.Page).html)
        val request = server.takeRequest()
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Mozilla/5.0"))
        assertEquals("text/html", request.getHeader("Accept"))
    }

    @Test
    fun followsRedirects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
        server.enqueue(html("done"))
        val result = fetcher().fetchPage(server.url("/start").toString()) as PageResult.Page
        assertEquals("done", result.html)
        assertTrue(result.finalUrl.endsWith("/final"))
    }

    @Test
    fun moreThanFiveRedirects_isBlocked() = runBlocking {
        repeat(7) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again")) }
        assertEquals(PageResult.Blocked, fetcher().fetchPage(server.url("/start").toString()))
        assertEquals(WebPageFetcher.MAX_REDIRECTS + 1, server.requestCount)
    }

    @Test
    fun httpError_isReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(PageResult.HttpError(404), fetcher().fetchPage(server.url("/").toString()))
    }

    @Test
    fun nonHtmlContent_isBlocked() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF"))
        assertEquals(PageResult.Blocked, fetcher().fetchPage(server.url("/").toString()))
    }

    @Test
    fun bodyOverFiveMegabytes_isTooBig() = runBlocking {
        val big = Buffer().write(ByteArray(WebPageFetcher.MAX_PAGE_BYTES + 1024))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setChunkedBody(big, 8192))
        assertEquals(PageResult.TooBig, fetcher().fetchPage(server.url("/").toString()))
    }

    @Test
    fun slowServer_timesOut() = runBlocking {
        server.enqueue(html("late").setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        assertEquals(PageResult.Timeout, fetcher(client = client).fetchPage(server.url("/").toString()))
    }

    @Test
    fun unreachableHost_isReported() = runBlocking {
        val url = server.url("/").toString()
        server.shutdown()
        val result = fetcher().fetchPage(url)
        assertTrue(result.toString(), result == PageResult.Unreachable || result == PageResult.Timeout)
    }

    @Test
    fun httpsOnly_refusesPlainHttpWithoutConnecting() = runBlocking {
        assertEquals(PageResult.Blocked, fetcher(httpsOnly = true).fetchPage(server.url("/").toString()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun nonWebScheme_isBlocked() = runBlocking {
        assertEquals(PageResult.Blocked, fetcher().fetchPage("file:///etc/passwd"))
    }

    @Test
    fun cookiesAreNotStored() = runBlocking {
        server.enqueue(html("one").setHeader("Set-Cookie", "session=abc"))
        server.enqueue(html("two"))
        val f = fetcher()
        f.fetchPage(server.url("/").toString())
        f.fetchPage(server.url("/").toString())
        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun image_returnedOnlyForImageTypes() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes)))
        server.enqueue(html("not an image"))
        val f = fetcher()
        assertArrayEquals(bytes, f.fetchImage(server.url("/a.jpg").toString()))
        assertNull(f.fetchImage(server.url("/b").toString()))
    }

    @Test
    fun image_overTenMegabytes_isDropped() = runBlocking {
        val big = Buffer().write(ByteArray(WebPageFetcher.MAX_IMAGE_BYTES + 1024))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setChunkedBody(big, 8192))
        assertNull(fetcher().fetchImage(server.url("/big.png").toString()))
    }
}

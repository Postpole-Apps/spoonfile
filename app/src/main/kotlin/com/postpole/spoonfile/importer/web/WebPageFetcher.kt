package com.postpole.spoonfile.importer.web

import com.postpole.spoonfile.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PageResult {
    data class Page(val html: String, val finalUrl: String) : PageResult
    data class HttpError(val code: Int) : PageResult
    data object Timeout : PageResult
    data object TooBig : PageResult

    /** Not https, a redirect loop, or not an HTML page. */
    data object Blocked : PageResult

    /** Connection failed (no connectivity, DNS, TLS). */
    data object Unreachable : PageResult
}

/**
 * Fetches a web page on the phone: https only, bounded redirects, size and time caps, no cookies,
 * no JavaScript, no WebView. Only call this from an explicit user action.
 *
 * [httpsOnly] exists so unit tests can talk to a plain-http MockWebServer; the app always uses true.
 */
@Singleton
class WebPageFetcher internal constructor(
    client: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val httpsOnly: Boolean,
) {
    @Inject
    constructor(@IoDispatcher io: CoroutineDispatcher) : this(defaultClient(), io, true)

    // We follow redirects ourselves so every hop can be checked.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES).build()

    suspend fun fetchPage(url: String): PageResult = withContext(io) {
        when (val r = get(url, ACCEPT_HTML, MAX_PAGE_BYTES) { it.startsWith("text/html") || it.startsWith("application/xhtml+xml") }) {
            is Raw.Ok -> PageResult.Page(decode(r.bytes, r.contentType), r.finalUrl)
            is Raw.Failed -> r.result
        }
    }

    /** The image bytes, or null on any problem (https only, up to 10 MB, an image content type). A bad image never blocks a recipe. */
    suspend fun fetchImage(url: String): ByteArray? = withContext(io) {
        (get(url, ACCEPT_IMAGE, MAX_IMAGE_BYTES) { it.startsWith("image/") } as? Raw.Ok)?.bytes
    }

    private sealed interface Raw {
        class Ok(val bytes: ByteArray, val contentType: String, val finalUrl: String) : Raw
        class Failed(val result: PageResult) : Raw
    }

    private suspend fun get(url: String, accept: String, maxBytes: Int, typeOk: (String) -> Boolean): Raw {
        var current: HttpUrl = url.toHttpUrlOrNull()?.takeIf(::allowed) ?: return Raw.Failed(PageResult.Blocked)
        try {
            repeat(MAX_REDIRECTS + 1) {
                val request = Request.Builder().url(current)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", accept)
                    .get().build()
                client.newCall(request).await().use { response ->
                    if (response.isRedirect) {
                        val next = response.header("Location")?.let { current.resolve(it) }
                        if (next == null || !allowed(next)) return Raw.Failed(PageResult.Blocked)
                        current = next
                        return@repeat
                    }
                    if (!response.isSuccessful) return Raw.Failed(PageResult.HttpError(response.code))
                    val body = response.body ?: return Raw.Failed(PageResult.HttpError(response.code))
                    val type = response.header("Content-Type").orEmpty().lowercase()
                    if (!typeOk(type.trim())) return Raw.Failed(PageResult.Blocked)
                    if (body.contentLength() > maxBytes) return Raw.Failed(PageResult.TooBig)
                    val bytes = readCapped(body.source(), maxBytes) ?: return Raw.Failed(PageResult.TooBig)
                    return Raw.Ok(bytes, type, current.toString())
                }
            }
            return Raw.Failed(PageResult.Blocked) // too many redirects
        } catch (_: InterruptedIOException) {
            return Raw.Failed(PageResult.Timeout)
        } catch (_: IOException) {
            return Raw.Failed(PageResult.Unreachable)
        }
    }

    private fun allowed(url: HttpUrl) = !httpsOnly || url.isHttps

    private suspend fun readCapped(source: okio.BufferedSource, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            // Lets the user's Cancel stop a slow body promptly.
            currentCoroutineContext().ensureActive()
            val n = source.read(chunk)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray, contentType: String): String {
        val headerCharset = Regex("charset=([\\w-]+)").find(contentType)?.groupValues?.get(1)
        val sniffed = headerCharset ?: Regex("(?i)charset=[\"']?([\\w-]+)")
            .find(String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val charset = try {
            sniffed?.let { java.nio.charset.Charset.forName(it) } ?: Charsets.UTF_8
        } catch (_: IllegalArgumentException) {
            Charsets.UTF_8
        }
        return String(bytes, charset)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    companion object {
        const val MAX_PAGE_BYTES = 5 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_SECONDS = 15L
        private const val ACCEPT_HTML = "text/html"
        private const val ACCEPT_IMAGE = "image/*"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .cookieJar(CookieJar.NO_COOKIES)
            .build()
    }
}

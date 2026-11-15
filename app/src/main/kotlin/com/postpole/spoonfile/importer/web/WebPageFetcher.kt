package com.postpole.spoonfile.importer.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
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
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface FetchResult {
    data class Page(val html: String, val finalUrl: String) : FetchResult
    data class HttpError(val code: Int?) : FetchResult
    data object Timeout : FetchResult
    data object TooBig : FetchResult

    /** The link answered with something other than a web page. */
    data object NotHtml : FetchResult

    /** Not an https link, or a redirect that left https. */
    data object Insecure : FetchResult
}

/**
 * Downloads a web page (and its photo) from the phone. https only, no cookies, no scripts, no WebView.
 * Call only in response to an explicit user action.
 */
@Singleton
class WebPageFetcher internal constructor(
    base: OkHttpClient,
    private val requireHttps: Boolean,
    timeoutMillis: Long,
) {
    @Inject
    constructor() : this(OkHttpClient(), true, TIMEOUT_SECONDS * 1000)

    private val client: OkHttpClient = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    suspend fun fetchPage(url: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val response = open(url, "text/html,application/xhtml+xml;q=0.9")
            response.use {
                if (!it.isSuccessful) return@withContext FetchResult.HttpError(it.code)
                val type = it.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                if (type == null || type !in HTML_TYPES) return@withContext FetchResult.NotHtml
                val bytes = readCapped(it, MAX_PAGE_BYTES) ?: return@withContext FetchResult.TooBig
                FetchResult.Page(decode(bytes, it.header("Content-Type")), it.request.url.toString())
            }
        } catch (_: InsecureException) {
            FetchResult.Insecure
        } catch (_: InterruptedIOException) {
            // OkHttp reports its connect/read timeouts as InterruptedIOException subclasses.
            FetchResult.Timeout
        } catch (_: IOException) {
            FetchResult.HttpError(null)
        } catch (_: IllegalArgumentException) {
            FetchResult.HttpError(null)
        }
    }

    /** The photo at [url] (https, images only, at most 10 MB), or null for any problem. Never throws for I/O. */
    suspend fun fetchImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            open(url, "image/*").use {
                val type = it.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                if (!it.isSuccessful || type == null || !type.startsWith("image/")) return@withContext null
                readCapped(it, MAX_IMAGE_BYTES)
            }
        } catch (_: IOException) {
            null
        } catch (_: InsecureException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private class InsecureException : IOException()

    /** GETs [url], following up to [MAX_REDIRECTS] redirects by hand so each hop can be checked for https. */
    private suspend fun open(url: String, accept: String): Response {
        var current = checked(url.toHttpUrlOrNull() ?: throw InsecureException())
        var hops = 0
        while (true) {
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", "en")
                .get()
                .build()
            val response = client.newCall(request).await()
            if (response.code !in REDIRECT_CODES) return response
            val location = response.header("Location")
            response.close()
            if (location == null || ++hops > MAX_REDIRECTS) throw IOException("Too many redirects")
            current = checked(current.resolve(location) ?: throw InsecureException())
        }
    }

    private fun checked(url: HttpUrl): HttpUrl {
        if (requireHttps && !url.isHttps) throw InsecureException()
        return url
    }

    private suspend fun readCapped(response: Response, cap: Int): ByteArray? {
        val body = response.body ?: return ByteArray(0)
        val declared = body.contentLength()
        if (declared > cap) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        body.byteStream().use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > cap) return null
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = contentType?.let { Regex("""charset=["']?([\w\-]+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?: Regex("""<meta[^>]+charset=["']?([\w\-]+)""", RegexOption.IGNORE_CASE)
                .find(String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val charset = try {
            declared?.let { java.nio.charset.Charset.forName(it) } ?: Charsets.UTF_8
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
                cont.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object {
        const val MAX_PAGE_BYTES = 5 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_SECONDS = 15L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        private val HTML_TYPES = setOf("text/html", "application/xhtml+xml")
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

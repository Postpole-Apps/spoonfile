package com.postpole.spoonfile.importer.web

import java.net.URI
import java.net.URISyntaxException

/** Pulls a web address out of untrusted shared or pasted text. Never loads anything itself. */
object SharedTextUrl {
    /** Shared text beyond this many characters is ignored. */
    const val MAX_INPUT_CHARS = 4 * 1024

    private val candidate = Regex("https?://[^\\s<>\"'`]+", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}>'\""

    /** The first valid http(s) URL in [text] (e.g. from "Title https://…"), or null. */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val capped = text.take(MAX_INPUT_CHARS)
        for (match in candidate.findAll(capped)) {
            val url = match.value.trimEnd { it in TRAILING }
            if (isWebUrl(url)) return url
        }
        return null
    }

    /** Upgrades `http://` to `https://`; the fetcher only speaks https. */
    fun toHttps(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring("http://".length) else url

    fun host(url: String): String? = try {
        URI(url).host?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    } catch (_: URISyntaxException) {
        null
    }

    private fun isWebUrl(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
    } catch (_: URISyntaxException) {
        false
    }
}

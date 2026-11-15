package com.postpole.spoonfile.importer.web

import java.net.URI

/** Pulls a web link out of untrusted text (shared text, clipboard, the link box). Only http(s) is ever returned. */
object SharedTextUrl {
    /** Shared or pasted text longer than this is not read past. */
    const val MAX_INPUT_CHARS = 4096
    private const val MAX_URL_CHARS = 2048

    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val schemePattern = Regex("""^[a-z][a-z0-9+.\-]*:""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}"

    /** The first http(s) URL in [text] ("Title https://…" is common), or null. */
    fun extract(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val capped = text.take(MAX_INPUT_CHARS)
        for (match in urlPattern.findAll(capped)) {
            // A URL running into the cap may be cut short, so it is not trusted.
            if (text.length > MAX_INPUT_CHARS && match.range.last == capped.length - 1) continue
            val url = match.value.trimEnd { it in TRAILING }
            if (url.length <= MAX_URL_CHARS && hasHost(url)) return url
        }
        return null
    }

    /**
     * The https URL to import for what the user typed or pasted, or null if it isn't a usable web link.
     * `http://` links are upgraded to https; a bare "example.com/recipe" gets https added.
     */
    fun toImportUrl(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        val found = extract(text) ?: bareHost(text) ?: return null
        val https = if (found.startsWith("http://", ignoreCase = true)) "https://" + found.substring(7) else found
        return https.takeIf { hasHost(it) }
    }

    private fun bareHost(text: String): String? {
        if (text.any(Char::isWhitespace) || '.' !in text || schemePattern.containsMatchIn(text)) return null
        return "https://$text".takeIf { hasHost(it) && it.length <= MAX_URL_CHARS }
    }

    private fun hasHost(url: String): Boolean = try {
        val host = URI(url).host
        !host.isNullOrEmpty() && '.' in host
    } catch (_: java.net.URISyntaxException) {
        false
    }
}

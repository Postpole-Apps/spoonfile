package com.postpole.spoonfile.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextUrlTest {
    @Test
    fun extractsUrlFromTitleAndLink() {
        assertEquals(
            "https://example.com/recipes/soup",
            SharedTextUrl.extract("Great Soup https://example.com/recipes/soup"),
        )
    }

    @Test
    fun takesTheFirstLinkAndTrimsTrailingPunctuation() {
        assertEquals(
            "https://a.example.com/x",
            SharedTextUrl.extract("see (https://a.example.com/x), or https://b.example.com/y."),
        )
    }

    @Test
    fun keepsQueryStrings() {
        assertEquals("http://example.com/r?id=3&x=1", SharedTextUrl.extract("http://example.com/r?id=3&x=1"))
    }

    @Test
    fun ignoresOtherSchemesAndPlainText() {
        assertNull(SharedTextUrl.extract("javascript:alert(1)"))
        assertNull(SharedTextUrl.extract("file:///etc/passwd"))
        assertNull(SharedTextUrl.extract("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(SharedTextUrl.extract("just some words"))
        assertNull(SharedTextUrl.extract(""))
        assertNull(SharedTextUrl.extract(null))
    }

    @Test
    fun rejectsLinksWithoutAHost() {
        assertNull(SharedTextUrl.extract("https:///nohost"))
        assertNull(SharedTextUrl.extract("http://localhost/path"))
    }

    @Test
    fun readsNoFurtherThanTheLengthCap() {
        val padding = "x ".repeat(SharedTextUrl.MAX_INPUT_CHARS)
        assertNull(SharedTextUrl.extract("$padding https://example.com/late"))
        assertEquals("https://example.com/early", SharedTextUrl.extract("https://example.com/early $padding"))
    }

    @Test
    fun linkRunningIntoTheCapIsNotTrusted() {
        val text = "a ".repeat(2040) + "https://example.com/" + "p".repeat(500)
        assertNull(SharedTextUrl.extract(text))
    }

    @Test
    fun importUrlUpgradesHttpAndAddsSchemeToBareHosts() {
        assertEquals("https://example.com/r", SharedTextUrl.toImportUrl("http://example.com/r"))
        assertEquals("https://example.com/r", SharedTextUrl.toImportUrl("  example.com/r  "))
        assertEquals("https://example.com/r", SharedTextUrl.toImportUrl("Soup https://example.com/r"))
    }

    @Test
    fun importUrlRejectsUnusableInput() {
        assertNull(SharedTextUrl.toImportUrl("not a link"))
        assertNull(SharedTextUrl.toImportUrl("ftp://example.com/file"))
        assertNull(SharedTextUrl.toImportUrl("javascript:alert(1)"))
        assertNull(SharedTextUrl.toImportUrl("nodots"))
        assertNull(SharedTextUrl.toImportUrl(""))
    }
}

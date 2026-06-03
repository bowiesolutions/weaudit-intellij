package com.bowiesolutions.weaudit.integration

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PermalinkBuilderTest {

    // ── GitHub URL format ─────────────────────────────────────────────────────

    @Test
    fun `GitHub single-line permalink uses L{n} fragment`() {
        val loc = Location("src/Vault.sol", 41, 41)  // 0-based line 41 = display line 42
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://github.com/org/repo", "abc1234")
        assertEquals("https://github.com/org/repo/blob/abc1234/src/Vault.sol#L42", url)
    }

    @Test
    fun `GitHub multi-line permalink uses L{start}-L{end} fragment`() {
        val loc = Location("src/Vault.sol", 41, 54)  // lines 42-55 (1-based display)
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://github.com/org/repo", "abc1234")
        assertEquals("https://github.com/org/repo/blob/abc1234/src/Vault.sol#L42-L55", url)
    }

    @Test
    fun `GitHub URL normalises trailing slash and git suffix`() {
        val loc = Location("main.go", 0, 0)
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://github.com/org/repo.git/", "sha")
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://github.com/org/repo/blob/"))
    }

    @Test
    fun `GitHub URL strips leading slash from path`() {
        val loc = Location("/src/main.go", 0, 4)
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://github.com/org/repo", "sha")
        assertNotNull(url)
        assertTrue(url!!.contains("/blob/sha/src/main.go"), "Expected path without leading slash: $url")
    }

    // ── GitLab URL format ─────────────────────────────────────────────────────

    @Test
    fun `GitLab single-line permalink uses L{n} fragment`() {
        val loc = Location("lib/auth.rb", 9, 9)
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://gitlab.com/org/repo", "def5678")
        assertEquals("https://gitlab.com/org/repo/-/blob/def5678/lib/auth.rb#L10", url)
    }

    @Test
    fun `GitLab multi-line permalink uses L{start}-{end} without second L`() {
        val loc = Location("lib/auth.rb", 9, 19)
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://gitlab.com/org/repo", "def5678")
        assertEquals("https://gitlab.com/org/repo/-/blob/def5678/lib/auth.rb#L10-20", url)
    }

    @Test
    fun `self-hosted GitLab is recognised`() {
        val loc = Location("src/app.py", 0, 0)
        val url = PermalinkBuilder.buildForLocation(loc,
            "https://gitlab.mycompany.com/team/project", "aabbcc")
        assertNotNull(url)
        assertTrue(url!!.contains("/-/blob/"), "Expected GitLab /-/blob/ format: $url")
    }

    // ── Null / blank inputs ───────────────────────────────────────────────────

    @Test
    fun `blank remote returns null`() {
        val loc = Location("x.ts", 0, 0)
        assertNull(PermalinkBuilder.buildForLocation(loc, "", "sha"))
    }

    @Test
    fun `blank sha returns null`() {
        val loc = Location("x.ts", 0, 0)
        assertNull(PermalinkBuilder.buildForLocation(loc, "https://github.com/o/r", ""))
    }

    @Test
    fun `unrecognised remote returns null`() {
        val loc = Location("x.ts", 0, 0)
        assertNull(PermalinkBuilder.buildForLocation(loc,
            "https://bitbucket.org/org/repo", "sha"))
    }

    // ── Entry-level helpers ───────────────────────────────────────────────────

    @Test
    fun `buildForEntry uses primary location`() {
        val entry = entry(locations = listOf(
            Location("primary.kt", 5, 10),
            Location("secondary.kt", 20, 25),
        ))
        val url = PermalinkBuilder.buildForEntry(entry,
            "https://github.com/org/repo", "sha123")
        assertNotNull(url)
        assertTrue(url!!.contains("primary.kt"), "Expected primary location in URL: $url")
        assertTrue(url.contains("#L6-L11"))
    }

    @Test
    fun `buildAllForEntry returns one URL per location`() {
        val entry = entry(locations = listOf(
            Location("a.kt", 0, 0),
            Location("b.kt", 5, 5),
        ))
        val urls = PermalinkBuilder.buildAllForEntry(entry,
            "https://github.com/org/repo", "sha")
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("a.kt"))
        assertTrue(urls[1].contains("b.kt"))
    }

    // ── Markdown links ────────────────────────────────────────────────────────

    @Test
    fun `formatMarkdownLinks single location is inline link`() {
        val entry = entry(locations = listOf(Location("src/x.kt", 41, 54)))
        val md = PermalinkBuilder.formatMarkdownLinks(entry,
            "https://github.com/org/repo", "abc123")
        // Should be [text](url) format, not a list
        assertTrue(md.startsWith("["), "Expected inline link, got: $md")
        assertTrue(md.contains("`src/x.kt` lines 42–55"))
        assertTrue(md.contains("](https://"))
    }

    @Test
    fun `formatMarkdownLinks multi location is list`() {
        val entry = entry(locations = listOf(
            Location("a.kt", 0, 5,  label = "Init"),
            Location("b.kt", 10, 15, label = "Use"),
        ))
        val md = PermalinkBuilder.formatMarkdownLinks(entry,
            "https://github.com/org/repo", "sha")
        assertTrue(md.contains("- [Init]"), "Expected list item with Init label: $md")
        assertTrue(md.contains("- [Use]"),  "Expected list item with Use label: $md")
    }

    @Test
    fun `formatMarkdownLinks falls back to plain text when remote blank`() {
        val entry = entry(locations = listOf(Location("x.kt", 0, 0)))
        val md = PermalinkBuilder.formatMarkdownLinks(entry, "", "")
        assertEquals("`x.kt` lines 1–1", md)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun entry(
        label:     String         = "Test",
        locations: List<Location> = listOf(Location("test.kt", 0, 1)),
    ) = Entry(
        label     = label,
        entryType = EntryType.Finding.jsonValue,
        author    = "tester",
        locations = locations,
    )
}

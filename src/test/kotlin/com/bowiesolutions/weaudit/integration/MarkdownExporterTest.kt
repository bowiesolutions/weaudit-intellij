package com.bowiesolutions.weaudit.integration

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.FindingDetails
import com.bowiesolutions.weaudit.model.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownExporterTest {

    // ── Document structure ────────────────────────────────────────────────────

    @Test
    fun `export produces H1 title`() {
        val md = MarkdownExporter.export(listOf(finding()))
        assertTrue(md.startsWith("# Audit Findings\n"), "Expected H1 title: ${md.take(50)}")
    }

    @Test
    fun `custom title is used`() {
        val md = MarkdownExporter.export(listOf(finding()), title = "My Audit")
        assertTrue(md.startsWith("# My Audit\n"))
    }

    @Test
    fun `empty entries produces placeholder message`() {
        val md = MarkdownExporter.export(emptyList())
        assertTrue(md.contains("No findings or notes to export"))
    }

    // ── Finding format ────────────────────────────────────────────────────────

    @Test
    fun `finding heading includes severity prefix`() {
        val md = MarkdownExporter.export(listOf(
            finding(label = "Reentrancy", severity = "High")
        ))
        assertTrue(md.contains("## [High] Reentrancy"), "Expected severity prefix: $md")
    }

    @Test
    fun `finding heading has no prefix when severity blank`() {
        val md = MarkdownExporter.export(listOf(finding(label = "No Severity", severity = "")))
        assertTrue(md.contains("## No Severity"))
        assertFalse(md.contains("## [] No Severity"))
    }

    @Test
    fun `metadata line includes severity difficulty and type`() {
        val md = MarkdownExporter.export(listOf(
            finding(severity = "High", difficulty = "Low", type = "Access Control")
        ))
        assertTrue(md.contains("**Severity:** High"))
        assertTrue(md.contains("**Difficulty:** Low"))
        assertTrue(md.contains("**Type:** Access Control"))
    }

    @Test
    fun `description section is present`() {
        val md = MarkdownExporter.export(listOf(finding(description = "The issue is X.")))
        assertTrue(md.contains("### Description\n\nThe issue is X."))
    }

    @Test
    fun `exploit scenario section is present`() {
        val md = MarkdownExporter.export(listOf(finding(exploit = "Attacker calls withdraw.")))
        assertTrue(md.contains("### Exploit Scenario\n\nAttacker calls withdraw."))
    }

    @Test
    fun `recommendation section is present`() {
        val md = MarkdownExporter.export(listOf(finding(recommendation = "Use CEI pattern.")))
        assertTrue(md.contains("### Recommendation\n\nUse CEI pattern."))
    }

    @Test
    fun `blank sections are omitted`() {
        val md = MarkdownExporter.export(listOf(
            finding(description = "", exploit = "", recommendation = "")
        ))
        assertFalse(md.contains("### Description"))
        assertFalse(md.contains("### Exploit Scenario"))
        assertFalse(md.contains("### Recommendation"))
    }

    @Test
    fun `author attribution is present`() {
        val md = MarkdownExporter.export(listOf(finding(author = "alice")))
        assertTrue(md.contains("*Author: alice*"))
    }

    @Test
    fun `each finding is separated by horizontal rule`() {
        val md = MarkdownExporter.export(listOf(finding(), finding()))
        assertTrue(md.contains("---"))
    }

    // ── Location rendering ────────────────────────────────────────────────────

    @Test
    fun `single location uses inline Location line`() {
        val md = MarkdownExporter.export(listOf(
            finding(path = "src/Vault.sol", startLine = 41, endLine = 54)
        ))
        // 0-based 41 = 1-based 42; 0-based 54 = 1-based 55
        assertTrue(md.contains("**Location:**"))
        assertTrue(md.contains("src/Vault.sol` lines 42–55"))
    }

    @Test
    fun `multi-location uses Locations list`() {
        val entry = Entry(
            label     = "Multi",
            entryType = EntryType.Finding.jsonValue,
            author    = "alice",
            locations = listOf(
                Location("a.sol", 0, 5,  label = "Constructor"),
                Location("b.sol", 10, 15, label = "Call site"),
            )
        )
        val md = MarkdownExporter.export(listOf(entry))
        assertTrue(md.contains("**Locations:**"))
        assertTrue(md.contains("**Constructor**"))
        assertTrue(md.contains("**Call site**"))
    }

    // ── Notes section ─────────────────────────────────────────────────────────

    @Test
    fun `notes appear in separate Notes section`() {
        val md = MarkdownExporter.export(listOf(
            finding(label = "A Finding"),
            note(label    = "A Note"),
        ))
        assertTrue(md.contains("## Notes"))
        assertTrue(md.contains("### A Note"))
    }

    @Test
    fun `resolved entries are excluded from export`() {
        val md = MarkdownExporter.export(listOf(
            finding(label = "Active Finding",   resolved = false),
            finding(label = "Resolved Finding", resolved = true),
        ))
        assertTrue(md.contains("Active Finding"))
        assertFalse(md.contains("Resolved Finding"))
    }

    // ── Permalink integration ─────────────────────────────────────────────────

    @Test
    fun `location is hyperlinked when remote and sha provided`() {
        val md = MarkdownExporter.export(
            listOf(finding(path = "src/Token.sol", startLine = 86, endLine = 91)),
            remote = "https://github.com/org/repo",
            sha    = "abc1234",
        )
        assertTrue(md.contains("](https://github.com/org/repo/blob/abc1234/src/Token.sol"),
            "Expected hyperlink in exported markdown: $md")
    }

    @Test
    fun `location is plain text when remote blank`() {
        val md = MarkdownExporter.export(
            listOf(finding(path = "src/Token.sol", startLine = 0, endLine = 5)),
            remote = "",
            sha    = "",
        )
        assertFalse(md.contains("](http"), "Expected plain text (no hyperlink): $md")
        assertTrue(md.contains("`src/Token.sol`"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun finding(
        label:          String  = "Test Finding",
        author:         String  = "alice",
        path:           String  = "src/Test.sol",
        startLine:      Int     = 0,
        endLine:        Int     = 1,
        severity:       String  = "High",
        difficulty:     String  = "Medium",
        type:           String  = "Access Control",
        description:    String  = "Test description.",
        exploit:        String  = "Test exploit.",
        recommendation: String  = "Test recommendation.",
        resolved:       Boolean = false,
    ) = Entry(
        label     = label,
        entryType = EntryType.Finding.jsonValue,
        author    = author,
        locations = listOf(Location(path, startLine, endLine)),
        resolved  = resolved,
        details   = FindingDetails(
            title          = label,
            severity       = severity,
            difficulty     = difficulty,
            type           = type,
            description    = description,
            exploit        = exploit,
            recommendation = recommendation,
        )
    )

    private fun note(
        label:  String = "Test Note",
        author: String = "alice",
        path:   String = "src/Test.sol",
    ) = Entry(
        label     = label,
        entryType = EntryType.Note.jsonValue,
        author    = author,
        locations = listOf(Location(path, 0, 1)),
    )
}

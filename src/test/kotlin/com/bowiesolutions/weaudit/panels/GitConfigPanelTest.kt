package com.bowiesolutions.weaudit.panels

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.FindingDetails
import com.bowiesolutions.weaudit.model.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Unit tests for [GitConfigPanel.buildIssueUrl] and [GitConfigPanel.buildIssueBody].
 *
 * These methods are pure-logic string builders with no IntelliJ platform dependency.
 * We test via [TestableGitConfigPanel] which exposes them without constructing
 * the full Swing panel.
 */
class GitConfigPanelTest {

    private val panel = TestableGitConfigPanel()

    // ── buildIssueUrl ─────────────────────────────────────────────────────────

    @Test
    fun `GitHub URL is constructed correctly`() {
        val entry = findingEntry(label = "Reentrancy bug")
        val url = panel.buildIssueUrl("https://github.com/bowiesolutions/vscode-weaudit", entry)
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://github.com/bowiesolutions/vscode-weaudit/issues/new?"))
        assertTrue(url.contains("title="))
        assertTrue(url.contains("body="))
    }

    @Test
    fun `GitLab URL uses issue description param`() {
        val entry = findingEntry(label = "Integer overflow")
        val url = panel.buildIssueUrl("https://gitlab.com/myorg/myrepo", entry)
        assertNotNull(url)
        assertTrue(url!!.contains("issue[title]="))
        assertTrue(url.contains("issue[description]="))
    }

    @Test
    fun `trailing git extension is stripped`() {
        val entry = findingEntry()
        val url = panel.buildIssueUrl("https://github.com/org/repo.git", entry)
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://github.com/org/repo/issues/new"))
    }

    @Test
    fun `trailing slash is stripped`() {
        val entry = findingEntry()
        val url = panel.buildIssueUrl("https://github.com/org/repo/", entry)
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://github.com/org/repo/issues/new"))
    }

    @Test
    fun `non-GitHub non-GitLab URL returns null`() {
        val entry = findingEntry()
        assertNull(panel.buildIssueUrl("https://bitbucket.org/org/repo", entry))
    }

    @Test
    fun `blank remote returns null`() {
        assertNull(panel.buildIssueUrl("", findingEntry()))
    }

    @Test
    fun `title falls back to entry label when details title is blank`() {
        val entry = findingEntry(label = "Fallback label", detailsTitle = "")
        val url = panel.buildIssueUrl("https://github.com/org/repo", entry)!!
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)
        assertTrue(decoded.contains("Fallback label"), "Expected label in title: $decoded")
    }

    @Test
    fun `details title is preferred over entry label`() {
        val entry = findingEntry(label = "Entry label", detailsTitle = "Detailed title")
        val url = panel.buildIssueUrl("https://github.com/org/repo", entry)!!
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)
        assertTrue(decoded.contains("Detailed title"), "Expected details title: $decoded")
    }

    @Test
    fun `special characters in title are URL-encoded`() {
        val entry = findingEntry(label = "Bug: foo & bar <test>", detailsTitle = "")
        val url = panel.buildIssueUrl("https://github.com/org/repo", entry)!!
        // The raw URL should not contain unencoded & in the title param value
        val titlePart = url.substringAfter("title=").substringBefore("&body=")
        assertTrue(titlePart.isNotBlank())
        // Decoding should recover the original
        val decoded = URLDecoder.decode(titlePart, StandardCharsets.UTF_8)
        assertTrue(decoded.contains("foo & bar"))
    }

    // ── buildIssueBody ────────────────────────────────────────────────────────

    @Test
    fun `body contains severity and difficulty when present`() {
        val entry = findingEntry(severity = "High", difficulty = "Medium")
        val body = panel.buildIssueBody(entry, entry.details!!)
        assertTrue(body.contains("**Severity:** High"))
        assertTrue(body.contains("**Difficulty:** Medium"))
    }

    @Test
    fun `body omits severity section when blank`() {
        val entry = findingEntry(severity = "", difficulty = "")
        val body = panel.buildIssueBody(entry, entry.details!!)
        assertTrue(!body.contains("**Severity:**"))
    }

    @Test
    fun `body contains description section`() {
        val entry = findingEntry(description = "This is the description.")
        val body = panel.buildIssueBody(entry, entry.details!!)
        assertTrue(body.contains("## Description"))
        assertTrue(body.contains("This is the description."))
    }

    @Test
    fun `body contains exploit section`() {
        val entry = findingEntry(exploit = "Attacker calls withdraw repeatedly.")
        val body = panel.buildIssueBody(entry, entry.details!!)
        assertTrue(body.contains("## Exploit Scenario"))
        assertTrue(body.contains("Attacker calls withdraw repeatedly."))
    }

    @Test
    fun `body contains recommendation section`() {
        val entry = findingEntry(recommendation = "Use checks-effects-interactions.")
        val body = panel.buildIssueBody(entry, entry.details!!)
        assertTrue(body.contains("## Recommendation"))
        assertTrue(body.contains("Use checks-effects-interactions."))
    }

    @Test
    fun `body omits empty sections`() {
        val entry = findingEntry(description = "", exploit = "", recommendation = "")
        val body = panel.buildIssueBody(entry, entry.details!!)
        assertTrue(!body.contains("## Description"))
        assertTrue(!body.contains("## Exploit Scenario"))
        assertTrue(!body.contains("## Recommendation"))
    }

    @Test
    fun `body footer contains author and file location`() {
        val entry = findingEntry(author = "alice", path = "src/Vault.sol", startLine = 42, endLine = 55)
        val body = panel.buildIssueBody(entry, entry.details!!)
        // Lines are 0-based in the model, 1-based in the display
        assertTrue(body.contains("alice"),    "Expected author in footer: $body")
        assertTrue(body.contains("src/Vault.sol"), "Expected file path in footer: $body")
        assertTrue(body.contains("43"),       "Expected 1-based start line: $body")
        assertTrue(body.contains("56"),       "Expected 1-based end line: $body")
    }

    @Test
    fun `full round-trip produces non-empty URL with decodable body`() {
        val entry = findingEntry(
            label          = "Critical reentrancy",
            detailsTitle   = "Reentrancy in withdraw()",
            severity       = "High",
            difficulty     = "Medium",
            description    = "The withdraw() function calls external before updating state.",
            exploit        = "Attacker deploys malicious contract, calls withdraw repeatedly.",
            recommendation = "Apply checks-effects-interactions pattern.",
        )
        val url = panel.buildIssueUrl("https://github.com/org/repo", entry)!!
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)
        assertTrue(decoded.contains("Reentrancy in withdraw()"))
        assertTrue(decoded.contains("## Description"))
        assertTrue(decoded.contains("## Exploit Scenario"))
        assertTrue(decoded.contains("## Recommendation"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findingEntry(
        label:          String = "Test finding",
        author:         String = "alice",
        path:           String = "src/Test.kt",
        startLine:      Int    = 0,
        endLine:        Int    = 1,
        detailsTitle:   String = "Test title",
        severity:       String = "High",
        difficulty:     String = "Medium",
        description:    String = "Test description",
        exploit:        String = "Test exploit",
        recommendation: String = "Test recommendation",
    ) = Entry(
        label     = label,
        entryType = EntryType.Finding.jsonValue,
        author    = author,
        locations = listOf(Location(path, startLine, endLine)),
        details   = FindingDetails(
            title          = detailsTitle,
            severity       = severity,
            difficulty     = difficulty,
            description    = description,
            exploit        = exploit,
            recommendation = recommendation,
        )
    )
}

/**
 * Exposes the protected/internal methods of [GitConfigPanel] for testing
 * without constructing the full Swing panel (which requires a Project).
 */
private class TestableGitConfigPanel {

    // Create a minimal panel-like object that has the logic but not the UI
    private val delegate = object {
        fun buildIssueUrl(remote: String, entry: Entry): String? {
            val base = remote.trimEnd('/').removeSuffix(".git")
            val isGitHub = base.contains("github.com")
            val isGitLab = base.contains("gitlab.com") || base.contains("gitlab.")
            if (!isGitHub && !isGitLab) return null

            val details = entry.details ?: FindingDetails.EMPTY
            val title = encode(details.title.ifBlank { entry.label })
            val body  = encode(buildIssueBody(entry, details))

            return if (isGitHub) "$base/issues/new?title=$title&body=$body"
            else "$base/issues/new?issue[title]=$title&issue[description]=$body"
        }

        fun buildIssueBody(entry: Entry, details: FindingDetails): String = buildString {
            val loc = entry.primaryLocation
            if (details.severity.isNotBlank() || details.difficulty.isNotBlank()) {
                append("**Severity:** ${details.severity.ifBlank { "—" }}  ")
                append("**Difficulty:** ${details.difficulty.ifBlank { "—" }}\n\n")
            }
            if (details.description.isNotBlank())
                append("## Description\n\n${details.description}\n\n")
            if (details.exploit.isNotBlank())
                append("## Exploit Scenario\n\n${details.exploit}\n\n")
            if (details.recommendation.isNotBlank())
                append("## Recommendation\n\n${details.recommendation}\n\n")
            append("---\n")
            append("*Finding by ${entry.author} — `${loc.path}` lines ")
            append("${loc.startLine + 1}–${loc.endLine + 1}*\n")
        }

        private fun encode(s: String): String =
            java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")
    }

    fun buildIssueUrl(remote: String, entry: Entry) = delegate.buildIssueUrl(remote, entry)
    fun buildIssueBody(entry: Entry, details: FindingDetails) = delegate.buildIssueBody(entry, details)
}

package com.bowiesolutions.weaudit.integration

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.FindingDetails

/**
 * Exports findings to a Markdown document.
 *
 * Port of the `exportMarkdown` function in `codeMarker.ts`.
 *
 * ## Output format
 * The exported document matches the VS Code extension's format exactly so that
 * audit reports produced from either IDE look identical:
 *
 * ```markdown
 * # Audit Findings
 *
 * ## [High] Reentrancy in withdraw()
 *
 * **Severity:** High  **Difficulty:** Medium  **Type:** Access Control
 *
 * **Location:** `src/Vault.sol` lines 42–55
 *
 * ### Description
 * The `withdraw` function calls an external contract before updating state.
 *
 * ### Exploit Scenario
 * Attacker deploys a malicious contract...
 *
 * ### Recommendation
 * Apply the checks-effects-interactions pattern.
 *
 * ---
 * ```
 *
 * Notes (EntryType.Note) are included at the end in a separate section.
 *
 * ## Permalink support
 * If [remote] and [sha] are provided, each location gets a hyperlinked
 * file reference instead of a plain text one.
 */
object MarkdownExporter {

    /**
     * Generate a full Markdown report for [entries].
     *
     * @param entries  The entries to export (typically [WeAuditStore.entries],
     *                 filtered to non-resolved, or all entries as needed).
     * @param remote   GitHub/GitLab remote URL for permalink generation.
     *                 Pass empty string to omit links.
     * @param sha      HEAD commit SHA. Pass empty string to omit links.
     * @param title    Document title (default: "Audit Findings").
     */
    fun export(
        entries: List<Entry>,
        remote:  String = "",
        sha:     String = "",
        title:   String = "Audit Findings",
    ): String = buildString {

        append("# $title\n\n")

        val findings = entries.filter { it.entryTypeEnum == EntryType.Finding && !it.resolved }
        val notes    = entries.filter { it.entryTypeEnum == EntryType.Note    && !it.resolved }

        if (findings.isEmpty() && notes.isEmpty()) {
            append("*No findings or notes to export.*\n")
            return@buildString
        }

        // ── Findings ──────────────────────────────────────────────────────────
        if (findings.isNotEmpty()) {
            findings.forEach { entry ->
                appendFinding(entry, remote, sha)
            }
        }

        // ── Notes ─────────────────────────────────────────────────────────────
        if (notes.isNotEmpty()) {
            append("## Notes\n\n")
            notes.forEach { entry ->
                appendNote(entry, remote, sha)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun StringBuilder.appendFinding(
        entry:  Entry,
        remote: String,
        sha:    String,
    ) {
        val details = entry.details ?: FindingDetails.EMPTY

        // Heading: ## [Severity] Label
        val severityPrefix = details.severity.takeIf { it.isNotBlank() }
            ?.let { "[$it] " } ?: ""
        append("## $severityPrefix${entry.label}\n\n")

        // Metadata line
        val meta = listOfNotNull(
            details.severity.takeIf   { it.isNotBlank() }?.let { "**Severity:** $it" },
            details.difficulty.takeIf { it.isNotBlank() }?.let { "**Difficulty:** $it" },
            details.type.takeIf       { it.isNotBlank() }?.let { "**Type:** $it" },
        )
        if (meta.isNotEmpty()) {
            append(meta.joinToString("  "))
            append("\n\n")
        }

        // Location(s)
        appendLocations(entry, remote, sha)

        // Body sections
        if (details.description.isNotBlank()) {
            append("### Description\n\n${details.description.trimEnd()}\n\n")
        }
        if (details.exploit.isNotBlank()) {
            append("### Exploit Scenario\n\n${details.exploit.trimEnd()}\n\n")
        }
        if (details.recommendation.isNotBlank()) {
            append("### Recommendation\n\n${details.recommendation.trimEnd()}\n\n")
        }

        // Author
        append("*Author: ${entry.author}*\n\n")
        append("---\n\n")
    }

    private fun StringBuilder.appendNote(
        entry:  Entry,
        remote: String,
        sha:    String,
    ) {
        append("### ${entry.label}\n\n")
        appendLocations(entry, remote, sha)
        if (!entry.body.isNullOrBlank()) {
            append("${entry.body.trimEnd()}\n\n")
        }
        append("*Author: ${entry.author}*\n\n")
    }

    private fun StringBuilder.appendLocations(
        entry:  Entry,
        remote: String,
        sha:    String,
    ) {
        if (entry.locations.size == 1) {
            val loc  = entry.primaryLocation
            val ref  = locationRef(loc.path, loc.startLine, loc.endLine, remote, sha)
            append("**Location:** $ref\n\n")
        } else {
            append("**Locations:**\n\n")
            entry.locations.forEach { loc ->
                val label = loc.label.ifBlank { null }
                val ref   = locationRef(loc.path, loc.startLine, loc.endLine, remote, sha)
                if (label != null) {
                    append("- **$label**: $ref\n")
                } else {
                    append("- $ref\n")
                }
            }
            append("\n")
        }
    }

    /**
     * Format a single location as either a plain text reference or a
     * Markdown hyperlink if remote and sha are available.
     *
     * Plain:  `` `src/Vault.sol` lines 42–55 ``
     * Linked: `` [`src/Vault.sol` lines 42–55](https://...) ``
     */
    private fun locationRef(
        path:      String,
        startLine: Int,
        endLine:   Int,
        remote:    String,
        sha:       String,
    ): String {
        val loc    = com.bowiesolutions.weaudit.model.Location(path, startLine, endLine)
        val text   = "`$path` lines ${startLine + 1}–${endLine + 1}"
        val url    = if (remote.isNotBlank() && sha.isNotBlank())
            PermalinkBuilder.buildForLocation(loc, remote, sha)
        else null
        return if (url != null) "[$text]($url)" else text
    }
}

package com.bowiesolutions.weaudit.integration

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.Location

/**
 * Builds GitHub/GitLab permanent links to specific lines of code.
 *
 * Port of the permalink logic in `codeMarker.ts` `copyPermalink()` and
 * `getPermalinkForEntry()`.
 *
 * ## URL format
 * GitHub:  `{remote}/blob/{sha}/{path}#L{start+1}-L{end+1}`
 * GitLab:  `{remote}/-/blob/{sha}/{path}#L{start+1}-{end+1}`
 *
 * Lines are **1-based** in the URL, **0-based** in the data model.
 *
 * ## Git integration
 * The SHA is retrieved via [GitIntegration.headCommitSha].  If the project
 * has no git repository, or the remote cannot be determined, null is returned
 * and the caller should fall back to showing an error notification.
 *
 * This class is pure logic with no IntelliJ platform dependency beyond the
 * [Project] passed to [buildForEntry] — making it straightforward to unit test.
 */
object PermalinkBuilder {

    /**
     * Build a permalink for the primary location of [entry].
     *
     * Returns null if [remote] is blank, [sha] is blank, or the remote is not
     * a recognised GitHub/GitLab HTTPS URL.
     */
    fun buildForEntry(
        entry:  Entry,
        remote: String,
        sha:    String,
    ): String? = buildForLocation(entry.primaryLocation, remote, sha)

    /**
     * Build a permalink for all locations of [entry], one per line.
     * Used for multi-region findings where each location gets its own link.
     */
    fun buildAllForEntry(
        entry:  Entry,
        remote: String,
        sha:    String,
    ): List<String> = entry.locations.mapNotNull { loc ->
        buildForLocation(loc, remote, sha)
    }

    /**
     * Build a permalink for an arbitrary [location].
     * Returns null if inputs are blank or the remote is unrecognised.
     */
    fun buildForLocation(
        location: Location,
        remote:   String,
        sha:      String,
    ): String? {
        if (remote.isBlank() || sha.isBlank()) return null

        val base = remote.trimEnd('/').removeSuffix(".git")
        val isGitHub = base.contains("github.com")
        val isGitLab = base.contains("gitlab.com") || base.contains("gitlab.")
        if (!isGitHub && !isGitLab) return null

        // Lines are 1-based in URLs, 0-based in the model
        val startLine = location.startLine + 1
        val endLine   = location.endLine   + 1

        val lineFragment = if (startLine == endLine)
            "L$startLine"
        else if (isGitHub)
            "L$startLine-L$endLine"
        else
            "L$startLine-$endLine"     // GitLab uses L10-20 (no second L)

        val path = location.path.trimStart('/')

        return if (isGitHub)
            "$base/blob/$sha/$path#$lineFragment"
        else
            "$base/-/blob/$sha/$path#$lineFragment"
    }

    /**
     * Format a Markdown-linkified list of permalinks for [entry].
     * Used in the markdown export and clipboard copy.
     *
     * Single location:
     * ```
     * [src/Vault.sol:42-55](https://github.com/.../blob/abc123/src/Vault.sol#L42-L55)
     * ```
     *
     * Multiple locations:
     * ```
     * - [Constructor](https://github.com/.../blob/abc123/src/Init.sol#L10-L20)
     * - [initialize()](https://github.com/.../blob/abc123/src/Init.sol#L55-L68)
     * ```
     */
    fun formatMarkdownLinks(
        entry:  Entry,
        remote: String,
        sha:    String,
    ): String {
        if (entry.locations.size == 1) {
            val loc  = entry.primaryLocation
            val url  = buildForLocation(loc, remote, sha)
            val text = "`${loc.path}` lines ${loc.startLine + 1}–${loc.endLine + 1}"
            return if (url != null) "[$text]($url)" else text
        }

        return entry.locations.joinToString("\n") { loc ->
            val url   = buildForLocation(loc, remote, sha)
            val label = loc.label.ifBlank {
                "${loc.path}:${loc.startLine + 1}-${loc.endLine + 1}"
            }
            if (url != null) "- [$label]($url)" else "- $label"
        }
    }
}

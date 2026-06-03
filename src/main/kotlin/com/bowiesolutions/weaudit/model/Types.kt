package com.bowiesolutions.weaudit.model

/**
 * Kotlin port of `src/types.ts` from trailofbits/vscode-weaudit.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FROZEN CONTRACT — do not change field names or integer values without also
 * updating the VS Code extension.  Both plugins share the same `.weaudit` JSON
 * files on disk so byte-compatibility is a hard requirement for mixed teams.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * JSON serialization is handled by [WeAuditSerializer] (store package) using
 * kotlinx.serialization, with explicit [SerialName] on every field that must
 * match the TypeScript key exactly.
 *
 * Companion objects hold the default/sentinel values used by the VS Code
 * extension so we never silently diverge.
 */

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps to the `EntryType` numeric enum in `types.ts`.
 * Serialized as its integer ordinal value (0 / 1 / 2) in the JSON.
 *
 * VS Code source:
 *   export const enum EntryType { Finding = 0, Note = 1, }
 *   (resolved findings are stored inline via Entry.resolved flag, not a 3rd type)
 */
@Serializable
enum class EntryType(val jsonValue: Int) {
    @SerialName("0") Finding(0),
    @SerialName("1") Note(1);

    companion object {
        fun fromInt(v: Int): EntryType = entries.first { it.jsonValue == v }
    }
}

/**
 * Controls how the List-of-Findings tree groups its items.
 * Not stored in the `.weaudit` file — it is UI/session state held by
 * [WeAuditSettingsState].
 *
 * VS Code source: export const enum TreeViewMode { List = 0, GroupByFile = 1, }
 */
enum class TreeViewMode { List, GroupByFile }

// ─────────────────────────────────────────────────────────────────────────────
// Finding Details
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Structured metadata for a Finding entry (not used for Notes).
 * Stored as `entry.details` in the JSON.
 *
 * All fields are optional/nullable and default to empty string / null so that
 * an absent field in legacy `.weaudit` files deserializes cleanly.
 *
 * VS Code source: `FindingDetails` interface in `types.ts`.
 */
@Serializable
data class FindingDetails(
    /** Short title shown in GitHub issue subject. */
    @SerialName("title")
    val title: String = "",

    /** Severity string, e.g. "High", "Medium", "Low", "Informational". */
    @SerialName("severity")
    val severity: String = "",

    /** Difficulty string, e.g. "High", "Medium", "Low". */
    @SerialName("difficulty")
    val difficulty: String = "",

    /** Finding type / category, e.g. "Access Control", "Data Validation". */
    @SerialName("type")
    val type: String = "",

    /** Description body (Markdown). */
    @SerialName("description")
    val description: String = "",

    /** Exploit scenario (Markdown). */
    @SerialName("exploit")
    val exploit: String = "",

    /** Recommended fix / recommendation (Markdown). */
    @SerialName("recommendation")
    val recommendation: String = "",
) {
    companion object {
        val EMPTY = FindingDetails()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Location
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single code region within a finding or note.
 *
 * Paths are stored **relative to the workspace root** (the directory that
 * contains `.vscode/`).  This is the same convention as the VS Code extension.
 * The [WeAuditStore] resolves them to absolute [com.intellij.openapi.vfs.VirtualFile]
 * paths at load time using [com.intellij.openapi.project.Project.basePath].
 *
 * Lines are **0-based** in the `.weaudit` JSON, matching VS Code's
 * `TextDocument.lineAt` convention.  The IntelliJ editor layer (Phase 2)
 * must convert to/from IntelliJ's 0-based document line numbers — they happen
 * to match, but keep it explicit.
 *
 * VS Code source: `Location` interface in `types.ts`.
 */
@Serializable
data class Location(
    /** Workspace-relative path, e.g. `"src/foo/bar.ts"`. */
    @SerialName("path")
    val path: String,

    /** 0-based start line (inclusive). */
    @SerialName("startLine")
    val startLine: Int,

    /** 0-based end line (inclusive). */
    @SerialName("endLine")
    val endLine: Int,

    /**
     * Per-location label (used in multi-region findings to distinguish regions).
     * Defaults to empty string — never null in JSON.
     */
    @SerialName("label")
    val label: String = "",

    /**
     * Per-location description / note text.
     * Defaults to empty string — never null in JSON.
     */
    @SerialName("description")
    val description: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// Entry  (Finding or Note)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The primary unit of the data model — a finding or a note.
 *
 * An Entry may span **multiple locations** (multi-region findings), always has
 * at least one.  The first element of [locations] is the "primary" location
 * displayed in the tree view.
 *
 * VS Code source: `Entry` interface in `types.ts`.
 */
@Serializable
data class Entry(
    /** Display name shown in the tree view and used as the GitHub issue title. */
    @SerialName("label")
    val label: String,

    /**
     * Integer-serialized [EntryType].
     * Use the [entryTypeEnum] helper to avoid raw int comparisons in business logic.
     */
    @SerialName("entryType")
    val entryType: Int,

    /** Username of the auditor who created this entry (from settings). */
    @SerialName("author")
    val author: String,

    /** All code regions belonging to this entry (at least one). */
    @SerialName("locations")
    val locations: List<Location>,

    /**
     * Structured details, present on Findings; may be absent on Notes.
     * Serialized as `null`-omitted in JSON for backwards compatibility with
     * files produced by early VS Code extension versions.
     */
    @SerialName("details")
    val details: FindingDetails? = null,

    /**
     * Whether this entry has been resolved (hidden from active findings, still
     * stored and visible in the Resolved panel).
     * Absent in legacy files = `false`.
     */
    @SerialName("resolved")
    val resolved: Boolean = false,

    /**
     * Optional additional note text attached to the entry root (as opposed to
     * per-location descriptions).  Maps to `entry.body` in some VS Code versions.
     */
    @SerialName("body")
    val body: String? = null,
) {
    /** Convenience accessor that avoids raw int comparisons in business logic. */
    val entryTypeEnum: EntryType get() = EntryType.fromInt(entryType)

    /** The primary (first) location — always present; entries have ≥ 1 location. */
    val primaryLocation: Location get() = locations.first()
}

// ─────────────────────────────────────────────────────────────────────────────
// Audited / Partially-audited files
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A file that has been fully marked as reviewed.
 *
 * VS Code source: `AuditedFile` interface in `types.ts`.
 */
@Serializable
data class AuditedFile(
    /** Workspace-relative path. */
    @SerialName("path")
    val path: String,
)

/**
 * A file with one or more specific regions marked as reviewed (not the whole file).
 *
 * VS Code source: `PartiallyAuditedFile` interface in `types.ts`.
 */
@Serializable
data class PartiallyAuditedFile(
    /** Workspace-relative path. */
    @SerialName("path")
    val path: String,

    /** The reviewed regions within this file. */
    @SerialName("regions")
    val regions: List<Location>,
)

// ─────────────────────────────────────────────────────────────────────────────
// SerializedData  —  the top-level .weaudit file shape
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The complete contents of a `$USERNAME.weaudit` file.
 *
 * This is the **frozen JSON contract**.  The JSON written by the VS Code
 * extension looks like:
 *
 * ```json
 * {
 *   "treeEntries": [ { "label": "...", "entryType": 0, ... } ],
 *   "auditedFiles": [ { "path": "src/foo.ts" } ],
 *   "partiallyAuditedFiles": [ { "path": "src/bar.ts", "regions": [...] } ]
 * }
 * ```
 *
 * All three top-level arrays are always present (may be empty `[]`).
 *
 * VS Code source: `SerializedData` interface in `types.ts`.
 */
@Serializable
data class SerializedData(
    /** All findings and notes for this user (including resolved ones). */
    @SerialName("treeEntries")
    val treeEntries: List<Entry> = emptyList(),

    /** Files that have been fully marked as reviewed. */
    @SerialName("auditedFiles")
    val auditedFiles: List<AuditedFile> = emptyList(),

    /** Files with specific reviewed regions (not the whole file). */
    @SerialName("partiallyAuditedFiles")
    val partiallyAuditedFiles: List<PartiallyAuditedFile> = emptyList(),
) {
    companion object {
        val EMPTY = SerializedData()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Daily log
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One day's audit activity record.
 *
 * Stored in `.vscode/$USERNAME.weauditdaylog` as a JSON array of these objects.
 * The VS Code extension appends to this file; we must preserve all existing
 * records on write.
 *
 * VS Code source: `DayLog` / `DayLogEntry` in `types.ts`.
 */
@Serializable
data class DayLogEntry(
    /**
     * ISO-8601 date string, e.g. `"2024-03-19"`.
     * Keyed by date in the VS Code extension; we store it inline for simplicity.
     */
    @SerialName("date")
    val date: String,

    /** Workspace-relative paths of files audited this day. */
    @SerialName("auditedFiles")
    val auditedFiles: List<String> = emptyList(),

    /** Total lines of code audited this day (sum over all audited files). */
    @SerialName("auditedLOC")
    val auditedLoc: Int = 0,
)

/**
 * The complete `.weauditdaylog` file: an array of [DayLogEntry] objects.
 * Wrapped in a data class to match the serializer pattern.
 */
@Serializable
data class DayLog(
    @SerialName("entries")
    val entries: List<DayLogEntry> = emptyList(),
) {
    companion object {
        val EMPTY = DayLog()
    }
}

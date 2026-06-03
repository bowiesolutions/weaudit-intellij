package com.bowiesolutions.weaudit.toolwindow.tree

import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile

/**
 * Sealed hierarchy of every node type that can appear in the three weAudit trees.
 *
 * ## Tree structure (List of Findings)
 *
 * ### List mode (TreeViewMode.List)
 * ```
 * Root
 *   EntryNode(entry, index)          ← Finding or Note
 *     LocationNode(loc, locIndex)    ← only shown when entry has > 1 location
 * ```
 *
 * ### GroupByFile mode (TreeViewMode.GroupByFile)
 * ```
 * Root
 *   FileGroupNode(relativePath)
 *     EntryNode(entry, index)
 *       LocationNode(loc, locIndex)
 * ```
 *
 * ## Tree structure (Resolved Findings) — always List mode
 * ```
 * Root
 *   EntryNode(entry, index)
 *     LocationNode(loc, locIndex)
 * ```
 *
 * ## Tree structure (Audited Files)
 * ```
 * Root
 *   AuditedFileNode(auditedFile)
 *   PartialFileNode(partialFile)
 *     PartialRegionNode(loc, regionIndex)
 * ```
 *
 * All nodes carry enough data to:
 *   - render their label and icon
 *   - navigate to the relevant file/line when clicked
 *   - identify the store index for mutation operations
 */
sealed class WeAuditTreeNode {

    // ── Findings / Notes trees ────────────────────────────────────────────────

    /**
     * A top-level file group header in GroupByFile mode.
     * Not clickable for navigation — expands to show [EntryNode] children.
     */
    data class FileGroupNode(
        /** Workspace-relative path used as the group key and display label. */
        val path: String,
    ) : WeAuditTreeNode()

    /**
     * A Finding or Note entry.
     *
     * [storeIndex] is the index into [WeAuditStore.entries] (active list) or
     * [WeAuditStore.entries] filtered to resolved entries, depending on which
     * tree this node appears in.  The tree model resolves the correct list
     * before constructing nodes, so [storeIndex] is always valid at the time
     * of construction and must be recomputed on every store change.
     *
     * [author] is set to distinguish own entries from co-auditor entries in
     * the Saved Findings tree (Phase 5), and to look up the correct highlight
     * color.
     */
    data class EntryNode(
        val entry:      Entry,
        val storeIndex: Int,
        val author:     String,
    ) : WeAuditTreeNode() {
        val isFinding: Boolean get() = entry.entryTypeEnum == EntryType.Finding
        val isNote:    Boolean get() = entry.entryTypeEnum == EntryType.Note
        /** True when the entry has more than one location (multi-region finding). */
        val hasMultipleLocations: Boolean get() = entry.locations.size > 1
    }

    /**
     * A single code region within a multi-region finding.
     * Only shown as a child of [EntryNode] when the entry has > 1 location.
     *
     * [locationIndex] is the index within [EntryNode.entry.locations].
     */
    data class LocationNode(
        val location:      Location,
        val parentEntry:   Entry,
        val storeIndex:    Int,   // same as parent EntryNode.storeIndex
        val locationIndex: Int,
    ) : WeAuditTreeNode()

    // ── Audited Files tree ────────────────────────────────────────────────────

    /** A fully-reviewed file (green highlight covers whole file). */
    data class AuditedFileNode(
        val auditedFile: AuditedFile,
    ) : WeAuditTreeNode()

    /** A file with one or more reviewed regions (not the whole file). */
    data class PartialFileNode(
        val partialFile: PartiallyAuditedFile,
    ) : WeAuditTreeNode()

    /**
     * A single reviewed region within a partially-audited file.
     * Child of [PartialFileNode].
     */
    data class PartialRegionNode(
        val location:    Location,
        val parentPath:  String,
        val regionIndex: Int,
    ) : WeAuditTreeNode()
}

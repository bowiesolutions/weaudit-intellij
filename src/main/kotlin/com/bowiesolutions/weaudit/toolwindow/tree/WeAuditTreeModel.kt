package com.bowiesolutions.weaudit.toolwindow.tree

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.TreeViewMode
import com.bowiesolutions.weaudit.store.IWeAuditStore
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Swing [DefaultTreeModel] for the List of Findings and Resolved Findings panels.
 *
 * ## Rebuild strategy
 * The model is stateless between rebuilds: every call to [rebuild] discards all
 * nodes and reconstructs the full tree from [WeAuditStore].  This is safe because
 * the trees are small (hundreds of entries at most) and rebuild is only triggered
 * by store change notifications, which are already throttled to one per mutation.
 *
 * This matches the VS Code `TreeDataProvider.onDidChangeTreeData` pattern exactly
 * — on any change, fire the event and let the framework re-query the full tree.
 *
 * ## Threading
 * [rebuild] **must be called on the EDT** — it calls [DefaultTreeModel.setRoot]
 * which fires Swing events synchronously.
 *
 * ## Node identity
 * Each [DefaultMutableTreeNode] carries a [WeAuditTreeNode] as its `userObject`.
 * Use [nodeAt] to retrieve it.
 *
 * @param showResolved  If true, shows only resolved entries (Resolved panel).
 *                      If false, shows only active entries (Findings panel).
 */
class WeAuditTreeModel(
    private val store: IWeAuditStore,
    private val showResolved: Boolean = false,
) : DefaultTreeModel(DefaultMutableTreeNode("root")) {

    /** Current view mode — set by toolbar toggle, triggers [rebuild]. */
    var viewMode: TreeViewMode = TreeViewMode.List
        set(value) { field = value; rebuild() }

    /** Current filter string — set by search box, triggers [rebuild]. */
    var filterText: String = ""
        set(value) { field = value; rebuild() }

    /** Usernames currently hidden — toggled by the Hide User action. */
    private val hiddenAuthors: MutableSet<String> = mutableSetOf()

    // ── Public API ────────────────────────────────────────────────────────────

    fun hideAuthor(author: String)   { hiddenAuthors.add(author);    rebuild() }
    fun showAuthor(author: String)   { hiddenAuthors.remove(author); rebuild() }
    fun isHidden(author: String)     = author in hiddenAuthors
    fun toggleAuthor(author: String) { if (isHidden(author)) showAuthor(author) else hideAuthor(author) }

    /** All distinct author names seen in the current (unfiltered) entry list. */
    val knownAuthors: Set<String>
        get() = store.entries.map { it.author }.toSet() +
                store.allUsersData.keys

    /**
     * Rebuild the full tree from current store state.
     * Called by [WeAuditStartupActivity] change listener and toolbar actions.
     * Must be called on the EDT.
     */
    fun rebuild() {
        val entries = visibleEntries()
        val newRoot = DefaultMutableTreeNode("root")

        when (viewMode) {
            TreeViewMode.List        -> buildListMode(newRoot, entries)
            TreeViewMode.GroupByFile -> buildGroupByFileMode(newRoot, entries)
        }

        setRoot(newRoot)   // fires treeStructureChanged to all listeners
    }

    /** Return the [WeAuditTreeNode] stored in [node], or null if it's the invisible root. */
    fun nodeAt(node: DefaultMutableTreeNode): WeAuditTreeNode? =
        node.userObject as? WeAuditTreeNode

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Produce the filtered, visibility-checked list of (globalIndex, entry) pairs
     * that should appear in this tree.
     *
     * [globalIndex] is the entry's index in [WeAuditStore.entries], needed so
     * mutation actions (delete, resolve) can address the right entry.
     */
    private fun visibleEntries(): List<IndexedEntry> {
        val allEntries = store.entries
        val filter     = filterText.trim().lowercase()

        return allEntries
            .mapIndexed { index, entry -> IndexedEntry(index, entry) }
            .filter { (_, entry) ->
                // Resolved filter
                entry.resolved == showResolved &&
                // Author visibility
                entry.author !in hiddenAuthors &&
                // Text search across label, author, file paths
                (filter.isEmpty() ||
                    entry.label.lowercase().contains(filter) ||
                    entry.author.lowercase().contains(filter) ||
                    entry.locations.any { it.path.lowercase().contains(filter) })
            }
    }

    private fun buildListMode(root: DefaultMutableTreeNode, entries: List<IndexedEntry>) {
        for ((index, entry) in entries) {
            val entryNode = entryMutableNode(entry, index)
            appendLocationChildren(entryNode, entry, index)
            root.add(entryNode)
        }
    }

    private fun buildGroupByFileMode(root: DefaultMutableTreeNode, entries: List<IndexedEntry>) {
        // Group by the primary location's file path, preserving insertion order.
        val groups = linkedMapOf<String, MutableList<IndexedEntry>>()
        for (ie in entries) {
            val path = ie.entry.primaryLocation.path
            groups.getOrPut(path) { mutableListOf() }.add(ie)
        }

        for ((path, groupEntries) in groups) {
            val groupMutableNode = DefaultMutableTreeNode(WeAuditTreeNode.FileGroupNode(path))
            for ((index, entry) in groupEntries) {
                val entryNode = entryMutableNode(entry, index)
                appendLocationChildren(entryNode, entry, index)
                groupMutableNode.add(entryNode)
            }
            root.add(groupMutableNode)
        }
    }

    /** Only append [LocationNode] children when the entry has > 1 location. */
    private fun appendLocationChildren(
        parent: DefaultMutableTreeNode,
        entry:  Entry,
        index:  Int,
    ) {
        if (entry.locations.size <= 1) return
        entry.locations.forEachIndexed { locIdx, loc ->
            parent.add(DefaultMutableTreeNode(
                WeAuditTreeNode.LocationNode(loc, entry, index, locIdx)
            ))
        }
    }

    private fun entryMutableNode(entry: Entry, index: Int) =
        DefaultMutableTreeNode(
            WeAuditTreeNode.EntryNode(entry, index, entry.author)
        )

    data class IndexedEntry(val index: Int, val entry: Entry)
}

// ── Audited-files tree model ──────────────────────────────────────────────────

/**
 * [DefaultTreeModel] for the Audited Files panel.
 *
 * Two-level tree: fully-audited files are leaf nodes; partially-audited files
 * expand to show their individual reviewed regions.
 */
class AuditedFilesTreeModel(
    private val store: IWeAuditStore,
) : DefaultTreeModel(DefaultMutableTreeNode("root")) {

    fun rebuild() {
        val newRoot = DefaultMutableTreeNode("root")

        for (af in store.auditedFiles) {
            newRoot.add(DefaultMutableTreeNode(WeAuditTreeNode.AuditedFileNode(af)))
        }

        for (pf in store.partiallyAuditedFiles) {
            val pfNode = DefaultMutableTreeNode(WeAuditTreeNode.PartialFileNode(pf))
            pf.regions.forEachIndexed { idx, loc ->
                pfNode.add(DefaultMutableTreeNode(
                    WeAuditTreeNode.PartialRegionNode(loc, pf.path, idx)
                ))
            }
            newRoot.add(pfNode)
        }

        setRoot(newRoot)
    }

    fun nodeAt(node: DefaultMutableTreeNode): WeAuditTreeNode? =
        node.userObject as? WeAuditTreeNode
}

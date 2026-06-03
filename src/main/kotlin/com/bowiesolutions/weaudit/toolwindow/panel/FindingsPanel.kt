package com.bowiesolutions.weaudit.toolwindow.panel

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.bowiesolutions.weaudit.actions.NavigateToFindingAction
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.TreeViewMode
import com.bowiesolutions.weaudit.store.WeAuditStore
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeCellRenderer
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeModel
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeNode
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

/**
 * The "List of Findings" tool-window panel.
 *
 * Port of the VS Code `codeMarker` tree view, which showed active (non-resolved)
 * findings and notes with two view modes and a search field.
 *
 * ## Layout
 * ```
 * ┌─────────────────────────────────────────┐
 * │ [List] [Group] [Hide User▼] [Export]    │  ← toolbar
 * │ 🔍 search box                           │  ← search
 * │ ┌─────────────────────────────────────┐ │
 * │ │ ⚠ [High] Reentrancy in withdraw()  │ │
 * │ │   src/Vault.sol:42-55              │ │
 * │ │ ℹ TODO: verify nonce wrapping      │ │
 * │ └─────────────────────────────────────┘ │  ← tree
 * └─────────────────────────────────────────┘
 * ```
 *
 * ## Navigation
 * Clicking an [EntryNode] or [LocationNode] opens the file and navigates to the
 * start line using [NavigateToFindingAction.navigateTo].
 *
 * ## Store wiring
 * The panel registers itself as a store change listener in [attach] and unregisters
 * in [detach].  These are called by [WeAuditToolWindowFactory] when the tool window
 * is created / disposed.
 */
class FindingsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val store  = WeAuditStore.getInstance(project)
    private val model  = WeAuditTreeModel(store, showResolved = false)
    private val tree   = Tree(model)
    private val search = SearchTextField()

    // ── Phase 4: selection callbacks ─────────────────────────────────────────

    /**
     * Invoked on the EDT when the user selects a finding or note in the tree.
     * Receives the [Entry] and its store index.
     * Set by [WeAuditToolWindowFactory].
     */
    var onEntrySelected: ((entry: Entry, storeIndex: Int) -> Unit)? = null

    /**
     * Invoked on the EDT when the selection is cleared or a non-entry node
     * (FileGroupNode) is selected.
     * Set by [WeAuditToolWindowFactory].
     */
    var onEntryDeselected: (() -> Unit)? = null

    // ── Change listener ───────────────────────────────────────────────────────

    private val changeListener: () -> Unit = {
        model.rebuild()
        tree.repaint()
    }

    init {
        // ── Tree config ───────────────────────────────────────────────────────
        tree.cellRenderer         = WeAuditTreeCellRenderer()
        tree.isRootVisible        = false
        tree.showsRootHandles     = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        // Navigate on selection
        tree.addTreeSelectionListener(buildSelectionListener())

        // ── Search box ────────────────────────────────────────────────────────
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                model.filterText = search.text
            }
        })
        search.toolTipText = "Filter findings by label, author, or file path"

        // ── Toolbar ───────────────────────────────────────────────────────────
        val toolbar = buildToolbar()

        // ── Layout ────────────────────────────────────────────────────────────
        val topPanel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)
            add(search,            BorderLayout.SOUTH)
        }

        add(topPanel,              BorderLayout.NORTH)
        add(JBScrollPane(tree),    BorderLayout.CENTER)

        model.rebuild()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun attach()  { store.addChangeListener(changeListener) }
    fun detach()  { store.removeChangeListener(changeListener) }

    // ── Selection listener ────────────────────────────────────────────────────

    /**
     * Handles tree selection events:
     * - Navigates the editor to the selected location.
     * - Fires [onEntrySelected] so detail panels can update.
     * - Fires [onEntryDeselected] for non-entry selections or clearing.
     */
    private fun buildSelectionListener(): TreeSelectionListener = TreeSelectionListener { e ->
        val path = e.path
        if (path == null) {
            onEntryDeselected?.invoke()
            return@TreeSelectionListener
        }

        val mutableNode = path.lastPathComponent as? DefaultMutableTreeNode
            ?: run { onEntryDeselected?.invoke(); return@TreeSelectionListener }

        val node = model.nodeAt(mutableNode)
            ?: run { onEntryDeselected?.invoke(); return@TreeSelectionListener }

        when (node) {
            is WeAuditTreeNode.EntryNode -> {
                onEntrySelected?.invoke(node.entry, node.storeIndex)
                NavigateToFindingAction.navigateTo(project, node.entry.primaryLocation)
            }
            is WeAuditTreeNode.LocationNode -> {
                // For a location child, fire selection for the parent entry
                onEntrySelected?.invoke(node.parentEntry, node.storeIndex)
                NavigateToFindingAction.navigateTo(project, node.location)
            }
            else -> onEntryDeselected?.invoke()
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun buildToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(ToggleViewModeAction())
            addSeparator()
            add(ResolveSelectedAction())
            add(DeleteSelectedAction())
            addSeparator()
            add(CollapseAllAction())
        }
        return ActionManager.getInstance()
            .createActionToolbar("WeAudit.FindingsToolbar", group, true)
            .also { it.targetComponent = this }
    }

    // ── Toolbar inner actions ─────────────────────────────────────────────────

    private fun selectedEntryNode(): WeAuditTreeNode.EntryNode? {
        val mutableNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return model.nodeAt(mutableNode) as? WeAuditTreeNode.EntryNode
    }
    
//    // ── Selection → navigation ────────────────────────────────────────────────
//
//    private fun buildSelectionListener(): TreeSelectionListener = TreeSelectionListener { e ->
//        val mutableNode = e.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@TreeSelectionListener
//        val node = model.nodeAt(mutableNode) ?: return@TreeSelectionListener
//
//        val location = when (node) {
//            is WeAuditTreeNode.EntryNode    -> node.entry.primaryLocation
//            is WeAuditTreeNode.LocationNode -> node.location
//            else -> return@TreeSelectionListener
//        }
//        NavigateToFindingAction.navigateTo(project, location)
//    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    /**
     * Toggle between [TreeViewMode.List] and [TreeViewMode.GroupByFile].
     * The icon and tooltip change to reflect the current mode.
     */
    private inner class ToggleViewModeAction : AnAction() {
        override fun update(e: AnActionEvent) {
            val isGrouped = model.viewMode == TreeViewMode.GroupByFile
            e.presentation.icon        = if (isGrouped) AllIcons.Actions.ListFiles else AllIcons.Actions.GroupByFile
            e.presentation.text        = if (isGrouped) "Switch to List View" else "Group by File"
            e.presentation.description = if (isGrouped) "Show findings as a flat list"
                                         else "Group findings by source file"
        }
        override fun actionPerformed(e: AnActionEvent) {
            model.viewMode = if (model.viewMode == TreeViewMode.List)
                TreeViewMode.GroupByFile else TreeViewMode.List
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    /**
     * Resolve (or restore) the selected finding.
     * Resolved findings move to the Resolved panel and lose their editor highlight.
     */
    private inner class ResolveSelectedAction : AnAction(
        "Resolve/Restore Finding", "Toggle resolved state of selected finding",
        AllIcons.Actions.Commit
    ) {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = selectedEntryNode() != null
        }
        override fun actionPerformed(e: AnActionEvent) {
            val node = selectedEntryNode() ?: return
            store.toggleResolved(node.storeIndex)
            EditorHighlightManager.getInstance(project).reapplyAll()
            onEntryDeselected?.invoke()
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    /**
     * Delete the selected finding or note without confirmation (matches VS Code UX).
     * The confirmation dialog lives in [DeleteLocationUnderCursorAction] for the
     * keyboard path; toolbar delete is intentionally immediate for speed.
     */
    private inner class DeleteSelectedAction : AnAction(
        "Delete Finding", "Delete the selected finding or note",
        AllIcons.General.Remove
    ) {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = selectedEntryNode() != null
        }
        override fun actionPerformed(e: AnActionEvent) {
            val node = selectedEntryNode() ?: return
            store.removeEntry(node.storeIndex)
            EditorHighlightManager.getInstance(project).reapplyAll()
            onEntryDeselected?.invoke()
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class CollapseAllAction : AnAction(
        "Collapse All", "Collapse all tree nodes", AllIcons.Actions.Collapseall
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            for (i in tree.rowCount - 1 downTo 0) tree.collapseRow(i)
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

//    // ── Helpers ───────────────────────────────────────────────────────────────
//
//    private fun selectedEntryNode(): WeAuditTreeNode.EntryNode? {
//        val mutableNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
//        return model.nodeAt(mutableNode) as? WeAuditTreeNode.EntryNode
//    }
}

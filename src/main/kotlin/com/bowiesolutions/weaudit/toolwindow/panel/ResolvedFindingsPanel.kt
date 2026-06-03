package com.bowiesolutions.weaudit.toolwindow.panel

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.bowiesolutions.weaudit.actions.NavigateToFindingAction
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.store.WeAuditStore
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeCellRenderer
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeModel
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeNode
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

/**
 * The "Resolved Findings" tool-window panel.
 *
 * Port of the VS Code `resolvedFindings` tree view.  Shows all entries where
 * [Entry.resolved] == true.  Resolved entries are not highlighted in the editor
 * but remain in the `.weaudit` file so they can be inspected and restored.
 *
 * ## Toolbar actions
 * - **Restore** — calls [WeAuditStore.toggleResolved] to move the entry back to
 *   the active Findings panel and re-apply its editor highlight.
 * - **Delete** — permanently removes the resolved entry.
 *
 * Always uses [TreeViewMode.List] (groupByFile is less useful for resolved items).
 */
class ResolvedFindingsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val store  = WeAuditStore.getInstance(project)
    private val model  = WeAuditTreeModel(store, showResolved = true)
    private val tree   = Tree(model)

    private val changeListener: () -> Unit = {
        model.rebuild()
        tree.repaint()
    }

    init {
        tree.cellRenderer     = WeAuditTreeCellRenderer()
        tree.isRootVisible    = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        tree.addTreeSelectionListener(buildSelectionListener())

        val toolbar = buildToolbar()
        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        model.rebuild()
    }

    fun attach()  { store.addChangeListener(changeListener) }
    fun detach()  { store.removeChangeListener(changeListener) }

    private fun buildToolbar() = ActionManager.getInstance()
        .createActionToolbar(
            "WeAudit.ResolvedToolbar",
            DefaultActionGroup().apply {
                add(RestoreSelectedAction())
                add(DeleteSelectedAction())
            },
            true
        ).also { it.targetComponent = this }

    private fun buildSelectionListener(): TreeSelectionListener = TreeSelectionListener { e ->
        val mutableNode = e.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@TreeSelectionListener
        val node = model.nodeAt(mutableNode) ?: return@TreeSelectionListener
        val location = when (node) {
            is WeAuditTreeNode.EntryNode    -> node.entry.primaryLocation
            is WeAuditTreeNode.LocationNode -> node.location
            else -> return@TreeSelectionListener
        }
        NavigateToFindingAction.navigateTo(project, location)
    }

    private fun selectedEntryNode(): WeAuditTreeNode.EntryNode? {
        val mutableNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return model.nodeAt(mutableNode) as? WeAuditTreeNode.EntryNode
    }

    private inner class RestoreSelectedAction : AnAction(
        "Restore Finding", "Move the selected finding back to the active list",
        AllIcons.Actions.Rollback
    ) {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = selectedEntryNode() != null
        }
        override fun actionPerformed(e: AnActionEvent) {
            val node = selectedEntryNode() ?: return
            store.toggleResolved(node.storeIndex)
            EditorHighlightManager.getInstance(project).reapplyAll()
        }
        override fun getActionUpdateThread() =
            com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private inner class DeleteSelectedAction : AnAction(
        "Delete Finding", "Permanently delete the selected resolved finding",
        AllIcons.General.Remove
    ) {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = selectedEntryNode() != null
        }
        override fun actionPerformed(e: AnActionEvent) {
            val node = selectedEntryNode() ?: return
            store.removeEntry(node.storeIndex)
        }
        override fun getActionUpdateThread() =
            com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }
}

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
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.store.WeAuditStore
import com.bowiesolutions.weaudit.toolwindow.tree.AuditedFilesTreeModel
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeCellRenderer
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeNode
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

/**
 * The "Audited Files" tool-window panel.
 *
 * Port of the VS Code `savedFindings` tree view (confusingly named — it actually
 * shows audited/reviewed files, not saved findings).  Displays:
 *
 * - **Fully-audited files** — leaf nodes with a green check icon.
 * - **Partially-audited files** — parent nodes that expand to show reviewed regions.
 *
 * ## Toolbar
 * - **Unmark** — removes the selected audited or partially-audited file from the
 *   reviewed list and clears its editor highlight.
 *
 * ## Navigation
 * Clicking a partially-audited region navigates to its start line.
 * Clicking a fully-audited file navigates to line 0.
 */
class AuditedFilesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val store = WeAuditStore.getInstance(project)
    private val model = AuditedFilesTreeModel(store)
    private val tree  = Tree(model)

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
            "WeAudit.AuditedFilesToolbar",
            DefaultActionGroup().apply { add(UnmarkSelectedAction()) },
            true
        ).also { it.targetComponent = this }

    private fun buildSelectionListener(): TreeSelectionListener = TreeSelectionListener { e ->
        val mutableNode = e.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@TreeSelectionListener
        val node = model.nodeAt(mutableNode) ?: return@TreeSelectionListener
        val location: Location = when (node) {
            is WeAuditTreeNode.AuditedFileNode   ->
                Location(node.auditedFile.path, 0, 0)
            is WeAuditTreeNode.PartialFileNode   ->
                node.partialFile.regions.firstOrNull()
                    ?: Location(node.partialFile.path, 0, 0)
            is WeAuditTreeNode.PartialRegionNode ->
                node.location
            else -> return@TreeSelectionListener
        }
        NavigateToFindingAction.navigateTo(project, location)
    }

    private fun selectedNode(): WeAuditTreeNode? {
        val mutableNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return model.nodeAt(mutableNode)
    }

    private inner class UnmarkSelectedAction : AnAction(
        "Unmark as Reviewed", "Remove the selected file from the reviewed list",
        AllIcons.Actions.Cancel
    ) {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = when (selectedNode()) {
                is WeAuditTreeNode.AuditedFileNode -> true
                is WeAuditTreeNode.PartialFileNode -> true
                else -> false
            }
        }
        override fun actionPerformed(e: AnActionEvent) {
            when (val node = selectedNode()) {
                is WeAuditTreeNode.AuditedFileNode ->
                    store.unmarkFileAudited(node.auditedFile.path)
                is WeAuditTreeNode.PartialFileNode -> {
                    val newPartials = store.partiallyAuditedFiles
                        .filter { it.path != node.partialFile.path }
                    store.setPartiallyAuditedFiles(newPartials)
                }
                else -> {}
            }
        }
        override fun getActionUpdateThread() =
            com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }
}

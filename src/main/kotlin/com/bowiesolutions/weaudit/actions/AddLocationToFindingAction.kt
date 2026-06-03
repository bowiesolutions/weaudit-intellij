package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * "weAudit: Add Location to Existing Finding"
 *
 * Appends the current editor selection as an additional [Location] to an
 * existing finding chosen from a list — implementing the multi-region finding
 * feature from the VS Code extension.
 *
 * ## VS Code equivalent
 * In the VS Code extension, multi-region findings were created by dragging a
 * [LocationNode] onto an [EntryNode] in the tree, or via the context menu
 * "Add to finding".  Since IntelliJ drag-and-drop on a JTree requires a full
 * [TransferHandler] implementation (Phase 6), this action provides the keyboard
 * path: select code → invoke action → choose target finding from a popup.
 *
 * ## Interaction
 * 1. User selects code in the editor.
 * 2. Invokes this action (right-click → weAudit → Add Location to Finding).
 * 3. A chooser dialog lists all active (non-resolved) findings.
 * 4. User picks one.
 * 5. The selection is appended as a new [Location] to that finding.
 * 6. The finding's highlight is extended to cover the new region.
 *
 * Registered in `plugin.xml` with id `WeAudit.AddLocationToFinding`.
 */
class AddLocationToFindingAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return
        val store   = WeAuditStore.getInstance(project)

        val activeFindings = store.entries.withIndex()
            .filter { (_, entry) -> !entry.resolved }
            .map    { (index, entry) -> IndexedLabel(index, entry.label) }

        if (activeFindings.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No active findings to add a location to.\n" +
                "Create a finding first with Ctrl+3.",
                "weAudit: Add Location to Finding"
            )
            return
        }

        val labels = activeFindings.map { it.label }.toTypedArray()
        val choice = Messages.showChooseDialog(
            project,
            "Choose the finding to add this code region to:",
            "weAudit: Add Location to Finding",
            null,
            labels,
            labels[0]
        ) ?: return   // user cancelled; -1 also means cancel but Messages returns null

        if (choice < 0) return

        val selected = activeFindings[choice]
        val entry    = store.entries[selected.storeIndex]
        val newLoc   = selectionToLocation(editor, vFile, project)

        // Guard: don't add a duplicate location (same file+lines already present)
        if (entry.locations.any { it.path == newLoc.path &&
                it.startLine == newLoc.startLine &&
                it.endLine   == newLoc.endLine }) {
            Messages.showInfoMessage(
                project,
                "This exact region is already part of \"${entry.label}\".",
                "weAudit: Add Location to Finding"
            )
            return
        }

        val updated = entry.copy(locations = entry.locations + newLoc)
        mutateAndRefresh(project) {
            updateEntry(selected.storeIndex, updated)
        }
    }

    private data class IndexedLabel(val storeIndex: Int, val label: String)
}

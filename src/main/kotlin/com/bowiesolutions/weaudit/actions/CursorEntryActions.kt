package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * "weAudit: Delete Finding Under Cursor"
 *
 * VS Code command: `weaudit.deleteLocationUnderCursor`  (Cmd+5 / Ctrl+5)
 *
 * Removes the entry whose primary location's line range contains the caret.
 * If the caret is not inside any entry, shows a notice.
 *
 * Registered in `plugin.xml` with id `WeAudit.DeleteLocationUnderCursor`.
 */
class DeleteLocationUnderCursorAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return

        val index = entryIndexUnderCursor(editor, vFile, project)
        if (index < 0) {
            Messages.showInfoMessage(
                project,
                "No weAudit finding or note found at the cursor position.",
                "weAudit: Delete Finding"
            )
            return
        }

        val entry = WeAuditStore.getInstance(project).entries[index]
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete \"${entry.label}\"?",
            "weAudit: Delete Finding",
            Messages.getWarningIcon()
        )
        if (confirmed != Messages.YES) return

        mutateAndRefresh(project) { removeEntry(index) }
    }
}

/**
 * "weAudit: Edit Finding Under Cursor"
 *
 * VS Code command: `weaudit.editEntryUnderCursor`  (Cmd+6 / Ctrl+6)
 *
 * Presents an input dialog pre-filled with the current label, allowing the
 * user to rename the entry under the caret.
 *
 * Phase 4 will extend this to open the full Finding Details panel.
 *
 * Registered in `plugin.xml` with id `WeAudit.EditEntryUnderCursor`.
 */
class EditEntryUnderCursorAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return

        val store = WeAuditStore.getInstance(project)
        val index = entryIndexUnderCursor(editor, vFile, project)
        if (index < 0) {
            Messages.showInfoMessage(
                project,
                "No weAudit finding or note found at the cursor position.",
                "weAudit: Edit Finding"
            )
            return
        }

        val entry = store.entries[index]
        val newLabel = Messages.showInputDialog(
            project,
            "Edit label:",
            "weAudit: Edit Finding",
            Messages.getQuestionIcon(),
            entry.label,
            null,
        ) ?: return

        if (newLabel.isBlank() || newLabel == entry.label) return

        mutateAndRefresh(project) {
            updateEntry(index, entry.copy(label = newLabel))
        }
    }
}

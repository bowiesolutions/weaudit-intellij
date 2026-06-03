package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * "weAudit: Resolve/Restore Finding Under Cursor"
 *
 * Toggles the resolved state of whichever entry's location range contains the caret.
 * Resolved entries lose their editor highlight and move to the Resolved panel.
 * Restoring them re-applies the highlight.
 *
 * This is the keyboard path for resolve/restore; the toolbar button in
 * [FindingsPanel] and [ResolvedFindingsPanel] provides the same operation for
 * tree-selected entries.
 *
 * Registered in `plugin.xml` with id `WeAudit.ResolveUnderCursor`.
 * No default shortcut in the VS Code extension (operation was tree-only);
 * we add Ctrl/Cmd+8 as a natural extension of the existing shortcut sequence.
 */
class ResolveUnderCursorAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return
        val store   = WeAuditStore.getInstance(project)

        val index = entryIndexUnderCursor(editor, vFile, project)
        if (index < 0) {
            Messages.showInfoMessage(
                project,
                "No weAudit finding or note found at the cursor position.",
                "weAudit: Resolve Finding"
            )
            return
        }

        val entry  = store.entries[index]
        val action = if (entry.resolved) "Restore" else "Resolve"
        Messages.showInfoMessage(
            project,
            "$action \"${entry.label}\".",
            "weAudit: $action Finding"
        )

        store.toggleResolved(index)
        EditorHighlightManager.getInstance(project).reapplyAll()
    }
}

package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.bowiesolutions.weaudit.model.EntryType

/**
 * "weAudit: Add Selected Code To Findings"
 *
 * VS Code command: `weaudit.addFinding`  (default shortcut: Cmd+3 / Ctrl+3)
 *
 * Prompts the user for a label, then adds the selected code region as a new
 * Finding entry to the store and highlights it in the editor.
 *
 * Registered in `plugin.xml` with id `WeAudit.AddFinding`.
 */
class NewFindingAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return

        val label = Messages.showInputDialog(
            project,
            "Finding label:",
            "weAudit: New Finding",
            Messages.getQuestionIcon(),
            /* initialValue = */ "",
            /* validator    = */ null,
        ) ?: return   // user cancelled

        if (label.isBlank()) return

        val location = selectionToLocation(editor, vFile, project)
        val entry    = buildEntry(label, EntryType.Finding, location)

        mutateAndRefresh(project) { addEntry(entry) }
    }
}

/**
 * "weAudit: Add Selected Code To Notes"
 *
 * VS Code command: `weaudit.addNote`  (default shortcut: Cmd+4 / Ctrl+4)
 *
 * Identical to [NewFindingAction] but creates a Note entry (different color,
 * displayed after findings in the tree view).
 *
 * Registered in `plugin.xml` with id `WeAudit.AddNote`.
 */
class NewNoteAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return

        val label = Messages.showInputDialog(
            project,
            "Note label:",
            "weAudit: New Note",
            Messages.getQuestionIcon(),
            "",
            null,
        ) ?: return

        if (label.isBlank()) return

        val location = selectionToLocation(editor, vFile, project)
        val entry    = buildEntry(label, EntryType.Note, location)

        mutateAndRefresh(project) { addEntry(entry) }
    }
}

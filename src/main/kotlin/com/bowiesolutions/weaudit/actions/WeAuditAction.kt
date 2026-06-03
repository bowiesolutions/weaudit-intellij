package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * Base class for all weAudit [AnAction] subclasses.
 *
 * Provides shared helpers for:
 *  - Extracting the active editor, project, and virtual file from [AnActionEvent]
 *  - Building a [Location] from the current selection
 *  - Finding which entry (if any) the caret sits inside
 *  - Dispatching store mutations followed by a highlight refresh
 *
 * ## Threading
 * All actions run on the EDT ([ActionUpdateThread.EDT]).  Store mutations
 * dispatch their disk writes to a background thread internally.
 *
 * ## Pattern
 * Subclasses override [actionPerformed] and optionally [update].
 * Call [requiresEditor] in [update] to disable the action when no editor is open.
 */
abstract class WeAuditAction : AnAction() {

    /** All weAudit actions evaluate their enabled state on the EDT. */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    // ── Context helpers ───────────────────────────────────────────────────────

    protected fun AnActionEvent.editor():  Editor?      = getData(CommonDataKeys.EDITOR)
    protected fun AnActionEvent.project(): Project?     = getData(CommonDataKeys.PROJECT)
    protected fun AnActionEvent.vFile():   VirtualFile? = getData(CommonDataKeys.VIRTUAL_FILE)

    /**
     * Build a [Location] from the current editor selection.
     *
     * Lines are 0-based in both the IntelliJ Document API and the `.weaudit`
     * JSON format, so no conversion is needed.
     *
     * If there is no selection, returns a single-line location at the caret.
     */
    protected fun selectionToLocation(editor: Editor, vFile: VirtualFile, project: Project): Location {
        val doc    = editor.document
        val sel    = editor.selectionModel
        val basePath = project.basePath ?: ""

        val (startLine, endLine) = if (sel.hasSelection()) {
            val s = doc.getLineNumber(sel.selectionStart)
            val e = doc.getLineNumber(
                // If selection ends exactly at the start of a line (user triple-clicked
                // and the end offset is the newline), don't include that blank line.
                if (sel.selectionEnd > 0 &&
                    sel.selectionEnd == doc.getLineStartOffset(doc.getLineNumber(sel.selectionEnd)))
                    sel.selectionEnd - 1 else sel.selectionEnd
            )
            s to e
        } else {
            val line = doc.getLineNumber(editor.caretModel.offset)
            line to line
        }

        // Build workspace-relative path: strip leading basePath + separator.
        val absolutePath = vFile.path
        val relativePath = if (basePath.isNotEmpty() && absolutePath.startsWith(basePath)) {
            absolutePath.removePrefix(basePath).trimStart('/')
        } else {
            absolutePath
        }

        return Location(
            path      = relativePath,
            startLine = startLine,
            endLine   = endLine,
        )
    }

    /**
     * Find the index of the first entry whose primary location's line range
     * contains the current caret position in [editor].
     * Returns -1 if no entry overlaps the caret.
     */
    protected fun entryIndexUnderCursor(editor: Editor, vFile: VirtualFile, project: Project): Int {
        val store     = WeAuditStore.getInstance(project)
        val caretLine = editor.document.getLineNumber(editor.caretModel.offset)
        val basePath  = project.basePath ?: ""
        val absolutePath = vFile.path
        val relativePath = if (basePath.isNotEmpty() && absolutePath.startsWith(basePath)) {
            absolutePath.removePrefix(basePath).trimStart('/')
        } else absolutePath

        return store.entries.indexOfFirst { entry ->
            entry.locations.any { loc ->
                loc.path == relativePath &&
                caretLine >= loc.startLine &&
                caretLine <= loc.endLine
            }
        }
    }

    /**
     * Convenience: mutate the store and immediately refresh all highlights.
     * Always called on the EDT.
     */
    protected fun mutateAndRefresh(project: Project, mutation: WeAuditStore.() -> Unit) {
        val store = WeAuditStore.getInstance(project)
        store.mutation()
        EditorHighlightManager.getInstance(project).reapplyAll()
    }

    /** Mark the action as visible/enabled only when an editor is open. */
    protected fun requiresEditor(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.editor() != null
    }

    /** Mark the action as visible/enabled only when an editor with a selection is open. */
    protected fun requiresSelection(e: AnActionEvent) {
        val editor = e.editor()
        e.presentation.isEnabledAndVisible =
            editor != null && editor.selectionModel.hasSelection()
    }

    // ── Entry construction helpers ────────────────────────────────────────────

    protected fun buildEntry(
        label:     String,
        type:      EntryType,
        location:  Location,
    ): Entry = Entry(
        label     = label,
        entryType = type.jsonValue,
        author    = WeAuditSettingsState.getInstance().username,
        locations = listOf(location),
    )
}

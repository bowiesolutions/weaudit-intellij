package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.bowiesolutions.weaudit.boundary.BoundaryEditManager
import com.bowiesolutions.weaudit.store.WeAuditStore
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory

// ─────────────────────────────────────────────────────────────────────────────
// Enter / exit boundary editing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "weAudit: Edit Finding Boundary"
 *
 * VS Code command: `weaudit.editFindingBoundary`
 * Keyboard shortcut: Ctrl/Cmd+B
 *
 * Enters boundary editing mode for the finding under the cursor.
 * If boundary editing is already active, commits the current session instead
 * (toggle behaviour, same as VS Code).
 *
 * Registered in `plugin.xml` with id `WeAudit.EditFindingBoundary`.
 */
class EditFindingBoundaryAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return
        val manager = BoundaryEditManager.getInstance(project)

        // Toggle: if already editing, commit and exit
        if (manager.isActive) {
            manager.commitSession()
            refreshHints(editor, project)
            return
        }

        val index = entryIndexUnderCursor(editor, vFile, project)
        if (index < 0) {
            Messages.showInfoMessage(
                project,
                "Place the cursor inside a highlighted finding to edit its boundary.",
                "weAudit: Edit Finding Boundary"
            )
            return
        }

        // If the finding has multiple locations, pick the one under the cursor
        val store     = WeAuditStore.getInstance(project)
        val entry     = store.entries[index]
        val caretLine = editor.document.getLineNumber(editor.caretModel.offset)
        val basePath  = project.basePath ?: ""
        val relPath   = if (vFile.path.startsWith(basePath))
            vFile.path.removePrefix(basePath).trimStart('/') else vFile.path

        val locIndex = entry.locations.indexOfFirst { loc ->
            loc.path == relPath && caretLine >= loc.startLine && caretLine <= loc.endLine
        }.coerceAtLeast(0)

        manager.startSession(index, locIndex)
        refreshHints(editor, project)
    }
}

/**
 * "weAudit: Done Editing Boundary"
 *
 * Commits the active boundary editing session.
 * Bound to Escape via `plugin.xml` keyboard shortcut override.
 *
 * Registered in `plugin.xml` with id `WeAudit.DoneBoundaryEdit`.
 */
class DoneBoundaryEditAction : WeAuditAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project()
        e.presentation.isEnabledAndVisible =
            project != null && BoundaryEditManager.getInstance(project).isActive
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        BoundaryEditManager.getInstance(project).commitSession()
        refreshHints(editor, project)
    }
}

/**
 * "weAudit: Cancel Boundary Edit"
 *
 * Discards the active boundary editing session without saving.
 *
 * Registered in `plugin.xml` with id `WeAudit.CancelBoundaryEdit`.
 */
class CancelBoundaryEditAction : WeAuditAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project()
        e.presentation.isEnabledAndVisible =
            project != null && BoundaryEditManager.getInstance(project).isActive
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        BoundaryEditManager.getInstance(project).cancelSession()
        refreshHints(editor, project)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Boundary adjustment actions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Expand the start (top) boundary upward by one line.
 * VS Code CodeLens label: "▲ Add line above"
 * Shortcut: Ctrl/Cmd+Up  (only active during boundary editing)
 */
class ExpandStartBoundaryAction : BoundaryAdjustAction() {
    override fun adjust(manager: BoundaryEditManager, maxLine: Int) =
        manager.expandStart()
}

/**
 * Shrink the start (top) boundary downward by one line.
 * VS Code CodeLens label: "▼ Remove line above"
 * Shortcut: Ctrl/Cmd+Shift+Down
 */
class ShrinkStartBoundaryAction : BoundaryAdjustAction() {
    override fun adjust(manager: BoundaryEditManager, maxLine: Int) =
        manager.shrinkStart()
}

/**
 * Expand the end (bottom) boundary downward by one line.
 * VS Code CodeLens label: "▼ Add line below"
 * Shortcut: Ctrl/Cmd+Down
 */
class ExpandEndBoundaryAction : BoundaryAdjustAction() {
    override fun adjust(manager: BoundaryEditManager, maxLine: Int) =
        manager.expandEnd(maxLine = maxLine)
}

/**
 * Shrink the end (bottom) boundary upward by one line.
 * VS Code CodeLens label: "▲ Remove line below"
 * Shortcut: Ctrl/Cmd+Shift+Up
 */
class ShrinkEndBoundaryAction : BoundaryAdjustAction() {
    override fun adjust(manager: BoundaryEditManager, maxLine: Int) =
        manager.shrinkEnd()
}

/**
 * Move the entire boundary up by one line (start and end together).
 * VS Code CodeLens label: "↑ Move up"
 * Shortcut: Alt+Up
 */
class MoveBoundaryUpAction : BoundaryAdjustAction() {
    override fun adjust(manager: BoundaryEditManager, maxLine: Int) =
        manager.moveUp()
}

/**
 * Move the entire boundary down by one line.
 * VS Code CodeLens label: "↓ Move down"
 * Shortcut: Alt+Down
 */
class MoveBoundaryDownAction : BoundaryAdjustAction() {
    override fun adjust(manager: BoundaryEditManager, maxLine: Int) =
        manager.moveDown(maxLine = maxLine)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared base for adjustment actions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Base for all boundary adjustment actions.
 * Only enabled when a [BoundaryEditSession] is active.
 */
abstract class BoundaryAdjustAction : WeAuditAction() {

    abstract fun adjust(manager: BoundaryEditManager, maxLine: Int)

    override fun update(e: AnActionEvent) {
        val project = e.project()
        e.presentation.isEnabledAndVisible =
            project != null && BoundaryEditManager.getInstance(project).isActive
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val manager = BoundaryEditManager.getInstance(project)
        if (!manager.isActive) return

        val maxLine = (editor.document.lineCount - 1).coerceAtLeast(0)
        adjust(manager, maxLine)
        refreshHints(editor, project)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared hint-refresh helper
// ─────────────────────────────────────────────────────────────────────────────

internal fun refreshHints(
    editor:  com.intellij.openapi.editor.Editor,
    project: com.intellij.openapi.project.Project,
) {
    try {
        DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
    } catch (_: Exception) {
        // If the declarative API is unavailable (older build), fail silently —
        // the hints will refresh on the next natural repaint.
    }
}

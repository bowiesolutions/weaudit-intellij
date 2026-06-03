package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.bowiesolutions.weaudit.model.Location

/**
 * Navigates the editor to a specific [Location] from the weAudit data model.
 *
 * This action is not directly keyboard-bound.  It is invoked programmatically:
 *  - Phase 3: from the tree-view click handler when the user selects a finding.
 *  - Future: from any "Go to location" affordance.
 *
 * It can also be triggered as an [AnAction] if registered, allowing future
 * keyboard shortcut binding for "navigate to next/previous finding".
 */
class NavigateToFindingAction : WeAuditAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = false // Not shown in menus; invoked programmatically.
    }

    override fun actionPerformed(e: AnActionEvent) {
        // Not used via the action system directly in Phase 2.
    }

    companion object {
        /**
         * Navigate the editor to [location] within [project].
         *
         * Opens the file if not already open, scrolls to [location.startLine],
         * and moves the caret to column 0 on that line.
         *
         * This is the IntelliJ equivalent of:
         * ```typescript
         * vscode.window.showTextDocument(document, {
         *   selection: new vscode.Range(startLine, 0, endLine, 0)
         * });
         * ```
         */
        fun navigateTo(project: Project, location: Location) {
            val basePath = project.basePath ?: return
            val absolutePath = "$basePath/${location.path}"
            val vFile = LocalFileSystem.getInstance()
                .findFileByPath(absolutePath) ?: return

            val descriptor = OpenFileDescriptor(
                project,
                vFile,
                location.startLine,
                /* column = */ 0
            )
            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(project)
                    .openTextEditor(descriptor, /* focusEditor = */ true)
            }
        }
    }
}

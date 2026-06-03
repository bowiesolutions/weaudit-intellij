package com.bowiesolutions.weaudit.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Re-applies weAudit highlights whenever an editor becomes visible.
 *
 * ## Why this is necessary
 * IntelliJ does not automatically replay [RangeHighlighter]s when an editor
 * is opened or a tab is brought to focus. The VS Code extension handled this
 * via `onDidChangeVisibleTextEditors`, calling `setDecorations` for every
 * newly-visible editor. This listener is the direct equivalent.
 *
 * [DocumentMarkupModel]-based highlights (which we use) **do** survive a tab
 * close/reopen within a session — but only if the document stayed in memory.
 * When IntelliJ evicts a document (e.g. on low memory or after a GC cycle),
 * the highlights are lost. [reapplyAll] is cheap enough to call on every open.
 *
 * ## Registration
 * Registered as a project-level message-bus listener in `plugin.xml`:
 * ```xml
 * <projectListeners>
 *   <listener class="com.bowiesolutions.weaudit.editor.EditorOpenListener"
 *             topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"/>
 * </projectListeners>
 * ```
 */
class EditorOpenListener(private val project: Project) : FileEditorManagerListener {

    /**
     * Called on the EDT when a file is opened in an editor for the first time.
     * [reapplyAll] is O(entries × open-files), which is fast in practice.
     */
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        EditorHighlightManager.getInstance(project).reapplyAll()
    }
}

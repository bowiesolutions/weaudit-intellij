package com.bowiesolutions.weaudit.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import com.bowiesolutions.weaudit.store.WeAuditStore
import java.awt.Color

/**
 * Port of `src/decorationManager.ts` from trailofbits/vscode-weaudit.
 *
 * Manages the full lifecycle of [RangeHighlighter]s for:
 *  - **Findings** (own and co-auditor) — red/orange background
 *  - **Notes** (own and co-auditor)    — blue/purple background
 *  - **Audited files**                 — green background (whole file)
 *  - **Partially-audited regions**     — green background (line range)
 *
 * ## VS Code → IntelliJ mapping
 *
 * | VS Code                              | IntelliJ                                      |
 * |--------------------------------------|-----------------------------------------------|
 * | `createTextEditorDecorationType`     | `TextAttributes` (built once per color)       |
 * | `editor.setDecorations(type, ranges)`| `markupModel.addRangeHighlighter(...)`        |
 * | `type.dispose()`                     | `markupModel.removeHighlighter(h)` per entry  |
 * | Re-apply on `onDidChangeVisible…`    | Re-apply in `FileEditorManagerListener`       |
 *
 * ## Threading
 * All public methods **must be called on the EDT**.  The [WeAuditStore] change
 * listener and [EditorOpenListener] already guarantee this.
 *
 * ## Highlight persistence
 * `DocumentMarkupModel.forDocument(doc, project, true)` returns the document-level
 * markup model — shared across all editors of the same document and alive as long
 * as the document is open.  Highlights survive tab close/reopen **within a session**.
 * On project reopen, [reapplyAll] is called from [WeAuditStartupActivity].
 *
 * Registered in `plugin.xml` as a project service:
 * ```xml
 * <projectService serviceImplementation=
 *     "com.bowiesolutions.weaudit.editor.EditorHighlightManager"/>
 * ```
 */
@Service(Service.Level.PROJECT)
class EditorHighlightManager(private val project: Project) {

    private val log = logger<EditorHighlightManager>()

    /**
     * Live highlighters keyed by a stable identity string.
     * Key format: `"<author>:<filePath>:<startLine>:<endLine>"` for entry locations,
     *             `"audited:<filePath>"` for audited files,
     *             `"partial:<filePath>:<startLine>:<endLine>"` for partial regions.
     *
     * Using a string key (rather than storing the [RangeHighlighter] directly on
     * the entry) decouples the model from the editor layer — matching the VS Code
     * extension's approach of re-deriving decoration ranges from the data model.
     */
    private val highlighters: MutableMap<String, RangeHighlighter> = mutableMapOf()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Re-create all highlights from the current [WeAuditStore] state.
     * Called:
     *   1. From [WeAuditStartupActivity] after data loads.
     *   2. From [EditorOpenListener] when a new editor becomes visible.
     *   3. From the store's change listener after every mutation.
     *
     * Clears all existing highlights first so stale markers don't accumulate.
     * Safe to call multiple times (idempotent).
     */
    fun reapplyAll() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        clearAll()

        if (!WeAuditSettingsState.getInstance().highlightingEnabled) return

        val store    = WeAuditStore.getInstance(project)
        val settings = WeAuditSettingsState.getInstance()
        val username = settings.username

        // Own + co-auditor entry highlights
        for (userData in store.allUsersData.entries + mapOf(username to
                com.bowiesolutions.weaudit.model.SerializedData(
                    store.entries, store.auditedFiles, store.partiallyAuditedFiles
                )).entries) {
            val isOwn = userData.key == username
            for (entry in userData.value.treeEntries) {
                if (entry.resolved) continue
                applyEntryHighlights(entry, isOwn)
            }
        }

        // Own audited files
        for (audited in store.auditedFiles) {
            applyAuditedFileHighlight(audited)
        }

        // Own partially-audited regions
        for (partial in store.partiallyAuditedFiles) {
            applyPartialHighlights(partial)
        }
    }

    /**
     * Remove all weAudit highlighters from every open document.
     * Called when the user toggles "Toggle Findings Highlighting".
     */
    fun clearAll() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val toRemove = highlighters.values.toList()
        highlighters.clear()
        for (h in toRemove) {
            removeHighlighterSafely(h)
        }
    }

    /**
     * Remove all highlighters for a single [Entry] across all its locations.
     * Called after delete/resolve operations so the editor updates immediately
     * without a full [reapplyAll].
     */
    fun clearEntry(entry: Entry, author: String) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        for (loc in entry.locations) {
            val key = entryKey(author, loc)
            highlighters.remove(key)?.let { removeHighlighterSafely(it) }
        }
    }

    /**
     * Apply/refresh highlights for a single [Entry].
     * Called after add/edit operations for a fast incremental update.
     */
    fun applyEntry(entry: Entry, isOwn: Boolean) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!WeAuditSettingsState.getInstance().highlightingEnabled) return
        if (entry.resolved) return
        applyEntryHighlights(entry, isOwn)
    }

    companion object {
        fun getInstance(project: Project): EditorHighlightManager = project.service()

        /**
         * UserData key tagging each [RangeHighlighter] with the weAudit identity
         * string, so stale highlights can be identified during cleanup.
         */
        val HIGHLIGHT_KEY: Key<String> = Key.create("weaudit.highlight.key")

        // Highlighter layers — just below the caret/selection layer so colors
        // remain visible during navigation, matching VS Code's z-ordering.
        const val LAYER_FINDING = HighlighterLayer.SELECTION - 2
        const val LAYER_NOTE    = HighlighterLayer.SELECTION - 3
        const val LAYER_AUDITED = HighlighterLayer.SELECTION - 4
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun applyEntryHighlights(entry: Entry, isOwn: Boolean) {
        val settings = WeAuditSettingsState.getInstance()
        val colorHex = when {
            isOwn && entry.entryTypeEnum == EntryType.Finding -> settings.findingColor
            isOwn && entry.entryTypeEnum == EntryType.Note    -> settings.noteColor
            !isOwn && entry.entryTypeEnum == EntryType.Finding-> settings.otherFindingColor
            else                                               -> settings.otherNoteColor
        }
        val layer = if (entry.entryTypeEnum == EntryType.Finding) LAYER_FINDING else LAYER_NOTE
        val attrs = buildAttributes(colorHex)
        val author = entry.author

        for (loc in entry.locations) {
            val vf = resolveVirtualFile(loc.path) ?: continue
            val key = entryKey(author, loc)
            addHighlighter(vf, loc.startLine, loc.endLine, layer, attrs, key)
        }
    }

    private fun applyAuditedFileHighlight(audited: AuditedFile) {
        val vf = resolveVirtualFile(audited.path) ?: return
        ApplicationManager.getApplication().runReadAction {
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager
                .getInstance().getDocument(vf) ?: return@runReadAction
            val lastLine = (doc.lineCount - 1).coerceAtLeast(0)
            val key   = "audited:${audited.path}"
            val attrs = buildAttributes(WeAuditSettingsState.getInstance().auditedFileColor)
            addHighlighter(vf, 0, lastLine, LAYER_AUDITED, attrs, key)
        }
    }

    private fun applyPartialHighlights(partial: PartiallyAuditedFile) {
        val attrs = buildAttributes(WeAuditSettingsState.getInstance().auditedFileColor)
        for (region in partial.regions) {
            val vf  = resolveVirtualFile(region.path) ?: continue
            val key = "partial:${region.path}:${region.startLine}:${region.endLine}"
            addHighlighter(vf, region.startLine, region.endLine, LAYER_AUDITED, attrs, key)
        }
    }

    /**
     * Core highlighter-creation routine.
     *
     * Uses [DocumentMarkupModel.forDocument] (document-level, not editor-level)
     * so highlights are shared across split editors and survive tab close/reopen
     * within a session.
     *
     * Lines are 0-based in the `.weaudit` format and in IntelliJ's Document API —
     * they match directly with no conversion needed.
     *
     * [HighlighterTargetArea.LINES_IN_RANGE] colours entire lines, matching
     * VS Code's whole-line decoration behaviour.
     */
    private fun addHighlighter(
        vf:        VirtualFile,
        startLine: Int,
        endLine:   Int,
        layer:     Int,
        attrs:     TextAttributes,
        key:       String,
    ) {
        ApplicationManager.getApplication().runReadAction {
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager
                .getInstance().getDocument(vf) ?: return@runReadAction

            val lineCount = doc.lineCount
            if (lineCount == 0) return@runReadAction
            val safeStart = startLine.coerceIn(0, lineCount - 1)
            val safeEnd   = endLine.coerceIn(safeStart, lineCount - 1)

            val startOffset = doc.getLineStartOffset(safeStart)
            val endOffset   = doc.getLineEndOffset(safeEnd)

            val markupModel: MarkupModel =
                DocumentMarkupModel.forDocument(doc, project, /* create = */ true)

            val h = markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                layer,
                attrs,
                HighlighterTargetArea.LINES_IN_RANGE
            )
            h.putUserData(HIGHLIGHT_KEY, key)
            highlighters[key] = h
        }
    }

    private fun removeHighlighterSafely(h: RangeHighlighter) {
        try {
            if (h.isValid) h.dispose()
        } catch (e: Exception) {
            log.warn("Failed to dispose highlighter", e)
        }
    }

    /**
     * Build [TextAttributes] from a hex color string (#RRGGBB or #RRGGBBAA).
     * Returns a plain background-only attribute — font weight/style are left
     * as [com.intellij.openapi.editor.markup.FontType.PLAIN] so the IDE's
     * syntax highlight and bold/italic settings are preserved.
     */
    private fun buildAttributes(hexColor: String): TextAttributes {
        val color = parseHex(hexColor) ?: return TextAttributes()
        return TextAttributes().apply {
            backgroundColor = color
        }
    }

    private fun parseHex(hex: String): Color? = try {
        val clean = hex.trimStart('#')
        when (clean.length) {
            6 -> Color(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
            8 -> Color(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16),
                clean.substring(6, 8).toInt(16)
            )
            else -> null
        }
    } catch (_: NumberFormatException) { null }

    /**
     * Resolve a workspace-relative path to a [VirtualFile].
     * The `.weaudit` format stores paths relative to the project base directory
     * (the directory containing `.vscode/`).
     */
    private fun resolveVirtualFile(relativePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val absolute = "$basePath/$relativePath"
        return LocalFileSystem.getInstance().findFileByPath(absolute)
    }

    private fun entryKey(author: String, loc: Location) =
        "$author:${loc.path}:${loc.startLine}:${loc.endLine}"
}

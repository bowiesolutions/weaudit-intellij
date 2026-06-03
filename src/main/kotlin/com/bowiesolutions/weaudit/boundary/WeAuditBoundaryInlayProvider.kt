package com.bowiesolutions.weaudit.boundary

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Inlay hints provider for boundary editing mode.
 *
 * Port of the VS Code CodeLens provider that showed clickable controls at
 * the top and bottom of the active finding's highlighted region during
 * boundary editing.
 *
 * ## What is shown
 * When a [BoundaryEditSession] is active for the current file, this provider
 * inserts inline hints at the start and end lines of the region:
 *
 * ```
 * ▲+ expand  ▼- shrink  ↕ move up    ← at startLine (top boundary)
 * ▼+ expand  ▲- shrink  ↕ move down  ← at endLine   (bottom boundary)
 * ✓ Done                              ← at endLine (always shown during session)
 * ```
 *
 * Each hint is a clickable action that delegates to [BoundaryEditManager].
 *
 * ## Registration
 * ```xml
 * <codeInsight.inlayProvider
 *     language=""
 *     implementationClass="com.bowiesolutions.weaudit.boundary.WeAuditBoundaryInlayProvider"
 *     isEnabledByDefault="true"
 *     group="OTHER_GROUP"
 *     providerId="com.bowiesolutions.weaudit.boundary"/>
 * ```
 *
 * Note: `language=""` means the provider runs for all file types, matching the
 * VS Code CodeLens which was registered for `{ scheme: 'file' }` (all files).
 *
 * ## Declarative Inlay API
 * Uses IntelliJ's newer declarative inlay hints API (2023.1+) rather than
 * the older `InlayHintsProvider<T>` to avoid the complex settings/key model.
 */
class WeAuditBoundaryInlayProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val project = file.project
        val manager = BoundaryEditManager.getInstance(project)
        val session = manager.activeSession ?: return null

        // Only show hints for the file being edited
        val filePath = file.virtualFile?.path ?: return null
        val basePath = project.basePath ?: return null
        val relativePath = if (filePath.startsWith(basePath))
            filePath.removePrefix(basePath).trimStart('/')
        else return null

        if (relativePath != session.filePath) return null

        return BoundaryHintsCollector(session, editor)
    }

    // ── Collector ─────────────────────────────────────────────────────────────

    private class BoundaryHintsCollector(
        private val session: BoundaryEditSession,
        private val editor:  Editor,
    ) : SharedBypassCollector {

        override fun collectFromElement(
            element: com.intellij.psi.PsiElement,
            sink:    InlayTreeSink,
        ) {
            // Only collect once from the root file element to avoid duplicate hints
            if (element.parent != null) return

            val doc       = editor.document
            val lineCount = doc.lineCount
            if (lineCount == 0) return

            val startLine = session.startLine.coerceIn(0, lineCount - 1)
            val endLine   = session.endLine.coerceIn(0, lineCount - 1)

            // ── Top boundary hints (at startLine) ─────────────────────────────
            val startOffset = doc.getLineEndOffset(startLine)

            sink.addPresentation(
                position    = InlineInlayPosition(startOffset, relatedToPrevious = false),
                hasBackground = true,
            ) {
                text("  ▲ Expand start ", actionData(ACTION_EXPAND_START))
                text("| ▼ Shrink start ", actionData(ACTION_SHRINK_START))
                text("| ↑ Move up",       actionData(ACTION_MOVE_UP))
            }

            // ── Bottom boundary hints (at endLine) ────────────────────────────
            if (endLine != startLine) {
                val endOffset = doc.getLineEndOffset(endLine)

                sink.addPresentation(
                    position    = InlineInlayPosition(endOffset, relatedToPrevious = false),
                    hasBackground = true,
                ) {
                    text("  ▼ Expand end ", actionData(ACTION_EXPAND_END))
                    text("| ▲ Shrink end ", actionData(ACTION_SHRINK_END))
                    text("| ↓ Move down  ", actionData(ACTION_MOVE_DOWN))
                    text("| ✓ Done",        actionData(ACTION_DONE))
                }
            } else {
                // Single-line region — show done inline with the start hints
                val startEndOffset = doc.getLineEndOffset(startLine)
                sink.addPresentation(
                    position    = InlineInlayPosition(startEndOffset, relatedToPrevious = false),
                    hasBackground = true,
                ) {
                    text("  ✓ Done", actionData(ACTION_DONE))
                }
            }
        }

        private fun actionData(actionId: String): InlayActionData =
            InlayActionData(
                StringInlayActionPayload(actionId),
                HANDLER_ID
            )
    }

    companion object {
        const val HANDLER_ID       = "com.bowiesolutions.weaudit.boundary"

        const val ACTION_EXPAND_START = "expandStart"
        const val ACTION_SHRINK_START = "shrinkStart"
        const val ACTION_EXPAND_END   = "expandEnd"
        const val ACTION_SHRINK_END   = "shrinkEnd"
        const val ACTION_MOVE_UP      = "moveUp"
        const val ACTION_MOVE_DOWN    = "moveDown"
        const val ACTION_DONE         = "done"
    }
}

// ── Action handler (click handler for hints) ──────────────────────────────────

/**
 * Handles clicks on the boundary inlay hints.
 * Registered alongside the provider in `plugin.xml`.
 *
 * ```xml
 * <codeInsight.inlayActionHandler
 *     implementationClass="com.bowiesolutions.weaudit.boundary.BoundaryInlayActionHandler"
 *     handlerId="com.bowiesolutions.weaudit.boundary"/>
 * ```
 */
class BoundaryInlayActionHandler : InlayActionHandler {

    override fun handleClick(editor: Editor, payload: InlayActionPayload) {
        val project = editor.project ?: return
        val manager = BoundaryEditManager.getInstance(project)
        if (!manager.isActive) return

        val actionId = (payload as? StringInlayActionPayload)?.text ?: return
        val maxLine  = (editor.document.lineCount - 1).coerceAtLeast(0)

        when (actionId) {
            WeAuditBoundaryInlayProvider.ACTION_EXPAND_START -> manager.expandStart()
            WeAuditBoundaryInlayProvider.ACTION_SHRINK_START -> manager.shrinkStart()
            WeAuditBoundaryInlayProvider.ACTION_EXPAND_END   -> manager.expandEnd(maxLine = maxLine)
            WeAuditBoundaryInlayProvider.ACTION_SHRINK_END   -> manager.shrinkEnd()
            WeAuditBoundaryInlayProvider.ACTION_MOVE_UP      -> manager.moveUp()
            WeAuditBoundaryInlayProvider.ACTION_MOVE_DOWN    -> manager.moveDown(maxLine = maxLine)
            WeAuditBoundaryInlayProvider.ACTION_DONE         -> manager.commitSession()
        }

        // Refresh inlay hints so the new boundary position is reflected
        com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
            .scheduleRecompute(editor, project)
    }
}

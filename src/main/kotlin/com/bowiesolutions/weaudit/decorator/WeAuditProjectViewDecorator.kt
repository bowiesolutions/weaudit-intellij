package com.bowiesolutions.weaudit.decorator

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.ui.PackageDependenciesNode
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * Decorates Project View file nodes with weAudit status badges.
 *
 * Port of the VS Code `FileDecorationProvider` in `codeMarker.ts`, which added
 * `!` badges to files with active findings and `✓` badges to audited files.
 *
 * ## Badge meanings
 * - **`!`** (red)  — file has one or more active (non-resolved) findings
 * - **`✓`** (green) — file is fully marked as reviewed
 * - **`~`** (blue)  — file has partially-reviewed regions
 *
 * When a file has both findings and audited status, the finding badge takes
 * priority (matching VS Code's behaviour).
 *
 * ## Registration
 * Registered in `plugin.xml`:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <projectViewNodeDecorator
 *       implementation="com.bowiesolutions.weaudit.decorator.WeAuditProjectViewDecorator"/>
 * </extensions>
 * ```
 *
 * ## Threading
 * Called by the platform on the EDT when the Project View repaints. The store
 * access is a read-only in-memory operation so it is safe here.
 */
class WeAuditProjectViewDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: com.intellij.ide.projectView.PresentationData) {
        val project  = node.project ?: return
        val vFile    = node.virtualFile ?: return
        val basePath = project.basePath ?: return

        // Build workspace-relative path from the virtual file
        val relativePath = if (vFile.path.startsWith(basePath)) {
            vFile.path.removePrefix(basePath).trimStart('/')
        } else return   // file is outside project root — skip

        val store = try {
            WeAuditStore.getInstance(project)
        } catch (_: Exception) {
            return   // service not available yet
        }

        val badge = when {
            // Finding takes highest priority
            store.entries.any { entry ->
                !entry.resolved && entry.locations.any { it.path == relativePath }
            } -> Badge.FINDING

            // Fully audited
            store.auditedFiles.any { it.path == relativePath } -> Badge.AUDITED

            // Partially audited
            store.partiallyAuditedFiles.any { it.path == relativePath } -> Badge.PARTIAL

            else -> return   // no decoration needed
        }

        data.locationString = badge.label
        data.setAttributesKey(badge.attributesKey)
    }

    override fun decorate(node: PackageDependenciesNode, cellRenderer: ColoredTreeCellRenderer) {
        // Not used — we only decorate the Project View, not dependency graphs
    }

    // ── Badge definitions ─────────────────────────────────────────────────────

    private enum class Badge(
        val label:         String,
        val attributesKey: com.intellij.openapi.editor.colors.TextAttributesKey,
    ) {
        FINDING("!", FINDING_ATTR),
        AUDITED("✓", AUDITED_ATTR),
        PARTIAL("~", PARTIAL_ATTR),
    }

    companion object {
        // Text attribute keys for the badge colors.
        // Defined as simple keys here; optionally expose them in
        // Settings → Editor → Color Scheme → weAudit in a future phase.
        private val FINDING_ATTR = com.intellij.openapi.editor.colors.TextAttributesKey
            .createTextAttributesKey("WEAUDIT_FINDING_BADGE",
                com.intellij.openapi.editor.markup.TextAttributes().apply {
                    foregroundColor = JBColor(0xCC2200, 0xFF6666)
                })

        private val AUDITED_ATTR = com.intellij.openapi.editor.colors.TextAttributesKey
            .createTextAttributesKey("WEAUDIT_AUDITED_BADGE",
                com.intellij.openapi.editor.markup.TextAttributes().apply {
                    foregroundColor = JBColor(0x007700, 0x66CC66)
                })

        private val PARTIAL_ATTR = com.intellij.openapi.editor.colors.TextAttributesKey
            .createTextAttributesKey("WEAUDIT_PARTIAL_BADGE",
                com.intellij.openapi.editor.markup.TextAttributes().apply {
                    foregroundColor = JBColor(0x0055CC, 0x6699FF)
                })
    }
}

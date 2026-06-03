package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.Messages
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * "weAudit: Mark Current File As Reviewed" / Toggle
 *
 * VS Code command: `weaudit.toggleAudited`  (Cmd+7 / Ctrl+7)
 *
 * Toggles the fully-audited state of the active file:
 *  - If not audited: marks it, removes any partial regions for that file,
 *    applies a green highlight over the whole file.
 *  - If already audited: removes the audited mark and its highlight.
 *
 * Registered in `plugin.xml` with id `WeAudit.ToggleAudited`.
 */
class ToggleAuditedAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return
        val store   = WeAuditStore.getInstance(project)

        val relativePath = vFile.relativeToProject(project) ?: return

        if (store.auditedFiles.any { it.path == relativePath }) {
            mutateAndRefresh(project) { unmarkFileAudited(relativePath) }
        } else {
            // Mark audited: also remove any partial regions for this file.
            val newPartials = store.partiallyAuditedFiles
                .filter { it.path != relativePath }
            mutateAndRefresh(project) {
                setPartiallyAuditedFiles(newPartials)
                markFileAudited(relativePath)
            }
        }
    }
}

/**
 * "weAudit: Mark Region As Reviewed"
 *
 * VS Code command: `weaudit.addPartiallyAudited`  (Cmd+Shift+7 / Ctrl+Shift+7)
 *
 * Marks the selected region as reviewed, merging/splitting existing partial
 * regions as needed.  The exact merge/split logic follows the VS Code extension:
 *
 *  - **Exact match**: unmarks the region.
 *  - **Selection contains an existing region**: extends to the larger region.
 *  - **Selection is contained in an existing region**: splits into two regions.
 *  - **No overlap**: adds a new partial region.
 *
 * Skips files that are already fully audited (the whole file is already green).
 *
 * Registered in `plugin.xml` with id `WeAudit.AddPartiallyAudited`.
 */
open class MarkRegionAsReviewedAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return
        val store   = WeAuditStore.getInstance(project)

        val relativePath = vFile.relativeToProject(project) ?: return

        // Skip if the whole file is already audited.
        if (store.auditedFiles.any { it.path == relativePath }) {
            Messages.showInfoMessage(
                project,
                "\"${vFile.name}\" is already fully marked as reviewed.\n" +
                "Use \"Mark Current File As Reviewed\" (Cmd+7) to toggle it off first.",
                "weAudit: Mark Region"
            )
            return
        }

        val newRegion = selectionToLocation(editor, vFile, project)
        val existing  = store.partiallyAuditedFiles
            .firstOrNull { it.path == relativePath }

        val updatedRegions = mergeRegion(
            existing?.regions ?: emptyList(),
            newRegion
        )

        val newPartials = store.partiallyAuditedFiles
            .filter { it.path != relativePath }
            .toMutableList()

        if (updatedRegions.isNotEmpty()) {
            newPartials.add(PartiallyAuditedFile(relativePath, updatedRegions))
        }

        mutateAndRefresh(project) { setPartiallyAuditedFiles(newPartials) }
    }

    /**
     * Merge [newRegion] into [existing] regions using the VS Code extension's rules.
     *
     * Returns the updated list of regions for the file.
     */
    protected fun mergeRegion(existing: List<Location>, newRegion: Location): List<Location> {
        val result = mutableListOf<Location>()
        var handled = false

        for (region in existing) {
            when {
                // Exact match → unmark (omit from result)
                region.startLine == newRegion.startLine &&
                region.endLine   == newRegion.endLine -> {
                    handled = true
                    // Don't add — this removes it.
                }

                // New region contains existing → extend to new boundaries
                newRegion.startLine <= region.startLine &&
                newRegion.endLine   >= region.endLine -> {
                    if (!handled) {
                        result.add(region.copy(
                            startLine = newRegion.startLine,
                            endLine   = newRegion.endLine
                        ))
                        handled = true
                    }
                    // Absorb — don't re-add the old region.
                }

                // New region is contained in existing → split into two
                region.startLine <= newRegion.startLine &&
                region.endLine   >= newRegion.endLine &&
                !handled -> {
                    // Top half (only if non-empty)
                    if (region.startLine < newRegion.startLine) {
                        result.add(region.copy(endLine = newRegion.startLine - 1))
                    }
                    // Bottom half (only if non-empty)
                    if (region.endLine > newRegion.endLine) {
                        result.add(region.copy(startLine = newRegion.endLine + 1))
                    }
                    handled = true
                }

                else -> result.add(region)
            }
        }

        // No existing region was affected → add as new
        if (!handled) {
            result.add(newRegion)
        }

        return result.sortedBy { it.startLine }
    }
}

/**
 * "weAudit: Navigate to Next Partially Audited Region"
 *
 * VS Code command: `weaudit.navigateToNextPartiallyAuditedRegion`  (Cmd+0 / Ctrl+0)
 *
 * Cycles through all partially-audited regions across all files in the project,
 * opening each file and jumping the caret to the start of the next region.
 *
 * State (current index) is held in memory and resets when the store changes.
 *
 * Registered in `plugin.xml` with id `WeAudit.NavigateNextPartiallyAuditedRegion`.
 */
class NavigateNextPartialAction : WeAuditAction() {

    // Session-scoped index; resets to 0 when the project reloads.
    private var currentIndex = 0

    override fun update(e: AnActionEvent) {
        val project = e.project()
        e.presentation.isEnabledAndVisible = project != null &&
            WeAuditStore.getInstance(project).partiallyAuditedFiles
                .any { it.regions.isNotEmpty() }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project() ?: return
        val store   = WeAuditStore.getInstance(project)

        // Flatten all regions across all files into a navigable list.
        data class RegionRef(val filePath: String, val region: Location)
        val allRegions = store.partiallyAuditedFiles
            .flatMap { pf -> pf.regions.map { RegionRef(pf.path, it) } }

        if (allRegions.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No partially-reviewed regions found.",
                "weAudit: Navigate Regions"
            )
            return
        }

        currentIndex = currentIndex % allRegions.size
        val ref = allRegions[currentIndex]
        currentIndex = (currentIndex + 1) % allRegions.size

        val basePath = project.basePath ?: return
        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath("$basePath/${ref.filePath}") ?: return

        val descriptor = OpenFileDescriptor(project, vFile, ref.region.startLine, 0)
        WriteIntentReadAction.run<RuntimeException> {
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }
}

// ── Private extension ─────────────────────────────────────────────────────────

/**
 * Convert an absolute [VirtualFile] path to a workspace-relative path.
 * Returns null if the file is outside the project root.
 */
internal fun com.intellij.openapi.vfs.VirtualFile.relativeToProject(
    project: com.intellij.openapi.project.Project
): String? {
    val basePath = project.basePath ?: return null
    return if (path.startsWith(basePath)) {
        path.removePrefix(basePath).trimStart('/')
    } else null
}

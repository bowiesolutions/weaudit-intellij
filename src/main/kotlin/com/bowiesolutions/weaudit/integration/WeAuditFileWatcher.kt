package com.bowiesolutions.weaudit.integration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.bowiesolutions.weaudit.store.WeAuditSerializer
import com.bowiesolutions.weaudit.store.WeAuditStore
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import kotlin.io.path.Path

/**
 * Watches `.vscode/*.weaudit` files for changes and reloads co-auditor data.
 *
 * Port of the VS Code extension's `workspace.onDidChangeWorkspaceFolders` and
 * `fs.watch` logic that kept the multi-user view up to date when teammates
 * committed changes to their `.weaudit` files.
 *
 * ## When it fires
 * - Another auditor's `.weaudit` file changes on disk (git pull, file sync)
 * - A new `.weaudit` file appears in `.vscode/` (new team member)
 *
 * ## What it does NOT do
 * - Reload the **current user's** own `.weaudit` file — that is managed
 *   exclusively by [WeAuditStore] to avoid a write-then-reload race.
 * - Act on deletions — if a co-auditor's file disappears, their data stays
 *   in memory until the next project reload.
 *
 * ## Registration
 * Registered as a project-level message-bus listener in `plugin.xml`:
 * ```xml
 * <projectListeners>
 *   <listener class="com.bowiesolutions.weaudit.integration.WeAuditFileWatcher"
 *             topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
 * </projectListeners>
 * ```
*/*/
class WeAuditFileWatcher(private val project: Project) : BulkFileListener {

    private val log = logger<WeAuditFileWatcher>()

    override fun after(events: List<VFileEvent>) {
        val username = WeAuditSettingsState.getInstance().username
        val weAuditPath = try {
            WeAuditStore.weAuditDir(project).toString()
        } catch (_: IllegalStateException) {
            return   // project has no base path (e.g. default project)
        }

        val relevantEvents = events.filter { event ->
            val path = event.path
            // Only care about .weaudit files in our .vscode/ directory
            path.startsWith(weAuditPath) &&
            path.endsWith(".weaudit") &&
            // Don't reload our own file — the store manages that
            !path.endsWith("$username.weaudit") &&
            (event is VFileContentChangeEvent || event is VFileCreateEvent)
        }

        if (relevantEvents.isEmpty()) return

        // Reload affected co-auditor files on a background thread,
        // then notify the EDT so the tree views refresh.
        ApplicationManager.getApplication().executeOnPooledThread {
            val store = WeAuditStore.getInstance(project)
            for (event in relevantEvents) {
                val filePath = Path(event.path)
                val author   = filePath.fileName.toString().removeSuffix(".weaudit")
                try {
                    val data = WeAuditSerializer.readWeAuditFile(filePath)
                    // Access the internal map via the store's loadAllUsersData
                    // equivalent — reload just the one changed file.
                    store.reloadCoAuditorData(author, data)
                    log.info("weAudit: reloaded co-auditor data for '$author'")
                } catch (e: Exception) {
                    log.warn("weAudit: failed to reload ${filePath.fileName}", e)
                }
            }
        }
    }
}

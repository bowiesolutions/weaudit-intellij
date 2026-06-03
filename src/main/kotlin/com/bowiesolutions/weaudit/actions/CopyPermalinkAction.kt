package com.bowiesolutions.weaudit.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.bowiesolutions.weaudit.integration.GitIntegration
import com.bowiesolutions.weaudit.integration.PermalinkBuilder
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import com.bowiesolutions.weaudit.store.WeAuditStore
import java.awt.datatransfer.StringSelection

/**
 * "weAudit: Copy Permalink to Finding"
 *
 * Copies a permanent GitHub/GitLab link to the finding under the cursor
 * to the system clipboard.
 *
 * ## Permalink format
 * `https://github.com/{org}/{repo}/blob/{sha}/{path}#L{start}-L{end}`
 *
 * Uses the HEAD commit SHA (not the branch name) so the link stays valid
 * after force-pushes or rebases — matching the VS Code extension behaviour.
 *
 * ## Fallback chain
 * 1. Remote URL from [WeAuditSettingsState.gitRemote] (user-configured)
 * 2. Remote URL auto-detected from git config via [GitIntegration.remoteUrl]
 * 3. Error notification if neither is available
 *
 * ## Multi-region findings
 * For findings with multiple locations, all links are copied joined by newlines.
 *
 * Registered in `plugin.xml` with id `WeAudit.CopyPermalink`.
 * No default shortcut (matches VS Code where it was palette-only).
 */
class CopyPermalinkAction : WeAuditAction() {

    override fun update(e: AnActionEvent) = requiresEditor(e)

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.editor()  ?: return
        val project = e.project() ?: return
        val vFile   = e.vFile()   ?: return
        val store   = WeAuditStore.getInstance(project)

        // Resolve remote URL: user setting takes priority, then git auto-detect
        val settings = WeAuditSettingsState.getInstance()
        val remote   = settings.gitRemote.ifBlank {
            GitIntegration.remoteUrl(project)
        } ?: ""

        val sha = GitIntegration.headCommitSha(project) ?: ""

        if (remote.isBlank() || sha.isBlank()) {
            notify(project,
                "Cannot build permalink: " +
                if (remote.isBlank()) "no remote URL configured (set it in Settings → Tools → weAudit or Git Config tab)."
                else "no git repository found.",
                NotificationType.WARNING)
            return
        }

        // Try to find a finding under the cursor first; fall back to bare selection
        val index   = entryIndexUnderCursor(editor, vFile, project)
        val text    = if (index >= 0) {
            val entry = store.entries[index]
            PermalinkBuilder.buildAllForEntry(entry, remote, sha)
                .joinToString("\n")
                .ifBlank { null }
        } else {
            // No finding under cursor — permalink to the current selection/caret
            val loc = selectionToLocation(editor, vFile, project)
            PermalinkBuilder.buildForLocation(loc, remote, sha)
        }

        if (text.isNullOrBlank()) {
            notify(project,
                "Could not build permalink. Check the remote URL in Settings → Tools → weAudit.",
                NotificationType.WARNING)
            return
        }

        CopyPasteManager.getInstance().setContents(StringSelection(text))
        notify(project, "Permalink copied to clipboard.", NotificationType.INFORMATION)
    }

    private fun notify(project: com.intellij.openapi.project.Project, msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("weAudit")
            ?.createNotification(msg, type)
            ?.notify(project)
    }
}

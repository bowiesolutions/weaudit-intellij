package com.bowiesolutions.weaudit.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.bowiesolutions.weaudit.integration.GitIntegration
import com.bowiesolutions.weaudit.integration.MarkdownExporter
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import com.bowiesolutions.weaudit.store.WeAuditStore
import java.io.IOException

/**
 * "weAudit: Export Findings to Markdown"
 *
 * Presents a save-file dialog, then writes a Markdown report of all active
 * (non-resolved) findings and notes to the chosen file.
 *
 * If a git remote and HEAD SHA are available, each location is rendered as
 * a hyperlink to the exact line on GitHub/GitLab.
 *
 * Registered in `plugin.xml` with id `WeAudit.ExportMarkdown`.
 */
class ExportMarkdownAction : WeAuditAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project()
        e.presentation.isEnabledAndVisible = project != null &&
            WeAuditStore.getInstance(project).entries.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project() ?: return
        val store   = WeAuditStore.getInstance(project)

        val settings = WeAuditSettingsState.getInstance()
        val remote   = settings.gitRemote.ifBlank { GitIntegration.remoteUrl(project) } ?: ""
        val sha      = GitIntegration.headCommitSha(project) ?: ""

        val markdown = MarkdownExporter.export(
            entries = store.entries,
            remote  = remote,
            sha     = sha,
        )

        saveToFile(project, markdown)
    }

    private fun saveToFile(project: Project, content: String) {
        val descriptor = FileSaverDescriptor(
            "Export weAudit Findings",
            "Save findings as a Markdown file",
            "md"
        )
        val dialog = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)

        val baseVf = project.basePath?.let {
            LocalFileSystem.getInstance().findFileByPath(it)
        }

        val wrapper = dialog.save(baseVf, "findings.md") ?: return

        try {
            val file = wrapper.getVirtualFile(/* createIfMissing = */ true)
                ?: throw IOException("Could not create file: ${wrapper.file}")

            ApplicationManager.getApplication().runWriteAction {
                file.setBinaryContent(content.toByteArray(Charsets.UTF_8))
            }

            NotificationGroupManager.getInstance()
                .getNotificationGroup("weAudit")
                ?.createNotification(
                    "Findings exported to ${wrapper.file.name}",
                    NotificationType.INFORMATION
                )?.notify(project)

        } catch (ex: IOException) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("weAudit")
                ?.createNotification(
                    "Export failed: ${ex.message}",
                    NotificationType.ERROR
                )?.notify(project)
        }
    }
}

package com.bowiesolutions.weaudit.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.bowiesolutions.weaudit.panels.FindingDetailsPanel
import com.bowiesolutions.weaudit.panels.GitConfigPanel
import com.bowiesolutions.weaudit.toolwindow.panel.AuditedFilesPanel
import com.bowiesolutions.weaudit.toolwindow.panel.DailyLogPanel
import com.bowiesolutions.weaudit.toolwindow.panel.FindingsPanel
import com.bowiesolutions.weaudit.toolwindow.panel.ResolvedFindingsPanel

/**
 * Creates and populates the weAudit tool window with five tabbed panels.
 *
 * ## Tabs (matching VS Code's five contributed views)
 * | Tab label       | Panel class             | VS Code view        |
 * |-----------------|-------------------------|---------------------|
 * | Findings        | [FindingsPanel]         | `codeMarker`        |
 * | Resolved        | [ResolvedFindingsPanel] | `resolvedFindings`  |
 * | Files           | [AuditedFilesPanel]     | `savedFindings`     |
 * | Finding Details | [FindingDetailsPanel]   | `findingDetails`    |
 * | Git Config      | [GitConfigPanel]        | `gitConfig`         |
 *
 * ## Selection bridge (Phase 4 addition)
 * When the user selects a finding in the Findings tree, [FindingsPanel] calls
 * [FindingDetailsPanel.showEntry] and [GitConfigPanel.showEntry] so both panels
 * reflect the selection immediately — matching the VS Code behaviour where
 * clicking a finding auto-populated the findingDetails webview.
 *
 * [FindingsPanel] receives the two detail panels via [FindingsPanel.onEntrySelected]
 * callback, keeping the panel classes decoupled from each other.
 *
 * ## Lifecycle
 * Panels register store change listeners in [attach] / [detach].
 * A [ContentManagerListener] detaches listeners on tab removal.
 */
class WeAuditToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val cf = ContentFactory.getInstance()

        // ── Create panels ─────────────────────────────────────────────────────
        val findingDetailsPanel = FindingDetailsPanel(project)
        val gitConfigPanel      = GitConfigPanel(project)

        val findingsPanel  = FindingsPanel(project).also { fp ->
            // Wire selection → detail panels
            fp.onEntrySelected = { entry, storeIndex ->
                findingDetailsPanel.showEntry(entry, storeIndex)
                gitConfigPanel.showEntry(entry)
            }
            fp.onEntryDeselected = {
                findingDetailsPanel.clearEntry()
                gitConfigPanel.clearEntry()
            }
        }
        val resolvedPanel  = ResolvedFindingsPanel(project)
        val filesPanel     = AuditedFilesPanel(project)
        val dailyLogPanel = DailyLogPanel(project)

        // ── Create contents ───────────────────────────────────────────────────
        val findingsContent       = cf.createContent(findingsPanel,       "Findings",        false)
        val resolvedContent       = cf.createContent(resolvedPanel,       "Resolved",        false)
        val filesContent          = cf.createContent(filesPanel,          "Files",           false)
        val findingDetailsContent = cf.createContent(findingDetailsPanel, "Finding Details", false)
        val gitConfigContent      = cf.createContent(gitConfigPanel,      "Git Config",      false)
        val dailyLogContent = cf.createContent(dailyLogPanel, "Daily Log", false)

        toolWindow.contentManager.apply {
            addContent(findingsContent)
            addContent(resolvedContent)
            addContent(filesContent)
            addContent(findingDetailsContent)
            addContent(gitConfigContent)
            addContent(dailyLogContent)
        }

        // ── Attach store listeners ────────────────────────────────────────────
        findingsPanel.attach()
        resolvedPanel.attach()
        filesPanel.attach()
        dailyLogPanel.attach()

        // ── Detach on remove ─────────────────────────────────────────────────
        toolWindow.contentManager.addContentManagerListener(
            object : ContentManagerListener {
                override fun contentRemoved(event: ContentManagerEvent) {
                    when (event.content) {
                        findingsContent -> findingsPanel.detach()
                        resolvedContent -> resolvedPanel.detach()
                        filesContent    -> filesPanel.detach()
                        dailyLogContent -> dailyLogPanel.detach()
                    }
                }
            }
        )
    }
}

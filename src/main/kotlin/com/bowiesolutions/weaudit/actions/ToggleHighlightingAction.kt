package com.bowiesolutions.weaudit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState

/**
 * "weAudit: Toggle Findings Highlighting"
 *
 * VS Code command: `weaudit.toggleHighlight` (no default shortcut in VS Code;
 * available via Command Palette).
 *
 * Toggles global highlight visibility.  When disabled, all weAudit highlighters
 * are removed from the markup models.  When re-enabled, [reapplyAll] rebuilds
 * them from the current store state.
 *
 * The enabled/disabled state is persisted in [WeAuditSettingsState.highlightingEnabled]
 * so it survives project close/reopen.
 *
 * Registered in `plugin.xml` with id `WeAudit.ToggleHighlighting`.
 */
class ToggleHighlightingAction : WeAuditAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project()
        e.presentation.isEnabledAndVisible = project != null
        // Reflect current state in the action text so the menu item reads as a toggle.
        val enabled = WeAuditSettingsState.getInstance().highlightingEnabled
        e.presentation.text = if (enabled) "Hide Findings Highlighting" else "Show Findings Highlighting"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project  = e.project() ?: return
        val settings = WeAuditSettingsState.getInstance()
        val manager  = EditorHighlightManager.getInstance(project)

        settings.highlightingEnabled = !settings.highlightingEnabled

        if (settings.highlightingEnabled) {
            manager.reapplyAll()
        } else {
            manager.clearAll()
        }
    }
}

package com.bowiesolutions.weaudit.editor

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.project.Project
import javax.swing.UIManager

/**
 * Recomputes weAudit highlights when the IDE look-and-feel (theme) changes.
 *
 * Port of the VS Code extension's `onDidChangeActiveColorTheme` handler in
 * `codeMarker.ts`, which called `updateDecorations()` to rebuild decoration
 * types using the new theme's background colors.
 *
 * In IntelliJ, colors stored as plain hex strings in [WeAuditSettingsState]
 * don't need to change — but if the user uses the default colors, the same
 * light-red background that looks fine on a light theme can become invisible
 * on a dark theme. A full [reapplyAll] after a LAF change ensures
 * [TextAttributes] are rebuilt with the correct resolved colors.
 *
 * Registration in `plugin.xml`:
 * ```xml
 * <projectListeners>
 *   <listener class="com.bowiesolutions.weaudit.editor.ThemeChangeListener"
 *             topic="com.intellij.ide.ui.LafManagerListener"/>
 * </projectListeners>
 * ```
 */
class ThemeChangeListener(private val project: Project) : LafManagerListener {

    override fun lookAndFeelChanged(source: com.intellij.ide.ui.LafManager) {
        EditorHighlightManager.getInstance(project).reapplyAll()
    }
}

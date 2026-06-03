package com.bowiesolutions.weaudit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page under **Settings → Tools → weAudit**.
 *
 * This is the IntelliJ equivalent of VS Code's `contributes.configuration` block
 * in `package.json`, which declared user-configurable settings for colors, username,
 * and GitHub org.
 *
 * The configurable is registered in `plugin.xml`:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <applicationConfigurable
 *       parentId="tools"
 *       instance="com.bowiesolutions.weaudit.settings.WeAuditSettingsConfigurable"
 *       id="weaudit.settings"
 *       displayName="weAudit"/>
 * </extensions>
 * ```
 *
 * Phase 2+: after the user clicks Apply, the editor layer must be notified
 * to recompute highlight [com.intellij.openapi.editor.markup.TextAttributes]
 * from the new color strings.  Wire this through a message-bus topic or
 * the [WeAuditStore] change-listener mechanism.
 */
class WeAuditSettingsConfigurable : Configurable {

    // ── UI components ─────────────────────────────────────────────────────────

    private val usernameField      = JBTextField()
    private val findingColorPanel  = ColorPanel()
    private val noteColorPanel     = ColorPanel()
    private val auditedColorPanel  = ColorPanel()
    private val otherFindingPanel  = ColorPanel()
    private val otherNotePanel     = ColorPanel()
    private val gitOrgField        = JBTextField()
    private val gitRemoteField     = JBTextField()

    private var panel: JPanel? = null

    // ── Configurable ─────────────────────────────────────────────────────────

    override fun getDisplayName(): String = "weAudit"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            // Add Identity Separator
            .addSeparator(10)
            .addLabeledComponent(JBLabel("Username:"), usernameField, 1, false)
            .addTooltip("Used as the .weaudit filename and author field on new entries.")
            // Add Colors Separator
            .addSeparator(10)
            .addLabeledComponent(JBLabel("Finding highlight:"),    findingColorPanel,  1, false)
            .addLabeledComponent(JBLabel("Note highlight:"),       noteColorPanel,     1, false)
            .addLabeledComponent(JBLabel("Audited file:"),         auditedColorPanel,  1, false)
            .addLabeledComponent(JBLabel("Other user's finding:"), otherFindingPanel,  1, false)
            .addLabeledComponent(JBLabel("Other user's note:"),    otherNotePanel,     1, false)

            // Add GitHub / GitLab Integration Separator
            .addSeparator(10)
            .addLabeledComponent(JBLabel("Organisation:"), gitOrgField,    1, false)
            .addLabeledComponent(JBLabel("Remote URL:"),   gitRemoteField, 1, false)
            .addTooltip("e.g. https://github.com/bowiesolutions/vscode-weaudit")

            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = WeAuditSettingsState.getInstance()
        return usernameField.text        != s.username          ||
               findingColorPanel.toHex() != s.findingColor       ||
               noteColorPanel.toHex()    != s.noteColor          ||
               auditedColorPanel.toHex() != s.auditedFileColor   ||
               otherFindingPanel.toHex() != s.otherFindingColor  ||
               otherNotePanel.toHex()    != s.otherNoteColor     ||
               gitOrgField.text          != s.gitOrg             ||
               gitRemoteField.text       != s.gitRemote
    }

    override fun apply() {
        val s = WeAuditSettingsState.getInstance()
        s.username         = usernameField.text.trim()
        s.findingColor     = findingColorPanel.toHex()
        s.noteColor        = noteColorPanel.toHex()
        s.auditedFileColor = auditedColorPanel.toHex()
        s.otherFindingColor= otherFindingPanel.toHex()
        s.otherNoteColor   = otherNotePanel.toHex()
        s.gitOrg           = gitOrgField.text.trim()
        s.gitRemote        = gitRemoteField.text.trim()
        // Phase 2: publish a message-bus event here so the editor layer
        // recomputes TextAttributes from the new colors.
    }

    override fun reset() {
        val s = WeAuditSettingsState.getInstance()
        usernameField.text   = s.username
        findingColorPanel.setSelectedColor(hexToColor(s.findingColor))
        noteColorPanel.setSelectedColor(hexToColor(s.noteColor))
        auditedColorPanel.setSelectedColor(hexToColor(s.auditedFileColor))
        otherFindingPanel.setSelectedColor(hexToColor(s.otherFindingColor))
        otherNotePanel.setSelectedColor(hexToColor(s.otherNoteColor))
        gitOrgField.text     = s.gitOrg
        gitRemoteField.text  = s.gitRemote
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ColorPanel.toHex(): String {
        val c = selectedColor ?: return "#000000"
        return "#%02X%02X%02X".format(c.red, c.green, c.blue)
    }

    private fun hexToColor(hex: String): Color = try {
        Color.decode(hex)
    } catch (_: NumberFormatException) {
        Color.LIGHT_GRAY
    }
}

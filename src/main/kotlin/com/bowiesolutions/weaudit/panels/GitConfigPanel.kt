package com.bowiesolutions.weaudit.panels

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.FindingDetails
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The "Git Config" panel — port of the VS Code `gitConfig` webview panel.
 *
 * Provides:
 * 1. **Organisation** and **Remote URL** fields, pre-filled from
 *    [WeAuditSettingsState] and saved back on change.
 * 2. An **Open Remote Issue** button that constructs a pre-filled GitHub/GitLab
 *    issue URL from the currently-selected finding's [FindingDetails] and opens
 *    it in the system browser via [BrowserUtil].
 *
 * ## Issue URL construction
 * Mirrors the logic in `codeMarker.ts` `openGithubIssue()`:
 * - Base:  `https://github.com/{org}/{repo}/issues/new`
 * - Title: `?title={finding.label}`
 * - Body:  `&body={formatted markdown body}`
 *
 * The body template matches the VS Code extension's format exactly so that
 * issues created from either IDE look identical.
 *
 * ## Selection wiring
 * [showEntry] is called by [FindingsPanel] when the user selects a finding,
 * enabling the "Open Remote Issue" button and storing the entry reference.
 */
class GitConfigPanel(private val project: Project) : JPanel(BorderLayout()) {

    // ── Fields ────────────────────────────────────────────────────────────────

    private val orgField    = JBTextField()
    private val remoteField = JBTextField()
    private val openIssueButton = JButton("Open Remote Issue")
    private val statusLabel = JBLabel("", SwingConstants.CENTER).apply {
        font = font.deriveFont(Font.ITALIC)
        foreground = java.awt.Color.GRAY
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private var currentEntry: Entry? = null

    init {
        buildForm()
        loadFromSettings()
        updateButtonState()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Notify the panel that [entry] is selected in the findings tree.
     * Enables the Open Remote Issue button when the entry is a Finding
     * with enough detail to create a meaningful issue.
     */
    fun showEntry(entry: Entry) {
        currentEntry = entry
        updateButtonState()
    }

    fun clearEntry() {
        currentEntry = null
        updateButtonState()
    }

    // ── Form construction ─────────────────────────────────────────────────────

    private fun buildForm() {
        val formPanel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val gbc = GridBagConstraints().apply {
            fill   = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        // Organisation
        gbc.apply { gridx = 0; gridy = 0; weightx = 0.0 }
        formPanel.add(JBLabel("Organisation:").apply { font = font.deriveFont(Font.BOLD) }, gbc)
        gbc.apply { gridx = 1; weightx = 1.0 }
        formPanel.add(orgField, gbc)

        // Remote URL
        gbc.apply { gridx = 0; gridy = 1; weightx = 0.0 }
        formPanel.add(JBLabel("Remote URL:").apply { font = font.deriveFont(Font.BOLD) }, gbc)
        gbc.apply { gridx = 1; weightx = 1.0 }
        formPanel.add(remoteField, gbc)

        // Helper text
        gbc.apply { gridx = 0; gridy = 2; gridwidth = 2; weightx = 1.0 }
        formPanel.add(JBLabel(
            "<html><i>e.g. https://github.com/bowiesolutions/vscode-weaudit</i></html>"
        ), gbc)

        // Open Remote Issue button
        gbc.apply { gridx = 0; gridy = 3; gridwidth = 2; fill = GridBagConstraints.NONE;
            anchor = GridBagConstraints.WEST }
        formPanel.add(openIssueButton, gbc)

        // Status label
        gbc.apply { gridy = 4; fill = GridBagConstraints.HORIZONTAL }
        formPanel.add(statusLabel, gbc)

        // Save settings on change
        orgField.addActionListener    { saveToSettings() }
        remoteField.addActionListener { saveToSettings() }
        orgField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) { saveToSettings() }
        })
        remoteField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) { saveToSettings() }
        })

        openIssueButton.addActionListener { openRemoteIssue() }

        add(formPanel, BorderLayout.NORTH)
    }

    // ── Settings I/O ─────────────────────────────────────────────────────────

    private fun loadFromSettings() {
        val s = WeAuditSettingsState.getInstance()
        orgField.text    = s.gitOrg
        remoteField.text = s.gitRemote
    }

    private fun saveToSettings() {
        val s = WeAuditSettingsState.getInstance()
        s.gitOrg    = orgField.text.trim()
        s.gitRemote = remoteField.text.trim()
    }

    // ── Issue URL construction ────────────────────────────────────────────────

    private fun openRemoteIssue() {
        saveToSettings()

        val entry  = currentEntry ?: return
        val remote = remoteField.text.trim()

        if (remote.isBlank()) {
            Messages.showWarningDialog(
                project,
                "Please enter the repository Remote URL before opening an issue.",
                "weAudit: Open Remote Issue"
            )
            return
        }

        val url = buildIssueUrl(remote, entry)
        if (url == null) {
            Messages.showWarningDialog(
                project,
                "Could not construct an issue URL from the remote: $remote\n" +
                "Expected a GitHub or GitLab HTTPS URL.",
                "weAudit: Open Remote Issue"
            )
            return
        }

        BrowserUtil.browse(url)
        statusLabel.text = "Issue opened in browser."
    }

    /**
     * Build a pre-filled GitHub or GitLab new-issue URL from [entry].
     *
     * Mirrors the VS Code `openGithubIssue` function in `codeMarker.ts`.
     * Returns null if the remote URL is not a recognized GitHub/GitLab HTTPS URL.
     *
     * The issue body uses the same Markdown template as the VS Code extension
     * so that issues opened from IntelliJ are identical to those from VS Code.
     */
    internal fun buildIssueUrl(remote: String, entry: Entry): String? {
        // Normalize: strip trailing .git and slash
        val base = remote.trimEnd('/').removeSuffix(".git")

        val isGitHub = base.contains("github.com")
        val isGitLab = base.contains("gitlab.com") || base.contains("gitlab.")

        if (!isGitHub && !isGitLab) return null

        val details = entry.details ?: FindingDetails.EMPTY

        val title = encode(
            details.title.ifBlank { entry.label }
        )

        val body = encode(buildIssueBody(entry, details))

        return if (isGitHub) {
            "$base/issues/new?title=$title&body=$body"
        } else {
            // GitLab uses the same query-param format
            "$base/issues/new?issue[title]=$title&issue[description]=$body"
        }
    }

    /**
     * Build the Markdown issue body, matching the VS Code template:
     *
     * ```
     * ## Description
     * {description}
     *
     * ## Exploit Scenario
     * {exploit}
     *
     * ## Recommendation
     * {recommendation}
     *
     * ---
     * *Finding by {author} — {path}:{startLine}*
     * ```
     */
    internal fun buildIssueBody(entry: Entry, details: FindingDetails): String = buildString {
        val loc = entry.primaryLocation

        if (details.severity.isNotBlank() || details.difficulty.isNotBlank()) {
            append("**Severity:** ${details.severity.ifBlank { "—" }}  ")
            append("**Difficulty:** ${details.difficulty.ifBlank { "—" }}\n\n")
        }

        if (details.description.isNotBlank()) {
            append("## Description\n\n${details.description}\n\n")
        }

        if (details.exploit.isNotBlank()) {
            append("## Exploit Scenario\n\n${details.exploit}\n\n")
        }

        if (details.recommendation.isNotBlank()) {
            append("## Recommendation\n\n${details.recommendation}\n\n")
        }

        append("---\n")
        append("*Finding by ${entry.author} — `${loc.path}` lines ")
        append("${loc.startLine + 1}–${loc.endLine + 1}*\n")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateButtonState() {
        val entry = currentEntry
        openIssueButton.isEnabled = entry != null &&
            entry.entryTypeEnum == EntryType.Finding
        if (entry == null) statusLabel.text = ""
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")
}

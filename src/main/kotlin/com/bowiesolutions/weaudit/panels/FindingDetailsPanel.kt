package com.bowiesolutions.weaudit.panels

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.FindingDetails
import com.bowiesolutions.weaudit.store.WeAuditStore
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * The "Finding Details" panel — port of the VS Code `findingDetails` webview panel.
 *
 * Displays and edits the [FindingDetails] fields for the currently-selected finding:
 * title, severity, difficulty, type, description, exploit scenario, and recommendation.
 *
 * ## VS Code equivalent
 * The VS Code extension used an HTML webview communicating over `postMessage` /
 * `onDidReceiveMessage`.  This is a native Swing form using [GridBagLayout] —
 * simpler, more idiomatic, and avoids the JCEF dependency.
 *
 * ## Save strategy
 * Fields auto-save on focus-lost (matching the webview's `onblur` handler pattern).
 * There is also an explicit Save button for keyboard users who prefer Ctrl+S semantics.
 * Changes are written back to [WeAuditStore] via [WeAuditStore.updateEntry], which
 * persists to disk asynchronously and fires the change listener.
 *
 * ## Selection wiring
 * [showEntry] is called by [FindingsPanel] (Phase 3) when the user clicks a finding
 * in the tree.  The panel is shown/hidden by [WeAuditToolWindowFactory] tab management.
 *
 * ## Empty state
 * When no finding is selected (or a Note is selected, which has no details),
 * the form shows a placeholder message and all fields are disabled.
 */
class FindingDetailsPanel(private val project: Project) : JPanel(BorderLayout()) {

    // ── Card layout ───────────────────────────────────────────────────────────

    private val cardLayout  = CardLayout()
    private val cardPanel   = JPanel(cardLayout)

    // ── Fields ────────────────────────────────────────────────────────────────

    private val titleField       = JBTextField()
    private val severityCombo    = ComboBox(SEVERITY_OPTIONS)
    private val difficultyCombo  = ComboBox(DIFFICULTY_OPTIONS)
    private val typeCombo        = ComboBox(TYPE_OPTIONS)
    private val descriptionArea  = makeTextArea(6)
    private val exploitArea      = makeTextArea(6)
    private val recommendArea    = makeTextArea(6)
    private val saveButton       = JButton("Save", AllIcons.Actions.MenuSaveall)
//    private val placeholderLabel = JBLabel(
//        "<html><center><br/>Select a finding in the<br/>" +
//        "List of Findings to view<br/>and edit its details.</center></html>",
//        SwingConstants.CENTER
//    )

    // ── State ─────────────────────────────────────────────────────────────────

    /** The entry currently being shown, null when nothing is selected. */
    private var currentEntry: Entry? = null
    /** Index of [currentEntry] in [WeAuditStore.entries]. */
    private var currentIndex: Int = -1
    /** Suppress change listeners while we are programmatically populating fields. */
    private var loading = false

    init {
        // Build form panel
        val formPanel = buildFormPanel()
        val formScroll = JBScrollPane(formPanel).apply {
            border = null
        }

        // Build placeholder panel
        val placeholderPanel = JPanel(BorderLayout()).apply {
            add(JBLabel(
                "<html><center><br/>Select a finding in the<br/>" +
                "List of Findings to view<br/>and edit its details.</center></html>",
                SwingConstants.CENTER
            ), BorderLayout.CENTER)
        }

        // Register cards
        cardPanel.add(placeholderPanel, CARD_PLACEHOLDER)
        cardPanel.add(formScroll,       CARD_FORM)

        add(cardPanel, BorderLayout.CENTER)

        // Start on placeholder
        cardLayout.show(cardPanel, CARD_PLACEHOLDER)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Populate the form with the details of [entry] at [storeIndex].
     * Called by [FindingsPanel] tree selection listener.
     * Must be called on the EDT.
     */
    fun showEntry(entry: Entry, storeIndex: Int) {
        currentEntry = entry
        currentIndex = storeIndex

        if (entry.entryTypeEnum == EntryType.Note) {
            cardLayout.show(cardPanel, CARD_PLACEHOLDER)
            return
        }

 //       showForm()
        populateFields(entry.details ?: FindingDetails.EMPTY)
        cardLayout.show(cardPanel, CARD_FORM)
    }

    /** Clear the form back to the placeholder state (e.g. after delete). */
    fun clearEntry() {
        currentEntry = null
        currentIndex = -1
        cardLayout.show(cardPanel, CARD_PLACEHOLDER)
    }

    // ── Form construction ─────────────────────────────────────────────────────

    private fun buildFormPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val gbc = GridBagConstraints().apply {
            fill    = GridBagConstraints.HORIZONTAL
            insets  = Insets(4, 4, 4, 4)
            weightx = 1.0
        }

        var row = 0

        // Title
        row = addFormRow(panel, gbc, row, "Title", titleField, fullWidth = true)

        // Severity + Difficulty side by side
        row = addSideBySide(panel, gbc, row,
            "Severity", severityCombo,
            "Difficulty", difficultyCombo)

        // Type
        row = addFormRow(panel, gbc, row, "Type", typeCombo, fullWidth = true)

        // Description
        row = addFormRow(panel, gbc, row, "Description",
            JBScrollPane(descriptionArea), fullWidth = true, tall = true)

        // Exploit scenario
        row = addFormRow(panel, gbc, row, "Exploit Scenario",
            JBScrollPane(exploitArea), fullWidth = true, tall = true)

        // Recommendation
        row = addFormRow(panel, gbc, row, "Recommendation",
            JBScrollPane(recommendArea), fullWidth = true, tall = true)

        // Save button
        gbc.apply {
            gridx = 0; gridy = row; gridwidth = 2
            fill  = GridBagConstraints.NONE
            anchor = GridBagConstraints.EAST
        }
        panel.add(saveButton, gbc)

        // Filler to push content up
        gbc.apply {
            gridx = 0; gridy = row + 1; gridwidth = 2
            fill    = GridBagConstraints.VERTICAL
            weighty = 1.0
        }
        panel.add(JPanel(), gbc)

        // Wire save
        saveButton.addActionListener { saveCurrentEntry() }

        // Auto-save on focus-lost for all fields
        listOf(titleField, descriptionArea, exploitArea, recommendArea).forEach { c ->
            c.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) { if (!loading) saveCurrentEntry() }
            })
        }
        listOf(severityCombo, difficultyCombo, typeCombo).forEach { c ->
            c.addActionListener { if (!loading) saveCurrentEntry() }
        }

	return panel
	
        // Main scroll wrapper
//        val scrollPane = JBScrollPane(formPanel).apply {
//            border = BorderFactory.createEmptyBorder()
//        }

//        add(scrollPane,       BorderLayout.CENTER)
//        add(placeholderLabel, BorderLayout.CENTER)   // swapped in showPlaceholder()
//    }

    // ── Show / hide helpers ───────────────────────────────────────────────────

//    private fun showForm() {
//        remove(placeholderLabel)
//        components.filterIsInstance<JBScrollPane>().firstOrNull()?.isVisible = true
//        setFieldsEnabled(true)
//        revalidate(); repaint()
//    }

//    private fun showPlaceholder(msg: String? = null) {
//        if (msg != null) placeholderLabel.text =
//            "<html><center><br/>$msg</center></html>"
//        else placeholderLabel.text =
//            "<html><center><br/>Select a finding in the<br/>" +
//            "List of Findings to view<br/>and edit its details.</center></html>"
//        setFieldsEnabled(false)
//        revalidate(); repaint()
//    }

//    private fun setFieldsEnabled(enabled: Boolean) {
//        listOf(titleField, severityCombo, difficultyCombo, typeCombo,
//            descriptionArea, exploitArea, recommendArea, saveButton
//        ).forEach { it.isEnabled = enabled }
    }

    // ── Populate / save ───────────────────────────────────────────────────────

    private fun populateFields(d: FindingDetails) {
        loading = true
        try {
            titleField.text      = d.title
            severityCombo.selectedItem  = d.severity.ifBlank { SEVERITY_OPTIONS[0] }
            difficultyCombo.selectedItem = d.difficulty.ifBlank { DIFFICULTY_OPTIONS[0] }
            typeCombo.selectedItem       = d.type.ifBlank { TYPE_OPTIONS[0] }
            descriptionArea.text = d.description
            exploitArea.text     = d.exploit
            recommendArea.text   = d.recommendation
            // Reset caret to top after programmatic fill
            listOf(descriptionArea, exploitArea, recommendArea)
                .forEach { it.caretPosition = 0 }
        } finally {
            loading = false
        }
    }

    private fun saveCurrentEntry() {
        val entry = currentEntry ?: return
        val index = currentIndex.takeIf { it >= 0 } ?: return
        if (entry.entryTypeEnum == EntryType.Note) return

        val store = WeAuditStore.getInstance(project)
        if (index >= store.entries.size) return

        val updated = entry.copy(
            details = FindingDetails(
                title          = titleField.text.trim(),
                severity       = severityCombo.selectedItem  as? String ?: "",
                difficulty     = difficultyCombo.selectedItem as? String ?: "",
                type           = typeCombo.selectedItem       as? String ?: "",
                description    = descriptionArea.text,
                exploit        = exploitArea.text,
                recommendation = recommendArea.text,
            )
        )
        currentEntry = updated
        SwingUtilities.invokeLater { store.updateEntry(index, updated) }
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private fun addFormRow(
        panel: JPanel, gbc: GridBagConstraints, row: Int,
        labelText: String, field: java.awt.Component,
        fullWidth: Boolean = false, tall: Boolean = false,
    ): Int {
        gbc.apply {
            gridx = 0; gridy = row; gridwidth = 1
            fill  = GridBagConstraints.NONE
            anchor = GridBagConstraints.NORTHWEST
            weightx = 0.0; weighty = 0.0
        }
        panel.add(makeLabel(labelText), gbc)

        gbc.apply {
            gridx     = if (fullWidth) 0 else 1
            gridy     = if (fullWidth) row + 1 else row
            gridwidth = if (fullWidth) 2 else 1
            fill      = GridBagConstraints.BOTH
            anchor    = GridBagConstraints.NORTHWEST
            weightx   = 1.0
            weighty   = if (tall) 1.0 else 0.0
        }
        panel.add(field, gbc)
        return if (fullWidth) row + 2 else row + 1
    }

    private fun addSideBySide(
        panel: JPanel, gbc: GridBagConstraints, row: Int,
        label1: String, field1: java.awt.Component,
        label2: String, field2: java.awt.Component,
    ): Int {
        val sub = JPanel(GridBagLayout())
        val sg  = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL; insets = Insets(0, 0, 0, 8)
        }
        sg.apply { gridx = 0; weightx = 0.0 }; sub.add(makeLabel(label1), sg)
        sg.apply { gridx = 1; weightx = 1.0 }; sub.add(field1, sg)
        sg.apply { gridx = 2; weightx = 0.0; insets = Insets(0, 8, 0, 0) }; sub.add(makeLabel(label2), sg)
        sg.apply { gridx = 3; weightx = 1.0 }; sub.add(field2, sg)

        gbc.apply {
            gridx = 0; gridy = row; gridwidth = 2
            fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; weighty = 0.0
        }
        panel.add(sub, gbc)
        return row + 1
    }

    private fun makeLabel(text: String) =
        JBLabel(text).apply { font = font.deriveFont(Font.BOLD) }

    private fun makeTextArea(rows: Int) = JBTextArea(rows, 40).apply {
        lineWrap      = true
        wrapStyleWord = true
        font          = font.deriveFont(14f)
        minimumSize   = Dimension(0, rows * 20)
    }

    companion object {
        private const val CARD_PLACEHOLDER = "placeholder"
        private const val CARD_FORM        = "form"

        val SEVERITY_OPTIONS   = arrayOf("", "Critical", "High", "Medium", "Low", "Informational", "Undetermined")
        val DIFFICULTY_OPTIONS = arrayOf("", "High", "Medium", "Low", "Undetermined")
        val TYPE_OPTIONS       = arrayOf(
            "", "Access Control", "Auditing and Logging", "Authentication",
            "Configuration", "Cryptography", "Data Exposure", "Data Validation",
            "Denial of Service", "Error Reporting", "Patching", "Timing",
            "Undefined Behavior",
        )
    }
}

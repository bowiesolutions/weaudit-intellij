package com.bowiesolutions.weaudit.toolwindow.panel

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.bowiesolutions.weaudit.store.WeAuditStore
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

/**
 * The "Daily Log" tool-window panel.
 *
 * Port of the VS Code extension's daily-log view, which showed a table of
 * files reviewed per day plus cumulative LOC counts.
 *
 * ## Layout
 * ```
 * ┌─────────────────────────────────────────┐
 * │ Daily Log                               │  ← title
 * │ ┌──────────────┬──────────┬──────────┐  │
 * │ │ Date         │ Files    │ LOC      │  │
 * │ ├──────────────┼──────────┼──────────┤  │
 * │ │ 2024-03-19   │ 4        │ 312      │  │
 * │ │ 2024-03-20   │ 2        │ 148      │  │
 * │ └──────────────┴──────────┴──────────┘  │
 * └─────────────────────────────────────────┘
 * ```
 *
 * Entries are shown newest-first. The panel registers a store change listener
 * so it refreshes when new files are marked as audited.
 */
class DailyLogPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val store      = WeAuditStore.getInstance(project)
    private val tableModel = DefaultTableModel(COLUMNS, 0).apply {
        // Make all cells non-editable
    }
    private val table = JBTable(tableModel).apply {
        setShowGrid(true)
        autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        rowHeight = 24
    }

    private val changeListener: () -> Unit = { refreshTable() }

    init {
        val title = JBLabel("Daily Audit Log", SwingConstants.CENTER).apply {
            font  = font.deriveFont(Font.BOLD, 13f)
            border = JBUI.Borders.empty(6, 0)
        }

        // Make table columns non-editable
        tableModel.setColumnCount(3)

        add(title,              BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)

        refreshTable()
    }

    fun attach()  { store.addChangeListener(changeListener) }
    fun detach()  { store.removeChangeListener(changeListener) }

    private fun refreshTable() {
        tableModel.rowCount = 0   // clear

        // Show newest first
        store.dayLog.entries
            .sortedByDescending { it.date }
            .forEach { entry ->
                tableModel.addRow(arrayOf(
                    entry.date,
                    entry.auditedFiles.size.toString(),
                    if (entry.auditedLoc > 0) entry.auditedLoc.toString() else "—",
                ))
            }

        if (tableModel.rowCount == 0) {
            tableModel.addRow(arrayOf("No activity recorded yet.", "", ""))
        }
    }

    companion object {
        private val COLUMNS = arrayOf("Date", "Files Audited", "LOC")
    }
}

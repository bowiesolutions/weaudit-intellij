package com.bowiesolutions.weaudit.toolwindow.tree

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import java.awt.Color
import javax.swing.JTree

/**
 * Custom cell renderer for all three weAudit trees.
 *
 * Renders each [WeAuditTreeNode] with an appropriate icon and label:
 *
 * | Node type         | Icon                          | Label                        |
 * |-------------------|-------------------------------|------------------------------|
 * | FileGroupNode     | AllIcons.Nodes.Folder         | filename only (not full path)|
 * | EntryNode Finding | AllIcons.General.Warning      | entry label + author badge   |
 * | EntryNode Note    | AllIcons.General.Information  | entry label + author badge   |
 * | LocationNode      | AllIcons.Nodes.Target         | filename:startLine–endLine   |
 * | AuditedFileNode   | AllIcons.Actions.Checked      | filename                     |
 * | PartialFileNode   | AllIcons.Actions.Show         | filename                     |
 * | PartialRegionNode | AllIcons.Nodes.Target         | lines startLine–endLine      |
 *
 * The author badge is rendered in a dimmer [SimpleTextAttributes.GRAYED_ATTRIBUTES]
 * style after the entry label, matching the VS Code extension's tree item description.
 *
 * Own entries (author == settings.username) render without a badge to reduce noise.
 */
class WeAuditTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree:     JTree,
        value:    Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf:     Boolean,
        row:      Int,
        hasFocus: Boolean,
    ) {
        val mutableNode = value as? javax.swing.tree.DefaultMutableTreeNode ?: return
        val node = mutableNode.userObject as? WeAuditTreeNode ?: return

        when (node) {
            is WeAuditTreeNode.FileGroupNode -> renderFileGroup(node)
            is WeAuditTreeNode.EntryNode     -> renderEntry(node)
            is WeAuditTreeNode.LocationNode  -> renderLocation(node)
            is WeAuditTreeNode.AuditedFileNode   -> renderAuditedFile(node)
            is WeAuditTreeNode.PartialFileNode   -> renderPartialFile(node)
            is WeAuditTreeNode.PartialRegionNode -> renderPartialRegion(node)
        }
    }

    // ── Node renderers ────────────────────────────────────────────────────────

    private fun renderFileGroup(node: WeAuditTreeNode.FileGroupNode) {
        icon = AllIcons.Nodes.Folder
        append(node.path.substringAfterLast('/').ifEmpty { node.path })
        toolTipText = node.path
    }

    private fun renderEntry(node: WeAuditTreeNode.EntryNode) {
        val settings = WeAuditSettingsState.getInstance()
        val isOwn    = node.author == settings.username

        icon = when (node.entry.entryTypeEnum) {
            EntryType.Finding -> AllIcons.General.Warning
            EntryType.Note    -> AllIcons.General.Information
        }

        // Severity prefix for findings (e.g. "[High] Label")
        val severityPrefix = node.entry.details
            ?.severity
            ?.takeIf { it.isNotBlank() }
            ?.let { "[$it] " }
            ?: ""

        val labelAttrs = if (node.entry.resolved)
            SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
        else
            SimpleTextAttributes.REGULAR_ATTRIBUTES

        append(severityPrefix + node.entry.label, labelAttrs)

        // Author badge — only show for co-auditor entries
        if (!isOwn) {
            append("  ${node.author}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        // Multi-region indicator
        if (node.hasMultipleLocations) {
            append("  (${node.entry.locations.size} locations)",
                SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        toolTipText = buildTooltip(node)
    }

    private fun renderLocation(node: WeAuditTreeNode.LocationNode) {
        icon = AllIcons.Nodes.Target
        val fileName = node.location.path.substringAfterLast('/')
        val lineRange = if (node.location.startLine == node.location.endLine)
            "line ${node.location.startLine + 1}"
        else
            "lines ${node.location.startLine + 1}–${node.location.endLine + 1}"
        val label = node.location.label.takeIf { it.isNotBlank() }
        append(label ?: "$fileName: $lineRange")
        if (label != null) {
            append("  $fileName: $lineRange", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
        toolTipText = node.location.path
    }

    private fun renderAuditedFile(node: WeAuditTreeNode.AuditedFileNode) {
        icon = AllIcons.Actions.Checked
        val fileName = node.auditedFile.path.substringAfterLast('/')
        append(fileName)
        append("  ${node.auditedFile.path}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        toolTipText = node.auditedFile.path
    }

    private fun renderPartialFile(node: WeAuditTreeNode.PartialFileNode) {
        icon = AllIcons.Actions.Show
        val fileName = node.partialFile.path.substringAfterLast('/')
        val regionCount = node.partialFile.regions.size
        append(fileName)
        append("  $regionCount region${if (regionCount != 1) "s" else ""}",
            SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        toolTipText = node.partialFile.path
    }

    private fun renderPartialRegion(node: WeAuditTreeNode.PartialRegionNode) {
        icon = AllIcons.Nodes.Target
        val lineRange = if (node.location.startLine == node.location.endLine)
            "Line ${node.location.startLine + 1}"
        else
            "Lines ${node.location.startLine + 1}–${node.location.endLine + 1}"
        append(lineRange)
        toolTipText = "${node.parentPath}: $lineRange"
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    private fun buildTooltip(node: WeAuditTreeNode.EntryNode): String = buildString {
        append("<html>")
        append("<b>${escHtml(node.entry.label)}</b><br/>")
        append("Author: ${escHtml(node.author)}<br/>")
        val loc = node.entry.primaryLocation
        append("${escHtml(loc.path)}: lines ${loc.startLine + 1}–${loc.endLine + 1}")
        node.entry.details?.let { d ->
            if (d.severity.isNotBlank())
                append("<br/>Severity: ${escHtml(d.severity)}")
            if (d.description.isNotBlank())
                append("<br/><i>${escHtml(d.description.take(120))}…</i>")
        }
        append("</html>")
    }

    private fun escHtml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

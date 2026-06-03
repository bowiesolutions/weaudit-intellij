package com.bowiesolutions.weaudit.boundary

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.Location

/**
 * Holds the state of an active boundary editing session.
 *
 * ## What boundary editing is
 * When the user invokes "Edit Finding Boundary", the plugin enters a modal-like
 * session where inlay hints appear at the top and bottom of the highlighted
 * region, offering clickable controls to expand or shrink the boundary one line
 * at a time.  The session ends when the user invokes "Done Editing Boundary"
 * or starts editing a different finding.
 *
 * ## VS Code equivalent
 * In VS Code, boundary editing used a CodeLens provider that registered lenses
 * at the start and end lines of the active entry.  Each lens was a clickable
 * command.  IntelliJ has no direct CodeLens equivalent so we use:
 *   - [com.intellij.codeInsight.hints.InlayHintsProvider] for the visual hints
 *   - Standard [AnAction]s with keyboard shortcuts for the boundary adjustments
 *
 * ## Session lifecycle
 * 1. User invokes [EditFindingBoundaryAction] (Cmd+B / Ctrl+B)
 * 2. [BoundaryEditSession] is created and stored in [BoundaryEditManager]
 * 3. Inlay hints refresh to show the boundary controls
 * 4. User presses expand/shrink actions or clicks the hints
 * 5. Each adjustment mutates [currentLocation] and persists via the store
 * 6. User invokes [DoneBoundaryEditAction] (Escape or Cmd+B again) → session cleared
 *
 * @param entryIndex   Index of the entry in [WeAuditStore.entries]
 * @param locationIndex Index within [entry.locations] being edited (usually 0)
 * @param entry        Snapshot of the entry at session start
 */
data class BoundaryEditSession(
    val entryIndex:    Int,
    val locationIndex: Int,
    val entry:         Entry,
) {
    /** The location currently being edited (may differ from the original after adjustments). */
    var currentLocation: Location = entry.locations[locationIndex]

    val filePath:  String get() = currentLocation.path
    val startLine: Int    get() = currentLocation.startLine
    val endLine:   Int    get() = currentLocation.endLine

    /** Produce an updated entry with [currentLocation] replacing the original. */
    fun updatedEntry(): Entry = entry.copy(
        locations = entry.locations.toMutableList().also {
            it[locationIndex] = currentLocation
        }
    )
}

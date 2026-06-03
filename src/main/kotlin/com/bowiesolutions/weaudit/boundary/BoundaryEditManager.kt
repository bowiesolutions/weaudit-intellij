package com.bowiesolutions.weaudit.boundary

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.store.WeAuditStore

/**
 * Project-scoped service that owns the active [BoundaryEditSession].
 *
 * At most one boundary edit session is active at a time per project.
 * All mutations go through this manager so that inlay hint providers
 * and toolbar actions always see consistent state.
 *
 * ## Threading
 * All public methods must be called on the EDT.
 *
 * ## Registration
 * ```xml
 * <projectService
 *     serviceImplementation="com.bowiesolutions.weaudit.boundary.BoundaryEditManager"/>
 * ```
 */
@Service(Service.Level.PROJECT)
class BoundaryEditManager(private val project: Project) {

    private val log = logger<BoundaryEditManager>()

    /** The currently active session, or null when not in boundary-edit mode. */
    var activeSession: BoundaryEditSession? = null
        private set

    /** Listeners notified (on the EDT) when the session starts, changes, or ends. */
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    fun addListener(l: () -> Unit)    { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    val isActive: Boolean get() = activeSession != null

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Start a boundary edit session for [entryIndex] / [locationIndex].
     * If a session is already active, it is committed first.
     */
    fun startSession(entryIndex: Int, locationIndex: Int = 0) {
        val store = WeAuditStore.getInstance(project)
        val entry = store.entries.getOrNull(entryIndex) ?: return

        // Commit any existing session before starting a new one
        if (activeSession != null) commitSession()

        activeSession = BoundaryEditSession(entryIndex, locationIndex, entry)
        log.info("weAudit: boundary edit started for '${entry.label}'")
        notifyListeners()
    }

    /**
     * Commit the current session to the store and end it.
     * The updated entry is persisted asynchronously.
     */
    fun commitSession() {
        val session = activeSession ?: return
        val store   = WeAuditStore.getInstance(project)

        // Guard: index may be stale if store was mutated during the session
        if (session.entryIndex < store.entries.size) {
            store.updateEntry(session.entryIndex, session.updatedEntry())
        }

        activeSession = null
        log.info("weAudit: boundary edit committed")
        notifyListeners()
    }

    /**
     * Discard the current session without saving changes.
     */
    fun cancelSession() {
        if (activeSession == null) return
        activeSession = null
        log.info("weAudit: boundary edit cancelled")
        notifyListeners()
    }

    // ── Boundary adjustments ──────────────────────────────────────────────────

    /**
     * Expand the start boundary upward by [lines] lines (minimum line 0).
     * Persists immediately and refreshes highlights.
     */
    fun expandStart(lines: Int = 1) = adjust { loc ->
        loc.copy(startLine = (loc.startLine - lines).coerceAtLeast(0))
    }

    /**
     * Shrink the start boundary downward by [lines] lines.
     * Will not go past [endLine] - 1 (minimum region is one line).
     */
    fun shrinkStart(lines: Int = 1) = adjust { loc ->
        loc.copy(startLine = (loc.startLine + lines).coerceAtMost(loc.endLine))
    }

    /**
     * Expand the end boundary downward by [lines] lines.
     * Capped at the last line of the file by the caller via the editor.
     */
    fun expandEnd(lines: Int = 1, maxLine: Int = Int.MAX_VALUE) = adjust { loc ->
        loc.copy(endLine = (loc.endLine + lines).coerceAtMost(maxLine))
    }

    /**
     * Shrink the end boundary upward by [lines] lines.
     * Will not go above [startLine] (minimum region is one line).
     */
    fun shrinkEnd(lines: Int = 1) = adjust { loc ->
        loc.copy(endLine = (loc.endLine - lines).coerceAtLeast(loc.startLine))
    }

    /**
     * Move the entire boundary up by [lines] lines (start and end together).
     */
    fun moveUp(lines: Int = 1) = adjust { loc ->
        val newStart = (loc.startLine - lines).coerceAtLeast(0)
        val delta    = loc.startLine - newStart
        loc.copy(startLine = newStart, endLine = loc.endLine - delta)
    }

    /**
     * Move the entire boundary down by [lines] lines.
     */
    fun moveDown(lines: Int = 1, maxLine: Int = Int.MAX_VALUE) = adjust { loc ->
        val newEnd   = (loc.endLine + lines).coerceAtMost(maxLine)
        val delta    = newEnd - loc.endLine
        loc.copy(startLine = loc.startLine + delta, endLine = newEnd)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun adjust(transform: (Location) -> Location) {
        val session = activeSession ?: return
        session.currentLocation = transform(session.currentLocation)

        // Persist incrementally so highlights update immediately
        val store = WeAuditStore.getInstance(project)
        if (session.entryIndex < store.entries.size) {
            store.updateEntry(session.entryIndex, session.updatedEntry())
        }

        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    companion object {
        fun getInstance(project: Project): BoundaryEditManager = project.service()
    }
}

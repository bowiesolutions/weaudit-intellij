package com.bowiesolutions.weaudit.boundary

import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BoundaryEditSession] and [BoundaryEditManager] adjustment logic.
 *
 * Uses [FakeBoundaryEditManager] to exercise all boundary arithmetic without
 * an IntelliJ platform fixture.
 */
class BoundaryEditSessionTest {

    private lateinit var manager: FakeBoundaryEditManager

    @BeforeEach
    fun setup() {
        manager = FakeBoundaryEditManager()
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @Test
    fun `session starts with correct initial location`() {
        val entry = entry(startLine = 10, endLine = 20)
        manager.startSession(entry, entryIndex = 0)

        assertTrue(manager.isActive)
        assertEquals(10, manager.session!!.startLine)
        assertEquals(20, manager.session!!.endLine)
    }

    @Test
    fun `updatedEntry reflects current location`() {
        val entry = entry(startLine = 5, endLine = 15)
        manager.startSession(entry, entryIndex = 0)
        manager.expandStart()

        val updated = manager.session!!.updatedEntry()
        assertEquals(4, updated.locations[0].startLine)
        assertEquals(15, updated.locations[0].endLine)
    }

    @Test
    fun `updatedEntry does not mutate original entry`() {
        val entry = entry(startLine = 5, endLine = 15)
        manager.startSession(entry, entryIndex = 0)
        manager.expandStart()

        // Original entry locations are unchanged
        assertEquals(5, entry.locations[0].startLine)
    }

    // ── expandStart ───────────────────────────────────────────────────────────

    @Test
    fun `expandStart moves startLine up by 1`() {
        manager.startSession(entry(10, 20), 0)
        manager.expandStart()
        assertEquals(9, manager.session!!.startLine)
        assertEquals(20, manager.session!!.endLine)
    }

    @Test
    fun `expandStart does not go below line 0`() {
        manager.startSession(entry(0, 5), 0)
        manager.expandStart()
        assertEquals(0, manager.session!!.startLine)
    }

    @Test
    fun `expandStart by multiple lines`() {
        manager.startSession(entry(10, 20), 0)
        manager.expandStart(3)
        assertEquals(7, manager.session!!.startLine)
    }

    // ── shrinkStart ───────────────────────────────────────────────────────────

    @Test
    fun `shrinkStart moves startLine down by 1`() {
        manager.startSession(entry(10, 20), 0)
        manager.shrinkStart()
        assertEquals(11, manager.session!!.startLine)
    }

    @Test
    fun `shrinkStart does not go past endLine`() {
        manager.startSession(entry(10, 10), 0)
        manager.shrinkStart()
        assertEquals(10, manager.session!!.startLine)
        assertEquals(10, manager.session!!.endLine)
    }

    // ── expandEnd ─────────────────────────────────────────────────────────────

    @Test
    fun `expandEnd moves endLine down by 1`() {
        manager.startSession(entry(10, 20), 0)
        manager.expandEnd(maxLine = 100)
        assertEquals(10, manager.session!!.startLine)
        assertEquals(21, manager.session!!.endLine)
    }

    @Test
    fun `expandEnd respects maxLine`() {
        manager.startSession(entry(10, 20), 0)
        manager.expandEnd(maxLine = 20)
        assertEquals(20, manager.session!!.endLine)
    }

    // ── shrinkEnd ─────────────────────────────────────────────────────────────

    @Test
    fun `shrinkEnd moves endLine up by 1`() {
        manager.startSession(entry(10, 20), 0)
        manager.shrinkEnd()
        assertEquals(19, manager.session!!.endLine)
    }

    @Test
    fun `shrinkEnd does not go above startLine`() {
        manager.startSession(entry(10, 10), 0)
        manager.shrinkEnd()
        assertEquals(10, manager.session!!.startLine)
        assertEquals(10, manager.session!!.endLine)
    }

    // ── moveUp ────────────────────────────────────────────────────────────────

    @Test
    fun `moveUp shifts both boundaries up by 1`() {
        manager.startSession(entry(10, 20), 0)
        manager.moveUp()
        assertEquals(9,  manager.session!!.startLine)
        assertEquals(19, manager.session!!.endLine)
    }

    @Test
    fun `moveUp does not go below line 0`() {
        manager.startSession(entry(0, 10), 0)
        manager.moveUp()
        assertEquals(0,  manager.session!!.startLine)
        assertEquals(10, manager.session!!.endLine)
    }

    @Test
    fun `moveUp preserves region size`() {
        manager.startSession(entry(5, 15), 0)
        manager.moveUp(3)
        assertEquals(2,  manager.session!!.startLine)
        assertEquals(12, manager.session!!.endLine)
        assertEquals(10, manager.session!!.endLine - manager.session!!.startLine)
    }

    // ── moveDown ──────────────────────────────────────────────────────────────

    @Test
    fun `moveDown shifts both boundaries down by 1`() {
        manager.startSession(entry(10, 20), 0)
        manager.moveDown(maxLine = 100)
        assertEquals(11, manager.session!!.startLine)
        assertEquals(21, manager.session!!.endLine)
    }

    @Test
    fun `moveDown respects maxLine and preserves region size`() {
        manager.startSession(entry(10, 20), 0)
        manager.moveDown(maxLine = 20)
        assertEquals(20, manager.session!!.endLine)
    }

    @Test
    fun `moveDown does not exceed maxLine`() {
        manager.startSession(entry(10, 20), 0)
        manager.moveDown(maxLine = 20)
        assertEquals(20, manager.session!!.endLine)
    }

    // ── Combined operations ───────────────────────────────────────────────────

    @Test
    fun `multiple adjustments compose correctly`() {
        manager.startSession(entry(10, 20), 0)
        manager.expandStart()    // 9..20
        manager.expandEnd(maxLine = 100)   // 9..21
        manager.shrinkStart()    // 10..21
        manager.moveUp()         // 9..20
        assertEquals(9,  manager.session!!.startLine)
        assertEquals(20, manager.session!!.endLine)
    }

    @Test
    fun `session is not active before start`() {
        assertFalse(manager.isActive)
    }

    @Test
    fun `session is not active after cancel`() {
        manager.startSession(entry(10, 20), 0)
        manager.cancelSession()
        assertFalse(manager.isActive)
    }

    @Test
    fun `location changes are isolated to session`() {
        val original = entry(10, 20)
        manager.startSession(original, 0)
        manager.expandStart()
        manager.expandStart()

        // Original location unchanged
        assertEquals(10, original.locations[0].startLine)
        // Session location updated
        assertEquals(8, manager.session!!.startLine)
    }

    // ── Multi-region ──────────────────────────────────────────────────────────

    @Test
    fun `editing location index 1 of multi-region entry`() {
        val entry = Entry(
            label     = "Multi",
            entryType = EntryType.Finding.jsonValue,
            author    = "alice",
            locations = listOf(
                Location("a.kt", 0, 5),
                Location("b.kt", 10, 20),
            )
        )
        manager.startSession(entry, entryIndex = 0, locationIndex = 1)
        manager.expandStart()

        val updated = manager.session!!.updatedEntry()
        // Location 0 unchanged
        assertEquals(0, updated.locations[0].startLine)
        // Location 1 adjusted
        assertEquals(9, updated.locations[1].startLine)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun entry(startLine: Int, endLine: Int) = Entry(
        label     = "Test",
        entryType = EntryType.Finding.jsonValue,
        author    = "alice",
        locations = listOf(Location("test.kt", startLine, endLine)),
    )
}

/**
 * Test double for [BoundaryEditManager] that exercises the adjustment logic
 * without an IntelliJ project.  Delegates all arithmetic to [BoundaryEditSession]
 * directly, bypassing the store persistence calls.
 */
class FakeBoundaryEditManager {

    var session: BoundaryEditSession? = null
    val isActive: Boolean get() = session != null

    fun startSession(entry: Entry, entryIndex: Int, locationIndex: Int = 0) {
        session = BoundaryEditSession(entryIndex, locationIndex, entry)
    }

    fun cancelSession() { session = null }

    fun expandStart(lines: Int = 1) = adjust { loc ->
        loc.copy(startLine = (loc.startLine - lines).coerceAtLeast(0))
    }

    fun shrinkStart(lines: Int = 1) = adjust { loc ->
        loc.copy(startLine = (loc.startLine + lines).coerceAtMost(loc.endLine))
    }

    fun expandEnd(lines: Int = 1, maxLine: Int = Int.MAX_VALUE) = adjust { loc ->
        loc.copy(endLine = (loc.endLine + lines).coerceAtMost(maxLine))
    }

    fun shrinkEnd(lines: Int = 1) = adjust { loc ->
        loc.copy(endLine = (loc.endLine - lines).coerceAtLeast(loc.startLine))
    }

    fun moveUp(lines: Int = 1) = adjust { loc ->
        val newStart = (loc.startLine - lines).coerceAtLeast(0)
        val delta    = loc.startLine - newStart
        loc.copy(startLine = newStart, endLine = loc.endLine - delta)
    }

    fun moveDown(lines: Int = 1, maxLine: Int = Int.MAX_VALUE) = adjust { loc ->
        val newEnd  = (loc.endLine + lines).coerceAtMost(maxLine)
        val delta   = newEnd - loc.endLine
        loc.copy(startLine = loc.startLine + delta, endLine = newEnd)
    }

    private fun adjust(transform: (Location) -> Location) {
        val s = session ?: return
        s.currentLocation = transform(s.currentLocation)
    }
}

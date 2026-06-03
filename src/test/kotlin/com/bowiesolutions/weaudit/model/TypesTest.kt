package com.bowiesolutions.weaudit.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for the pure data-model types in [com.bowiesolutions.weaudit.model].
 *
 * No IntelliJ platform required — these are plain Kotlin data class tests.
 */
class TypesTest {

    // ── EntryType ─────────────────────────────────────────────────────────────

    @Test
    fun `EntryType Finding has jsonValue 0`() {
        assertEquals(0, EntryType.Finding.jsonValue)
    }

    @Test
    fun `EntryType Note has jsonValue 1`() {
        assertEquals(1, EntryType.Note.jsonValue)
    }

    @Test
    fun `EntryType fromInt round-trips`() {
        assertEquals(EntryType.Finding, EntryType.fromInt(0))
        assertEquals(EntryType.Note,    EntryType.fromInt(1))
    }

    @Test
    fun `EntryType fromInt throws on unknown value`() {
        assertThrows<NoSuchElementException> { EntryType.fromInt(99) }
    }

    // ── Entry convenience accessors ───────────────────────────────────────────

    @Test
    fun `entryTypeEnum returns correct type for Finding`() {
        val e = entryWith(entryType = 0)
        assertEquals(EntryType.Finding, e.entryTypeEnum)
    }

    @Test
    fun `entryTypeEnum returns correct type for Note`() {
        val e = entryWith(entryType = 1)
        assertEquals(EntryType.Note, e.entryTypeEnum)
    }

    @Test
    fun `primaryLocation returns first location`() {
        val e = Entry(
            label     = "x",
            entryType = 0,
            author    = "a",
            locations = listOf(
                Location("first.ts",  0,  5),
                Location("second.ts", 6, 10),
            )
        )
        assertEquals("first.ts", e.primaryLocation.path)
    }

    @Test
    fun `resolved defaults to false`() {
        val e = entryWith()
        assertFalse(e.resolved)
    }

    @Test
    fun `details defaults to null`() {
        val e = entryWith()
        assertNull(e.details)
    }

    // ── FindingDetails ────────────────────────────────────────────────────────

    @Test
    fun `FindingDetails EMPTY has all blank fields`() {
        val d = FindingDetails.EMPTY
        assertEquals("", d.title)
        assertEquals("", d.severity)
        assertEquals("", d.difficulty)
        assertEquals("", d.type)
        assertEquals("", d.description)
        assertEquals("", d.exploit)
        assertEquals("", d.recommendation)
    }

    @Test
    fun `FindingDetails is a value type - copy works`() {
        val d = FindingDetails(title = "Original")
        val updated = d.copy(title = "Updated", severity = "High")
        assertEquals("Original", d.title)       // original unchanged
        assertEquals("Updated",  updated.title)
        assertEquals("High",     updated.severity)
    }

    // ── SerializedData ────────────────────────────────────────────────────────

    @Test
    fun `SerializedData EMPTY has empty lists`() {
        assertEquals(0, SerializedData.EMPTY.treeEntries.size)
        assertEquals(0, SerializedData.EMPTY.auditedFiles.size)
        assertEquals(0, SerializedData.EMPTY.partiallyAuditedFiles.size)
    }

    @Test
    fun `SerializedData copy-on-mutate does not share list references`() {
        val original = SerializedData(treeEntries = listOf(entryWith(label = "a")))
        val updated  = original.copy(treeEntries = original.treeEntries + entryWith(label = "b"))
        assertEquals(1, original.treeEntries.size)  // original unaffected
        assertEquals(2, updated.treeEntries.size)
    }

    // ── DayLog ────────────────────────────────────────────────────────────────

    @Test
    fun `DayLog EMPTY has no entries`() {
        assertEquals(0, DayLog.EMPTY.entries.size)
    }

    @Test
    fun `DayLogEntry defaults auditedLoc to 0`() {
        val entry = DayLogEntry(date = "2024-01-01")
        assertEquals(0, entry.auditedLoc)
        assertEquals(0, entry.auditedFiles.size)
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Test
    fun `Location label and description default to empty string`() {
        val loc = Location("src/x.rs", 0, 10)
        assertEquals("", loc.label)
        assertEquals("", loc.description)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun entryWith(
        label:     String = "test",
        entryType: Int    = EntryType.Finding.jsonValue,
        author:    String = "tester",
    ) = Entry(
        label     = label,
        entryType = entryType,
        author    = author,
        locations = listOf(Location("test.ts", 0, 1))
    )
}

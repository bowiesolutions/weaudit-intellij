package com.bowiesolutions.weaudit.panels

import com.bowiesolutions.weaudit.model.FindingDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FindingDetailsPanel] option constants and field validation.
 *
 * The Swing construction requires a real [Project], so we test only the
 * pure-logic parts: option lists, [FindingDetails] field mapping, and
 * the guard conditions for save.
 */
class FindingDetailsPanelTest {

    // ── Option lists ──────────────────────────────────────────────────────────

    @Test
    fun `severity options include expected values`() {
        val opts = FindingDetailsPanel.SEVERITY_OPTIONS.toList()
        assertTrue(opts.contains("Critical"))
        assertTrue(opts.contains("High"))
        assertTrue(opts.contains("Medium"))
        assertTrue(opts.contains("Low"))
        assertTrue(opts.contains("Informational"))
        // First option is blank (placeholder)
        assertEquals("", opts.first())
    }

    @Test
    fun `difficulty options include expected values`() {
        val opts = FindingDetailsPanel.DIFFICULTY_OPTIONS.toList()
        assertTrue(opts.contains("High"))
        assertTrue(opts.contains("Medium"))
        assertTrue(opts.contains("Low"))
        assertEquals("", opts.first())
    }

    @Test
    fun `type options include common audit finding categories`() {
        val opts = FindingDetailsPanel.TYPE_OPTIONS.toList()
        assertTrue(opts.contains("Access Control"))
        assertTrue(opts.contains("Authentication"))
        assertTrue(opts.contains("Cryptography"))
        assertTrue(opts.contains("Data Validation"))
        assertEquals("", opts.first())
    }

    @Test
    fun `all option arrays have blank first element`() {
        listOf(
            FindingDetailsPanel.SEVERITY_OPTIONS,
            FindingDetailsPanel.DIFFICULTY_OPTIONS,
            FindingDetailsPanel.TYPE_OPTIONS,
        ).forEach { arr ->
            assertEquals("", arr.first(),
                "Expected blank first element in ${arr.contentToString()}")
        }
    }

    // ── FindingDetails construction ───────────────────────────────────────────

    @Test
    fun `FindingDetails copy preserves all fields`() {
        val original = FindingDetails(
            title          = "T",
            severity       = "High",
            difficulty     = "Low",
            type           = "Access Control",
            description    = "D",
            exploit        = "E",
            recommendation = "R",
        )
        val copy = original.copy(severity = "Medium")
        assertEquals("T",              copy.title)
        assertEquals("Medium",         copy.severity)
        assertEquals("Low",            copy.difficulty)
        assertEquals("Access Control", copy.type)
        assertEquals("D",              copy.description)
        assertEquals("E",              copy.exploit)
        assertEquals("R",              copy.recommendation)
    }

    @Test
    fun `FindingDetails EMPTY has all blank strings`() {
        val d = FindingDetails.EMPTY
        listOf(d.title, d.severity, d.difficulty, d.type,
               d.description, d.exploit, d.recommendation)
            .forEach { assertEquals("", it) }
    }

    @Test
    fun `blank severity field does not break FindingDetails creation`() {
        val d = FindingDetails(severity = "")
        assertEquals("", d.severity)
    }
}

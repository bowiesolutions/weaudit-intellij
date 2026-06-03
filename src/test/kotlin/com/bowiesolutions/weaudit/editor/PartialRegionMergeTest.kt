package com.bowiesolutions.weaudit.editor

import com.bowiesolutions.weaudit.actions.MarkRegionAsReviewedAction
import com.bowiesolutions.weaudit.model.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the partial-region merge/split logic in [MarkRegionAsReviewedAction].
 *
 * This is pure algorithmic logic ported from `codeMarker.ts` — no IntelliJ
 * platform needed.  We test via the [TestableMarkRegionAction] subclass that
 * exposes the private `mergeRegion` method.
 *
 * Test cases mirror the four scenarios described in the VS Code extension docs:
 *  1. Exact match → unmark
 *  2. Selection contains existing → extend
 *  3. Selection is contained in existing → split
 *  4. No overlap → add new
 */
class PartialRegionMergeTest {

    // ── Expose the protected mergeRegion for testing ──────────────────────────

    /**
     * Thin wrapper that makes [mergeRegion] public for testing without needing
     * a platform fixture to instantiate the real action.
     */
    private val action = TestableMarkRegionAction()

    private fun merge(existing: List<Location>, newRegion: Location) =
        action.testMergeRegion(existing, newRegion)

    private fun loc(start: Int, end: Int) = Location("test.ts", start, end)

    // ── Scenario 1: exact match → unmark ─────────────────────────────────────

    @Test
    fun `exact match removes the region`() {
        val existing = listOf(loc(10, 20))
        val result   = merge(existing, loc(10, 20))
        assertTrue(result.isEmpty(), "Exact match should remove the region")
    }

    @Test
    fun `exact match with multiple regions removes only the matching one`() {
        val existing = listOf(loc(5, 9), loc(10, 20), loc(25, 30))
        val result   = merge(existing, loc(10, 20))
        assertEquals(2, result.size)
        assertEquals(loc(5, 9),   result[0])
        assertEquals(loc(25, 30), result[1])
    }

    // ── Scenario 2: new region contains existing → extend ────────────────────

    @Test
    fun `new region that contains existing extends the region`() {
        val existing = listOf(loc(12, 18))
        val result   = merge(existing, loc(10, 20))
        assertEquals(1, result.size)
        assertEquals(loc(10, 20), result[0])
    }

    @Test
    fun `new region containing multiple existing merges them all`() {
        val existing = listOf(loc(10, 15), loc(20, 25))
        val result   = merge(existing, loc(5, 30))
        // Both are absorbed; result is a single extended region.
        assertEquals(1, result.size)
        assertEquals(loc(5, 30), result[0])
    }

    // ── Scenario 3: new region is contained in existing → split ──────────────

    @Test
    fun `new region contained in existing splits into two`() {
        val existing = listOf(loc(0, 30))
        val result   = merge(existing, loc(10, 20))
        assertEquals(2, result.size)
        assertEquals(loc(0,  9),  result[0], "Top half")
        assertEquals(loc(21, 30), result[1], "Bottom half")
    }

    @Test
    fun `new region at start of existing produces only bottom half`() {
        val existing = listOf(loc(0, 20))
        val result   = merge(existing, loc(0, 10))
        // No top half (start == existing start), only bottom half.
        assertEquals(1, result.size)
        assertEquals(loc(11, 20), result[0])
    }

    @Test
    fun `new region at end of existing produces only top half`() {
        val existing = listOf(loc(0, 20))
        val result   = merge(existing, loc(10, 20))
        // No bottom half, only top half.
        assertEquals(1, result.size)
        assertEquals(loc(0, 9), result[0])
    }

    @Test
    fun `new region that exactly matches produces empty (exact match wins over split)`() {
        // Exact-match check runs before split check.
        val existing = listOf(loc(10, 20))
        val result   = merge(existing, loc(10, 20))
        assertTrue(result.isEmpty())
    }

    // ── Scenario 4: no overlap → add new ─────────────────────────────────────

    @Test
    fun `non-overlapping region is appended`() {
        val existing = listOf(loc(0, 10))
        val result   = merge(existing, loc(20, 30))
        assertEquals(2, result.size)
        assertEquals(loc(0,  10), result[0])
        assertEquals(loc(20, 30), result[1])
    }

    @Test
    fun `adding to empty list creates a single region`() {
        val result = merge(emptyList(), loc(5, 15))
        assertEquals(1, result.size)
        assertEquals(loc(5, 15), result[0])
    }

    @Test
    fun `result is always sorted by startLine`() {
        val existing = listOf(loc(20, 25))
        val result   = merge(existing, loc(1, 5))
        assertEquals(loc(1,  5),  result[0])
        assertEquals(loc(20, 25), result[1])
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `adjacent regions are not merged (they remain separate)`() {
        val existing = listOf(loc(0, 9))
        val result   = merge(existing, loc(10, 20))
        // Lines 9 and 10 are adjacent but not overlapping — both remain.
        assertEquals(2, result.size)
    }

    @Test
    fun `single-line region round-trips correctly`() {
        val result = merge(emptyList(), loc(42, 42))
        assertEquals(1, result.size)
        assertEquals(loc(42, 42), result[0])
    }
}

/**
 * Test-only subclass of [MarkRegionAsReviewedAction] that exposes the
 * protected `mergeRegion` function for unit testing.
 *
 * This pattern avoids reflection and doesn't require a platform fixture.
 */
private class TestableMarkRegionAction : MarkRegionAsReviewedAction() {
    fun testMergeRegion(existing: List<Location>, newRegion: Location): List<Location> =
        mergeRegion(existing, newRegion)
}

package com.bowiesolutions.weaudit.store

import com.bowiesolutions.weaudit.model.EntryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Parses the bundled [fixtures/alice.weaudit] file — a realistic multi-entry
 * `.weaudit` JSON that exercises all field types.
 *
 * If this test passes, the IntelliJ plugin can read any `.weaudit` file
 * produced by the VS Code extension on the same project.
 */
class FixtureRoundTripTest {

    private val fixturePath = Paths.get(
        checkNotNull(javaClass.classLoader.getResource("fixtures/alice.weaudit")) {
            "Test fixture not found — check src/test/resources/fixtures/alice.weaudit"
        }.toURI()
    )

    @Test
    fun `alice weaudit fixture parses completely`() {
        val data = WeAuditSerializer.readWeAuditFile(fixturePath)

        // Four entries total
        assertEquals(4, data.treeEntries.size)

        // 1. High-severity Finding with full details
        val vuln = data.treeEntries[0]
        assertEquals("Unchecked return value in transfer()", vuln.label)
        assertEquals(EntryType.Finding, vuln.entryTypeEnum)
        assertEquals("alice",           vuln.author)
        assertFalse(vuln.resolved)
        assertEquals("src/Token.sol",   vuln.primaryLocation.path)
        assertEquals(87,                vuln.primaryLocation.startLine)
        assertEquals(92,                vuln.primaryLocation.endLine)
        assertEquals("High",            vuln.details?.severity)
        assertEquals("Low",             vuln.details?.difficulty)
        assertEquals("Data Validation", vuln.details?.type)

        // 2. Note (no details)
        val note = data.treeEntries[1]
        assertEquals(EntryType.Note, note.entryTypeEnum)
        assertEquals("src/Nonce.sol",   note.primaryLocation.path)
        assertEquals(null,              note.details)

        // 3. Multi-region finding
        val multi = data.treeEntries[2]
        assertEquals(2, multi.locations.size)
        assertEquals("Constructor",     multi.locations[0].label)
        assertEquals("initialize()",    multi.locations[1].label)
        assertEquals(55,                multi.locations[1].startLine)

        // 4. Resolved finding
        val resolved = data.treeEntries[3]
        assertTrue(resolved.resolved)
        assertEquals("Informational",   resolved.details?.severity)

        // Audited files
        assertEquals(2, data.auditedFiles.size)
        assertEquals("src/Safe.sol",   data.auditedFiles[0].path)
        assertEquals("src/README.md",  data.auditedFiles[1].path)

        // Partially audited files
        assertEquals(1, data.partiallyAuditedFiles.size)
        val partial = data.partiallyAuditedFiles[0]
        assertEquals("src/Token.sol",  partial.path)
        assertEquals(1,                partial.regions.size)
        assertEquals(0,                partial.regions[0].startLine)
        assertEquals(86,               partial.regions[0].endLine)
    }

    @Test
    fun `fixture round-trips without data loss`() {
        val original = WeAuditSerializer.readWeAuditFile(fixturePath)

        // Write to a temp file and read back
        val tmp = kotlin.io.path.createTempFile( prefix="weaudit-rt-", suffix=".weaudit")
        tmp.toFile().deleteOnExit()

        WeAuditSerializer.writeWeAuditFile(tmp, original)
        val reloaded = WeAuditSerializer.readWeAuditFile(tmp)

        assertEquals(original, reloaded)
    }
}

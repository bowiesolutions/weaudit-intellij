package com.bowiesolutions.weaudit.store

import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.DayLog
import com.bowiesolutions.weaudit.model.DayLogEntry
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.FindingDetails
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.model.SerializedData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Unit tests for [WeAuditSerializer].
 *
 * These tests run as plain JUnit 5 — no IntelliJ platform fixture, no sandbox IDE.
 * This is intentional: the serializer has zero platform dependencies and should
 * be fast enough to run on every commit.
 *
 * Test strategy:
 * 1. Round-trip: write a Kotlin object → read it back → assert equality.
 * 2. Compatibility: parse real-world JSON produced by the VS Code extension
 *    (stored in src/test/resources/fixtures/) and assert field values.
 * 3. Lenience: unknown fields, missing optional fields, and empty arrays
 *    all parse without error.
 * 4. DayLog format: verify that `.weauditdaylog` is serialized as a raw JSON
 *    array (not a wrapped object) to match VS Code's format.
 */
class WeAuditSerializerTest {

    @TempDir
    lateinit var tmp: Path

    // ── .weaudit round-trip ───────────────────────────────────────────────────

    @Test
    fun `round-trip empty SerializedData`() {
        val path = tmp.resolve("empty.weaudit")
        WeAuditSerializer.writeWeAuditFile(path, SerializedData.EMPTY)
        val loaded = WeAuditSerializer.readWeAuditFile(path)
        assertEquals(SerializedData.EMPTY, loaded)
    }

    @Test
    fun `round-trip single finding`() {
        val data = SerializedData(
            treeEntries = listOf(
                Entry(
                    label     = "Reentrancy in withdraw()",
                    entryType = EntryType.Finding.jsonValue,
                    author    = "alice",
                    locations = listOf(
                        Location(path = "src/Vault.sol", startLine = 42, endLine = 55)
                    ),
                    details = FindingDetails(
                        title          = "Reentrancy",
                        severity       = "High",
                        difficulty     = "Medium",
                        type           = "Access Control",
                        description    = "The `withdraw` function calls an external contract before updating state.",
                        exploit        = "Attacker deploys a malicious contract and calls withdraw repeatedly.",
                        recommendation = "Apply the checks-effects-interactions pattern.",
                    )
                )
            )
        )

        val path = tmp.resolve("finding.weaudit")
        WeAuditSerializer.writeWeAuditFile(path, data)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        assertEquals(1,         loaded.treeEntries.size)
        val e = loaded.treeEntries[0]
        assertEquals("Reentrancy in withdraw()", e.label)
        assertEquals(EntryType.Finding,          e.entryTypeEnum)
        assertEquals("alice",                    e.author)
        assertEquals("src/Vault.sol",            e.primaryLocation.path)
        assertEquals(42,                         e.primaryLocation.startLine)
        assertEquals(55,                         e.primaryLocation.endLine)
        assertEquals("High",                     e.details?.severity)
        assertFalse(e.resolved)
    }

    @Test
    fun `round-trip note entry`() {
        val data = SerializedData(
            treeEntries = listOf(
                Entry(
                    label     = "Interesting pattern here",
                    entryType = EntryType.Note.jsonValue,
                    author    = "bob",
                    locations = listOf(Location("lib/util.ts", 10, 12)),
                )
            )
        )

        val path = tmp.resolve("note.weaudit")
        WeAuditSerializer.writeWeAuditFile(path, data)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        assertEquals(EntryType.Note, loaded.treeEntries[0].entryTypeEnum)
        assertEquals("bob",          loaded.treeEntries[0].author)
    }

    @Test
    fun `round-trip multi-region finding`() {
        val data = SerializedData(
            treeEntries = listOf(
                Entry(
                    label     = "Integer overflow across files",
                    entryType = EntryType.Finding.jsonValue,
                    author    = "carol",
                    locations = listOf(
                        Location("src/math.rs",     5,  10, label = "Overflow site"),
                        Location("src/handler.rs", 88, 92, label = "Call site"),
                    )
                )
            )
        )

        val path = tmp.resolve("multi.weaudit")
        WeAuditSerializer.writeWeAuditFile(path, data)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        assertEquals(2, loaded.treeEntries[0].locations.size)
        assertEquals("Overflow site", loaded.treeEntries[0].locations[0].label)
        assertEquals("Call site",     loaded.treeEntries[0].locations[1].label)
    }

    @Test
    fun `round-trip resolved finding`() {
        val data = SerializedData(
            treeEntries = listOf(
                Entry(
                    label     = "Fixed vuln",
                    entryType = EntryType.Finding.jsonValue,
                    author    = "dave",
                    locations = listOf(Location("main.go", 1, 1)),
                    resolved  = true,
                )
            )
        )

        val path = tmp.resolve("resolved.weaudit")
        WeAuditSerializer.writeWeAuditFile(path, data)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        assertTrue(loaded.treeEntries[0].resolved)
    }

    @Test
    fun `round-trip audited and partially-audited files`() {
        val data = SerializedData(
            auditedFiles = listOf(
                AuditedFile("src/auth.ts"),
                AuditedFile("src/crypto.ts"),
            ),
            partiallyAuditedFiles = listOf(
                PartiallyAuditedFile(
                    path    = "src/complex.ts",
                    regions = listOf(Location("src/complex.ts", 0, 50))
                )
            )
        )

        val path = tmp.resolve("files.weaudit")
        WeAuditSerializer.writeWeAuditFile(path, data)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        assertEquals(2, loaded.auditedFiles.size)
        assertEquals("src/auth.ts", loaded.auditedFiles[0].path)
        assertEquals(1, loaded.partiallyAuditedFiles.size)
        assertEquals("src/complex.ts", loaded.partiallyAuditedFiles[0].path)
        assertEquals(1, loaded.partiallyAuditedFiles[0].regions.size)
    }

    // ── VS Code compatibility: parse the exact JSON shape from the GitHub issue ──

    @Test
    fun `parse weaudit JSON from VS Code extension (real-world shape)`() {
        // This JSON matches the exact shape shown in
        // https://github.com/trailofbits/vscode-weaudit/issues/34
        // (the canonical real-world example from the VS Code authors).
        val json = """
            {
              "treeEntries": [
                {
                  "label": "Server-side vuln",
                  "entryType": 0,
                  "author": "lime",
                  "locations": [
                    {
                      "path": "server.ts",
                      "startLine": 1,
                      "endLine": 1,
                      "label": "",
                      "description": ""
                    }
                  ],
                  "details": {
                    "title": "",
                    "severity": "",
                    "difficulty": "",
                    "type": "",
                    "description": "",
                    "exploit": "",
                    "recommendation": ""
                  }
                },
                {
                  "label": "Client-side vuln",
                  "entryType": 0,
                  "author": "lime",
                  "locations": [
                    {
                      "path": "../frontend/index.html",
                      "startLine": 6,
                      "endLine": 6,
                      "label": "",
                      "description": ""
                    }
                  ],
                  "details": {}
                }
              ],
              "auditedFiles": [],
              "partiallyAuditedFiles": []
            }
        """.trimIndent()

        val path = tmp.resolve("vscode.weaudit")
        path.writeText(json)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        assertEquals(2, loaded.treeEntries.size)
        assertEquals("Server-side vuln",       loaded.treeEntries[0].label)
        assertEquals(EntryType.Finding,        loaded.treeEntries[0].entryTypeEnum)
        assertEquals("lime",                   loaded.treeEntries[0].author)
        assertEquals("server.ts",              loaded.treeEntries[0].primaryLocation.path)
        assertEquals(1,                        loaded.treeEntries[0].primaryLocation.startLine)
        assertEquals("../frontend/index.html", loaded.treeEntries[1].primaryLocation.path)
        assertEquals(0, loaded.auditedFiles.size)
        assertEquals(0, loaded.partiallyAuditedFiles.size)
    }

    // ── Lenience ──────────────────────────────────────────────────────────────

    @Test
    fun `unknown top-level fields are ignored`() {
        val json = """
            {
              "treeEntries": [],
              "auditedFiles": [],
              "partiallyAuditedFiles": [],
              "futureField": "some value added in a future VS Code extension version"
            }
        """.trimIndent()
        val path = tmp.resolve("future.weaudit")
        path.writeText(json)

        // Should not throw
        val loaded = WeAuditSerializer.readWeAuditFile(path)
        assertEquals(0, loaded.treeEntries.size)
    }

    @Test
    fun `missing optional fields on Entry use defaults`() {
        val json = """
            {
              "treeEntries": [
                {
                  "label": "Minimal entry",
                  "entryType": 1,
                  "author": "eve",
                  "locations": [{ "path": "x.py", "startLine": 0, "endLine": 0 }]
                }
              ],
              "auditedFiles": [],
              "partiallyAuditedFiles": []
            }
        """.trimIndent()
        val path = tmp.resolve("minimal.weaudit")
        path.writeText(json)
        val loaded = WeAuditSerializer.readWeAuditFile(path)

        val e = loaded.treeEntries[0]
        assertEquals(EntryType.Note, e.entryTypeEnum)
        assertFalse(e.resolved)         // default
        assertEquals(null, e.details)   // absent = null
        assertEquals("",  e.primaryLocation.label)
    }

    @Test
    fun `missing file returns EMPTY without throwing`() {
        val path = tmp.resolve("does-not-exist.weaudit")
        val result = WeAuditSerializer.readWeAuditFile(path)
        assertEquals(SerializedData.EMPTY, result)
    }

    @Test
    fun `malformed JSON throws WeAuditIoException`() {
        val path = tmp.resolve("bad.weaudit")
        path.writeText("{ this is not json }")
        assertThrows<WeAuditIoException> {
            WeAuditSerializer.readWeAuditFile(path)
        }
    }

    // ── .weauditdaylog format ─────────────────────────────────────────────────

    @Test
    fun `daylog round-trip as raw JSON array`() {
        val dayLog = DayLog(
            listOf(
                DayLogEntry("2024-03-19", listOf("src/auth.ts", "src/crypto.ts"), 120),
                DayLogEntry("2024-03-20", listOf("src/main.ts"), 80),
            )
        )

        val path = tmp.resolve("alice.weauditdaylog")
        WeAuditSerializer.writeDayLogFile(path, dayLog)

        // Verify the file starts with '[' — a raw JSON array, not '{'.
        val text = path.toFile().readText()
        assertTrue(text.trimStart().startsWith("["),
            "Day log must be a raw JSON array to be compatible with VS Code extension.\n" +
            "Actual content: ${text.take(50)}")

        val loaded = WeAuditSerializer.readDayLogFile(path)
        assertEquals(2, loaded.entries.size)
        assertEquals("2024-03-19", loaded.entries[0].date)
        assertEquals(120, loaded.entries[0].auditedLoc)
        assertEquals(2, loaded.entries[0].auditedFiles.size)
    }

    @Test
    fun `missing daylog returns EMPTY without throwing`() {
        val path = tmp.resolve("no.weauditdaylog")
        val result = WeAuditSerializer.readDayLogFile(path)
        assertEquals(DayLog.EMPTY, result)
    }

    @Test
    fun `daylog with unknown fields is lenient`() {
        val json = """
            [
              { "date": "2024-01-01", "auditedFiles": [], "auditedLOC": 0, "unknownNewField": true }
            ]
        """.trimIndent()
        val path = tmp.resolve("lenient.weauditdaylog")
        path.writeText(json)
        val loaded = WeAuditSerializer.readDayLogFile(path)
        assertEquals(1, loaded.entries.size)
        assertEquals("2024-01-01", loaded.entries[0].date)
    }
}

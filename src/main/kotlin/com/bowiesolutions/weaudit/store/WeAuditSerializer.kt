package com.bowiesolutions.weaudit.store

import com.intellij.openapi.diagnostic.logger
import com.bowiesolutions.weaudit.model.DayLog
import com.bowiesolutions.weaudit.model.DayLogEntry
import com.bowiesolutions.weaudit.model.SerializedData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Pure-Kotlin JSON serialization layer for `.weaudit` and `.weauditdaylog` files.
 *
 * This class has **no IntelliJ Platform dependencies** (no VirtualFile, no Project)
 * so it can be exercised in plain JUnit 5 unit tests without a platform fixture.
 * The VFS refresh that IntelliJ requires after writing is the caller's responsibility
 * (see [WeAuditStore]).
 *
 * ## Format contract
 *
 * `.weaudit` files:
 * ```json
 * {
 *   "treeEntries": [...],
 *   "auditedFiles": [...],
 *   "partiallyAuditedFiles": [...]
 * }
 * ```
 *
 * `.weauditdaylog` files — the VS Code extension writes a **raw JSON array**,
 * not a wrapped object.  We therefore serialize/deserialize [DayLog] as its
 * inner list so the bytes match what VS Code produces:
 * ```json
 * [
 *   { "date": "2024-03-19", "auditedFiles": [...], "auditedLOC": 42 },
 *   ...
 * ]
 * ```
 *
 * ## Lenient parsing
 * Unknown keys are ignored (`ignoreUnknownKeys = true`) so that future VS Code
 * extension versions can add fields without breaking the IntelliJ plugin.
 */
object WeAuditSerializer {

    private val log = logger<WeAuditSerializer>()

    /**
     * Lenient [Json] instance used for all reads.
     * - `ignoreUnknownKeys`: forward-compat with newer VS Code extension versions.
     * - `isLenient`: tolerates minor JSON quirks (trailing commas in some editors).
     * - `coerceInputValues`: if an enum value is unrecognized, falls back to default.
     */
    val jsonReader: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Strict [Json] instance used for writes — produces canonical, pretty-printed
     * JSON that is diff-friendly in git (matching VS Code's pretty-print behavior).
     */
    val jsonWriter: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "   // VS Code uses 2-space indent
        encodeDefaults = true       // always write optional fields so VS Code can read them
    }

    // ── .weaudit read/write ───────────────────────────────────────────────────

    /**
     * Read a `.weaudit` file from [path].
     * Returns [SerializedData.EMPTY] if the file does not exist (new project).
     * Throws [WeAuditIoException] on parse or IO errors.
     */
    fun readWeAuditFile(path: Path): SerializedData {
        if (!path.exists()) return SerializedData.EMPTY
        return try {
            val text = path.readText(Charsets.UTF_8)
            jsonReader.decodeFromString<SerializedData>(text)
        } catch (e: Exception) {
            log.warn("Failed to parse .weaudit file: $path", e)
            throw WeAuditIoException("Cannot parse ${path.fileName}: ${e.message}", e)
        }
    }

    /**
     * Write [data] to [path] as pretty-printed JSON.
     * Creates parent directories if necessary.
     * Throws [WeAuditIoException] on IO errors.
     */
    fun writeWeAuditFile(path: Path, data: SerializedData) {
        try {
            path.parent?.createDirectories()
            path.writeText(jsonWriter.encodeToString(data), Charsets.UTF_8)
        } catch (e: IOException) {
            log.error("Failed to write .weaudit file: $path", e)
            throw WeAuditIoException("Cannot write ${path.fileName}: ${e.message}", e)
        }
    }

    // ── .weauditdaylog read/write ─────────────────────────────────────────────

    /**
     * Read a `.weauditdaylog` file from [path].
     *
     * The VS Code extension writes a **raw JSON array** (not a wrapped object),
     * so we deserialize `List<DayLogEntry>` directly and wrap it in [DayLog].
     *
     * Returns [DayLog.EMPTY] if the file does not exist.
     * Throws [WeAuditIoException] on parse or IO errors.
     */
    fun readDayLogFile(path: Path): DayLog {
        if (!path.exists()) return DayLog.EMPTY
        return try {
            val text = path.readText(Charsets.UTF_8)
            val entries = jsonReader.decodeFromString<List<DayLogEntry>>(
                text
            )
            DayLog(entries)
        } catch (e: Exception) {
            log.warn("Failed to parse .weauditdaylog file: $path", e)
            throw WeAuditIoException("Cannot parse ${path.fileName}: ${e.message}", e)
        }
    }

    /**
     * Write [dayLog] to [path] as a raw pretty-printed JSON array.
     * Creates parent directories if necessary.
     * Throws [WeAuditIoException] on IO errors.
     */
    fun writeDayLogFile(path: Path, dayLog: DayLog) {
        try {
            path.parent?.createDirectories()
            val text = jsonWriter.encodeToString<List<DayLogEntry>>(
                dayLog.entries
            )
            path.writeText(text, Charsets.UTF_8)
        } catch (e: IOException) {
            log.error("Failed to write .weauditdaylog file: $path", e)
            throw WeAuditIoException("Cannot write ${path.fileName}: ${e.message}", e)
        }
    }
}

/**
 * Thrown when a `.weaudit` or `.weauditdaylog` file cannot be read or written.
 * Callers (e.g. [WeAuditStore]) translate this into a user-visible notification.
 */
class WeAuditIoException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

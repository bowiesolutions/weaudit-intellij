package com.bowiesolutions.weaudit.toolwindow

import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.DayLog
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.model.SerializedData
import com.bowiesolutions.weaudit.store.IWeAuditStore

/**
 * In-memory test double for [WeAuditStore].
 *
 * [WeAuditStore] is a project-scoped IntelliJ service and cannot be instantiated
 * without a live platform. This class exposes only the data properties that
 * [WeAuditTreeModel] and [AuditedFilesTreeModel] read, letting the tree-model
 * tests run as plain JUnit 5 with no sandbox IDE.
 *
 * Add properties here as needed when additional store API is accessed from
 * Phase 3 tree-model code.
 */
class FakeWeAuditStore : IWeAuditStore {
    override var entries:               List<Entry>               = emptyList()
    override var auditedFiles:          List<AuditedFile>         = emptyList()
    override var partiallyAuditedFiles: List<PartiallyAuditedFile> = emptyList()
    override var allUsersData:          Map<String, SerializedData> = emptyMap()
    override var dayLog:                DayLog                    = DayLog.EMPTY

    // Change-listener stubs — not exercised in model-only tests
    override fun addChangeListener(listener: () -> Unit)    {}
    override fun removeChangeListener(listener: () -> Unit) {}

    fun toggleResolved(index: Int) {
        val e = entries.getOrNull(index) ?: return
        entries = entries.toMutableList().also { it[index] = e.copy(resolved = !e.resolved) }
    }

    fun removeEntry(index: Int) {
        entries = entries.toMutableList().also { it.removeAt(index) }
    }

    fun updatePartiallyAuditedFiles(files: List<PartiallyAuditedFile>) {
        partiallyAuditedFiles = files
    }

    fun unmarkFileAudited(path: String) {
        auditedFiles = auditedFiles.filter { it.path != path }
    }
}

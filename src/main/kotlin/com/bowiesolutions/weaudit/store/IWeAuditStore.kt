package com.bowiesolutions.weaudit.store

import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.DayLog
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.model.SerializedData

/**
 * Read-only + change-notification interface over [WeAuditStore].
 *
 * Extracted so that [WeAuditTreeModel] and [AuditedFilesTreeModel] can be
 * constructed with either the real platform-backed store or a plain in-memory
 * [FakeWeAuditStore] in unit tests.
 *
 * Only the data and listener API used by the tree models is declared here.
 * Mutation methods (addEntry, removeEntry, etc.) remain on [WeAuditStore]
 * directly — they are always called from actions that hold a real project
 * reference, never from the tree model itself.
 */
interface IWeAuditStore {
    val entries:               List<Entry>
    val auditedFiles:          List<AuditedFile>
    val partiallyAuditedFiles: List<PartiallyAuditedFile>
    val allUsersData:          Map<String, SerializedData>
    val dayLog:                DayLog

    fun addChangeListener(listener: () -> Unit)
    fun removeChangeListener(listener: () -> Unit)
}

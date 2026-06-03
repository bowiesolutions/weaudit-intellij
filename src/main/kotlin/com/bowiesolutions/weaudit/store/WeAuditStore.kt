package com.bowiesolutions.weaudit.store

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.DayLog
import com.bowiesolutions.weaudit.model.DayLogEntry
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.model.SerializedData
import com.bowiesolutions.weaudit.settings.WeAuditSettingsState
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.name

/**
 * Project-scoped service that is the single source of truth for all weAudit data
 * in the current session.
 *
 * Responsibilities:
 * 1. Locate `$username.weaudit` and `$username.weauditdaylog` in the appropriate
 *    directory (.vscode/ or .idea/ depending on which exists in the project root)
 * 2. Load data into an in-memory [SerializedData] + [DayLog] on project open
 *    (called from [WeAuditStartupActivity]).
 * 3. Expose mutating operations (add/remove/resolve entries, mark files audited)
 *    and persist after each mutation.
 * 4. Notify listeners (Phase 3 tree views) when data changes.
 * 5. Refresh the IntelliJ VFS after every write so file-change listeners fire.
 *
 * ## Threading
 * All public methods must be called on the **EDT** (or inside a write action when
 * the caller holds one).  Internal writes to disk are dispatched to a background
 * thread via [ApplicationManager.getApplication().executeOnPooledThread] to avoid
 * blocking the UI.  VFS refresh is then re-dispatched to the EDT after the write.
 *
 * ## Persistence
 * We use [java.nio] + [WeAuditSerializer] for the actual bytes, then call
 * [LocalFileSystem.refreshAndFindFileByPath] so IntelliJ's VFS picks up the
 * change.  This is the IntelliJ-idiomatic replacement for Node's `fs.writeFile`
 * followed by a `workspace.onDidChangeTextDocument` listener in the VS Code extension.
 *
 * ## Multi-user
 * Each auditor writes only their own `$username.weaudit` file.  Other users'
 * files are loaded read-only (Phase 3+).  The [loadAllUsersData] function
 * returns all `*.weaudit` files found in the weAudit storage directory
 *
 * Registered in `plugin.xml` as:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <projectService serviceImplementation="com.bowiesolutions.weaudit.store.WeAuditStore"/>
 * </extensions>
 * ```
 */
@Service(Service.Level.PROJECT)
class WeAuditStore(private val project: Project) : IWeAuditStore {

    private val log = logger<WeAuditStore>()

    // ── In-memory state ───────────────────────────────────────────────────────

    /** Current user's data (the only data we mutate). */
    private var _data: SerializedData = SerializedData.EMPTY

    /** Current user's daily log. */
    private var _dayLog: DayLog = DayLog.EMPTY

    /** All users' data keyed by username (read-only except for the current user). */
    internal val _allUsersData: MutableMap<String, SerializedData> = mutableMapOf()

    /** Change listeners — called on the EDT after any mutation. */
    private val changeListeners: MutableList<() -> Unit> = mutableListOf()

    // ── Computed paths ────────────────────────────────────────────────────────

    private val weAuditDir: Path get() = weAuditDir(project)
    private val username:  String get() = WeAuditSettingsState.getInstance().username

    val weAuditFilePath:   Path get() = weAuditDir / "$username.weaudit"
    val dayLogFilePath:    Path get() = weAuditDir / "$username.weauditdaylog"

    // ── Accessors (read-only views) ───────────────────────────────────────────

    override val entries:              List<Entry>               get() = _data.treeEntries
    override val auditedFiles:         List<AuditedFile>         get() = _data.auditedFiles
    override val partiallyAuditedFiles:List<PartiallyAuditedFile>get() = _data.partiallyAuditedFiles
    override val dayLog:               DayLog                    get() = _dayLog
    override val allUsersData:         Map<String, SerializedData>get() = _allUsersData.toMap()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Load this user's data from disk.  Called by [WeAuditStartupActivity].
     * Safe to call on a background thread (no EDT requirements for the load itself).
     *
     * After loading, fires [notifyChanged] on the EDT so the tool window can refresh.
     */
    fun loadFromDisk() {
        try {
            _data   = WeAuditSerializer.readWeAuditFile(weAuditFilePath)
            _dayLog = WeAuditSerializer.readDayLogFile(dayLogFilePath)
            log.info("weAudit: loaded ${_data.treeEntries.size} entries from $weAuditFilePath")
        } catch (e: WeAuditIoException) {
            log.warn("weAudit: load failed", e)
            showError("Could not load weAudit data: ${e.message}")
        }
        ApplicationManager.getApplication().invokeLater { notifyChanged() }
    }

    /**
     * Discover and load all co-auditors' `.weaudit` files in `.vscode/` or '.idea/'.
     * Their data is stored in [_allUsersData] and available read-only.
     * Called from [WeAuditStartupActivity] after [loadFromDisk].
     */
    fun loadAllUsersData() {
        val dir = weAuditDir.toFile()
        if (!dir.exists()) return

        dir.listFiles { f -> f.extension == "weaudit" && f.nameWithoutExtension != username }
            ?.forEach { file ->
                try {
                    val user = file.nameWithoutExtension
                    _allUsersData[user] = WeAuditSerializer.readWeAuditFile(file.toPath())
                    log.info("weAudit: loaded co-auditor data for '$user'")
                } catch (e: WeAuditIoException) {
                    log.warn("weAudit: failed to load ${file.name}", e)
                }
            }
    }

    /**
     * Reload a single co-auditor's data from a freshly-read [SerializedData].
     * Called by [WeAuditFileWatcher] when a co-auditor's .weaudit file changes.
     * Safe to call from a background thread — notifyChanged() dispatches to EDT.
     */
    fun reloadCoAuditorData(author: String, data: SerializedData) {
        _allUsersData[author] = data
        ApplicationManager.getApplication().invokeLater { notifyChanged() }
    }

    // ── Mutation operations ───────────────────────────────────────────────────

    /**
     * Add a new [Entry] and persist.
     * Must be called on the EDT.
     */
    fun addEntry(entry: Entry) {
        mutate { copy(treeEntries = treeEntries + entry) }
    }

    /**
     * Replace the entry at [index] with [updated] and persist.
     * Must be called on the EDT.
     */
    fun updateEntry(index: Int, updated: Entry) {
        mutate {
            copy(treeEntries = treeEntries.toMutableList().also { it[index] = updated })
        }
    }

    /**
     * Remove the entry at [index] and persist.
     */
    fun removeEntry(index: Int) {
        mutate {
            copy(treeEntries = treeEntries.toMutableList().also { it.removeAt(index) })
        }
    }

    /**
     * Toggle the resolved state of the entry at [index] and persist.
     * Resolved entries are hidden from the active list but kept in the JSON
     * (they appear in the Resolved panel — Phase 3).
     */
    fun toggleResolved(index: Int) {
        val entry = _data.treeEntries.getOrNull(index) ?: return
        updateEntry(index, entry.copy(resolved = !entry.resolved))
    }

    /**
     * Mark [path] (workspace-relative) as fully audited and persist.
     * Also records today's date in the day log.
     */
    fun markFileAudited(path: String) {
        if (_data.auditedFiles.none { it.path == path }) {
            mutate { copy(auditedFiles = auditedFiles + AuditedFile(path)) }
        }
        recordDayLogEntry(path)
    }

    /**
     * Remove the fully-audited mark from [path] and persist.
     */
    fun unmarkFileAudited(path: String) {
        mutate { copy(auditedFiles = auditedFiles.filter { it.path != path }) }
    }

    /**
     * Replace the full list of partially-audited files and persist.
     * The caller (Phase 2 editor layer) is responsible for constructing
     * the correct [PartiallyAuditedFile] objects.
     */
    fun setPartiallyAuditedFiles(files: List<PartiallyAuditedFile>) {
        mutate { copy(partiallyAuditedFiles = files) }
    }

    // ── Day log ───────────────────────────────────────────────────────────────

    /**
     * Append (or update) today's entry in the day log with [path].
     * The day log is an append-only ledger; we never remove entries.
     */
    private fun recordDayLogEntry(path: String) {
        val today = LocalDate.now().toString()
        val existing = _dayLog.entries.indexOfFirst { it.date == today }

        _dayLog = if (existing >= 0) {
            val old = _dayLog.entries[existing]
            val updated = old.copy(
                auditedFiles = (old.auditedFiles + path).distinct(),
                auditedLoc   = old.auditedLoc   // LOC update is a Phase 5 concern
            )
            DayLog(_dayLog.entries.toMutableList().also { it[existing] = updated })
        } else {
            DayLog(_dayLog.entries + DayLogEntry(date = today, auditedFiles = listOf(path)))
        }

        persistDayLogAsync()
    }

    // ── Change notification ───────────────────────────────────────────────────

    /** Register a callback that fires (on the EDT) when any data changes. */
    override fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    override fun removeChangeListener(listener: () -> Unit) {
        changeListeners -= listener
    }

    private fun notifyChanged() {
        changeListeners.forEach { it() }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Apply [transform] to [_data], replace it, then persist async and notify.
     * All callers must be on the EDT.
     */
    private fun mutate(transform: SerializedData.() -> SerializedData) {
        _data = _data.transform()
        persistAsync()
        notifyChanged()
    }

    /**
     * Write [_data] to disk on a pooled thread, then refresh the VFS on the EDT.
     * Errors are surfaced as notifications (not exceptions) so a failed write
     * doesn't crash the action.
     */
    private fun persistAsync() {
        val snapshot = _data
        val path = weAuditFilePath
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                WeAuditSerializer.writeWeAuditFile(path, snapshot)
                refreshVfs(path)
            } catch (e: WeAuditIoException) {
                log.error("weAudit: persist failed", e)
                ApplicationManager.getApplication().invokeLater {
                    showError("Failed to save weAudit data: ${e.message}")
                }
            }
        }
    }

    private fun persistDayLogAsync() {
        val snapshot = _dayLog
        val path = dayLogFilePath
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                WeAuditSerializer.writeDayLogFile(path, snapshot)
                refreshVfs(path)
            } catch (e: WeAuditIoException) {
                log.error("weAudit: day-log persist failed", e)
            }
        }
    }

    /**
     * Tell IntelliJ's VFS that [path] has changed on disk so that
     * [com.intellij.openapi.vfs.newvfs.BulkFileListener] callbacks fire
     * (needed in Phase 5 for the multi-user file-watching feature).
     *
     * Must be called from a background thread; schedules EDT work internally.
     */
    private fun refreshVfs(path: Path) {
        val vFile = LocalFileSystem.getInstance()
            .findFileByPath(path.toString()) ?: return
        vFile.refresh(/* asynchronous = */ true, /* recursive = */ false)
    }

    /**
     * Show a balloon error notification in the IDE.
     * Must be called on the EDT.
     */
    private fun showError(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(message, NotificationType.ERROR)
            ?.notify(project)
    }

    companion object {
        const val NOTIFICATION_GROUP_ID = "weAudit"

        /** Convenience accessor — get the store for [project]. */
        fun getInstance(project: Project): WeAuditStore = project.service()

        /**
         * Returns the directory where .weaudit files are stored for [project].
         *
         * Resolution order:
         * 1. `.vscode/`  — if it already exists (VS Code project or mixed team)
         * 2. `.idea/`    — if it already exists (pure IntelliJ project)
         * 3. `.vscode/`  — fallback for brand new projects (creates on first write,
         *                  maintaining VS Code compatibility by default)
         *
         * This means existing projects are never disrupted, and pure IntelliJ
         * projects get a more natural storage location without any configuration.
         */
        fun weAuditDir(project: Project): Path {
            val base = project.basePath
                ?: throw IllegalStateException("weAudit: project has no base path")
            val root = Path(base)

            val vscodeDir = root / ".vscode"
            val ideaDir   = root / ".idea"

            return when {
                vscodeDir.toFile().exists() -> vscodeDir   // VS Code or mixed team
                ideaDir.toFile().exists()   -> ideaDir     // pure IntelliJ project
                else                        -> ideaDir   // new project — default to IntelliJ layout
            }
        }

        /**
         * Find all `*.weaudit` files in the project's `.vscode/` or './idea' directory.
         * Used by [WeAuditStartupActivity] and file-watcher.
         */
        fun findWeAuditFiles(project: Project): List<VirtualFile> {
            val dirPath = weAuditDir(project).toString()
            val dirVf   = LocalFileSystem.getInstance()
                .findFileByPath(dirPath) ?: return emptyList()
            return dirVf.children?.filter { it.extension == "weaudit" } ?: emptyList()
        }
    }
}

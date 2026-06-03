package com.bowiesolutions.weaudit.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.Locale

/**
 * Application-level persistent settings for the weAudit plugin.
 *
 * This is the IntelliJ equivalent of VS Code's `workspace.getConfiguration('weaudit')`
 * calls scattered throughout `codeMarker.ts`.
 *
 * Settings are stored in the IDE's standard per-user storage
 * (`~/.config/JetBrains/<IDE>/options/weaudit.xml` on Linux,
 *  `~/Library/Application Support/JetBrains/<IDE>/options/weaudit.xml` on macOS).
 *
 * ## VS Code settings mapped here
 * | VS Code key                        | Field here                |
 * |------------------------------------|---------------------------|
 * | weaudit.username                   | [username]                |
 * | weaudit.findingColor               | [findingColor]            |
 * | weaudit.noteColor                  | [noteColor]               |
 * | weaudit.auditedFileColor           | [auditedFileColor]        |
 * | weaudit.otherFindingColor          | [otherFindingColor]       |
 * | weaudit.otherNoteColor             | [otherNoteColor]          |
 * | weaudit.gitOrg                     | [gitOrg]                  |
 * | weaudit.gitRemote                  | [gitRemote]               |
 * | weaudit.treeViewMode               | [treeViewMode]            |
 *
 * Registered in `plugin.xml`:
 * ```xml
 * <applicationService
 *     serviceImplementation="com.bowiesolutions.weaudit.settings.WeAuditSettingsState"/>
 * ```
 *
 * A corresponding [WeAuditSettingsConfigurable] (Phase 2+) will expose these
 * in **Settings → Tools → weAudit**.
 */
@Service(Service.Level.APP)
@State(
    name = "WeAuditSettings",
    storages = [Storage("weaudit.xml")]
)
class WeAuditSettingsState : PersistentStateComponent<WeAuditSettingsState> {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * The auditor's username — used as the `.weaudit` file name and the
     * `author` field on every new entry.
     *
     * Defaults to the OS user name so first-run works without configuration.
     */
    var username: String = System.getProperty("user.name", "auditor")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_-]"), "_")

    // ── Colors (hex strings, e.g. "#FFD6D6") ─────────────────────────────────

    /** Background color for own Findings. Default: light red. */
    var findingColor: String = "#FFD6D6"

    /** Background color for own Notes. Default: light blue. */
    var noteColor: String = "#D6E4FF"

    /** Background color for fully-audited files. Default: light green. */
    var auditedFileColor: String = "#D6FFD6"

    /** Background color for co-auditor Findings. Default: light orange. */
    var otherFindingColor: String = "#FFE8D6"

    /** Background color for co-auditor Notes. Default: light purple. */
    var otherNoteColor: String = "#EDD6FF"

    // ── GitHub / GitLab integration ───────────────────────────────────────────

    /** GitHub/GitLab organisation or user slug, e.g. `"trailofbits"`. */
    var gitOrg: String = ""

    /**
     * Remote URL prefix used for permalink generation, e.g.
     * `"https://github.com/trailofbits/vscode-weaudit"`.
     */
    var gitRemote: String = ""

    // ── UI preferences (not in .weaudit JSON) ────────────────────────────────

    /**
     * Persisted tree-view grouping mode.
     * `"List"` or `"GroupByFile"` — matches [com.bowiesolutions.weaudit.model.TreeViewMode].
     */
    var treeViewMode: String = "List"

    /** Whether findings highlighting is globally enabled. */
    var highlightingEnabled: Boolean = true

    // ── PersistentStateComponent boilerplate ──────────────────────────────────

    override fun getState(): WeAuditSettingsState = this

    override fun loadState(state: WeAuditSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): WeAuditSettingsState = service()
    }
}

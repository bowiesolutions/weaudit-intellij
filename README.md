# weAudit for IntelliJ

A collaborative code-review and security audit tool for IntelliJ-based IDEs.

IntelliJ port of [trailofbits/vscode-weaudit](https://github.com/trailofbits/vscode-weaudit),
preserving full `.weaudit` file format compatibility so VS Code and IntelliJ users can share
findings on the same engagement.

Licensed under **GPL-3.0**. Copyright of the original work remains with Trail of Bits.

---

## Features

- Bookmark code regions as **Findings** or **Notes** with colored editor highlights
- Structured finding metadata: severity, difficulty, type, description, exploit scenario, recommendation
- **Multi-region findings** spanning multiple files
- **Resolve** findings to archive them without deleting; restore at any time
- **Boundary editing** — adjust finding boundaries line-by-line with inlay hint controls or keyboard shortcuts
- Mark files as **fully reviewed** or mark specific regions as reviewed
- **Project View badges** — `!` (has findings), `✓` (fully reviewed), `~` (partially reviewed)
- **Daily audit log** tracking files reviewed per day
- Export findings to a **Markdown report** with optional GitHub/GitLab permalinks
- **Copy permanent links** to findings using the HEAD commit SHA
- Create pre-filled **GitHub/GitLab issues** from findings
- **Co-auditor support** — reloads teammates' `.weaudit` files automatically when changed on disk
- Configurable highlight colors, username, and git remote

---

## Requirements

| | Version |
|--|--|
| IntelliJ IDEA (or any JetBrains IDE) | 2025.1 or later |
| JDK (for building) | 21 |

---

## Quick start

```bash
# Build and launch a sandbox IDE with the plugin
./gradlew runIde

# Run all unit tests
./gradlew test

# Build the distributable plugin zip
./gradlew buildPlugin
```

---

## Keyboard shortcuts

All shortcuts match the VS Code extension defaults.
macOS uses `Cmd`; Linux/Windows use `Ctrl`.

| Action | Shortcut | VS Code command |
|--------|----------|-----------------|
| New Finding from Selection | Ctrl/Cmd+3 | `weaudit.addFinding` |
| New Note from Selection | Ctrl/Cmd+4 | `weaudit.addNote` |
| Delete Finding Under Cursor | Ctrl/Cmd+5 | `weaudit.deleteLocationUnderCursor` |
| Edit Finding Under Cursor | Ctrl/Cmd+6 | `weaudit.editEntryUnderCursor` |
| Resolve/Restore Under Cursor | Ctrl/Cmd+8 | — |
| Edit Finding Boundary | Ctrl/Cmd+B | `weaudit.editFindingBoundary` |
| Mark Current File As Reviewed | Ctrl/Cmd+7 | `weaudit.toggleAudited` |
| Mark Region As Reviewed | Ctrl/Cmd+Shift+7 | `weaudit.addPartiallyAudited` |
| Navigate to Next Reviewed Region | Ctrl/Cmd+0 | `weaudit.navigateToNextPartiallyAuditedRegion` |

**Boundary editing shortcuts** (only active when boundary editing is open):

| Action | Shortcut |
|--------|----------|
| Expand start boundary up | Ctrl/Cmd+Up |
| Shrink start boundary down | Ctrl/Cmd+Shift+Down |
| Expand end boundary down | Ctrl/Cmd+Down |
| Shrink end boundary up | Ctrl/Cmd+Shift+Up |
| Move boundary up | Alt+Up |
| Move boundary down | Alt+Down |
| Commit and exit | Escape |

All actions are also available via **Right-click → weAudit** in the editor
and via the **weAudit** menu in the menu bar.

Settings: **Settings → Tools → weAudit**

---

## Project structure

```
weaudit-intellij/
  build.gradle.kts
  settings.gradle.kts
  CHANGELOG.md

  src/main/kotlin/com/bowiesolutions/weaudit/
    WeAuditStartupActivity.kt         # loads .weaudit files on project open
    model/
      Types.kt                        # frozen JSON contract (port of types.ts)
    store/
      IWeAuditStore.kt                # interface used by tree models and tests
      WeAuditSerializer.kt            # JSON I/O for .weaudit and .weauditdaylog
      WeAuditStore.kt                 # project service: in-memory state + persistence
    settings/
      WeAuditSettingsState.kt         # PersistentStateComponent (username, colors, git)
      WeAuditSettingsConfigurable.kt  # Settings → Tools → weAudit UI
    editor/
      EditorHighlightManager.kt       # RangeHighlighter lifecycle
      EditorOpenListener.kt           # re-applies highlights on editor open
      ThemeChangeListener.kt          # recomputes colors on IDE theme change
    boundary/
      BoundaryEditSession.kt          # state for an active boundary edit
      BoundaryEditManager.kt          # project service owning the active session
      WeAuditBoundaryInlayProvider.kt # declarative inlay hints + click handler
    actions/
      WeAuditAction.kt                # base class with shared context helpers
      NewEntryActions.kt              # New Finding · New Note
      CursorEntryActions.kt           # Delete · Edit · Resolve under cursor
      AuditedFileActions.kt           # Toggle Audited · Mark Region · Navigate
      BoundaryEditActions.kt          # all boundary editing actions
      AddLocationToFindingAction.kt   # append region to existing finding
      CopyPermalinkAction.kt          # copy GitHub/GitLab permalink
      ExportMarkdownAction.kt         # export findings to Markdown
      NavigateToFindingAction.kt      # programmatic editor navigation
      ToggleHighlightingAction.kt     # show/hide all highlights
    integration/
      GitIntegration.kt               # git4idea wrapper (HEAD SHA, remote URL)
      PermalinkBuilder.kt             # permalink URL construction
      MarkdownExporter.kt             # Markdown report generation
      WeAuditFileWatcher.kt           # VFS listener for co-auditor file changes
    decorator/
      WeAuditProjectViewDecorator.kt  # Project View !, ✓, ~ badges
    panels/
      FindingDetailsPanel.kt          # Finding Details tab (Swing form)
      GitConfigPanel.kt               # Git Config tab + issue URL builder
    toolwindow/
      WeAuditToolWindowFactory.kt     # creates all tool window tabs
      panel/
        FindingsPanel.kt              # active findings tree
        ResolvedFindingsPanel.kt      # resolved findings tree
        AuditedFilesPanel.kt          # reviewed files tree
        DailyLogPanel.kt              # daily audit log table
      tree/
        WeAuditTreeNode.kt            # sealed node hierarchy
        WeAuditTreeModel.kt           # tree model (filter, grouping, viewMode)
        WeAuditTreeCellRenderer.kt    # cell renderer with icons and badges

  src/main/resources/
    META-INF/plugin.xml
    META-INF/pluginIcon.svg
    META-INF/git4idea-integration.xml
    icons/weaudit.svg

  src/test/kotlin/com/bowiesolutions/weaudit/
    boundary/  BoundaryEditSessionTest.kt
    editor/    PartialRegionMergeTest.kt
    integration/  MarkdownExporterTest.kt · PermalinkBuilderTest.kt
    model/     TypesTest.kt
    panels/    FindingDetailsPanelTest.kt · GitConfigPanelTest.kt
    store/     WeAuditSerializerTest.kt · FixtureRoundTripTest.kt
    toolwindow/ FakeWeAuditStore.kt · WeAuditTreeModelTest.kt
  src/test/resources/fixtures/alice.weaudit
```

---

## `.weaudit` file format

Files are stored at `$DIR/$USERNAME.weaudit` in the project root, where `$DIR` is
determined automatically:

| Situation | Directory used                    |
|-----------|-----------------------------------|
| `.vscode/` exists (VS Code or mixed team) | `.vscode/`                        |
| `.idea/` exists but no `.vscode/` (pure IntelliJ) | `.idea/`                          |
| Neither exists (new project) | `.idea/` (created on first write) |

This means existing VS Code projects and mixed teams are never disrupted, while
pure IntelliJ projects get a more natural storage location without any configuration.
The daily log is stored in the same directory as `$USERNAME.weauditdaylog`.
```json
{
  "treeEntries": [
    {
      "label": "Unchecked return value",
      "entryType": 0,
      "author": "alice",
      "locations": [
        { "path": "src/Token.sol", "startLine": 87, "endLine": 92, "label": "", "description": "" }
      ],
      "details": {
        "title": "Unchecked Return Value",
        "severity": "High",
        "difficulty": "Low",
        "type": "Data Validation",
        "description": "...",
        "exploit": "...",
        "recommendation": "..."
      },
      "resolved": false
    }
  ],
  "auditedFiles":          [{ "path": "src/Safe.sol" }],
  "partiallyAuditedFiles": [{ "path": "src/Token.sol", "regions": [...] }]
}
```

`entryType`: `0` = Finding, `1` = Note. Lines are **0-based**.

---

## Build notes

**IntelliJ 2025.1+ required** — the plugin targets `sinceBuild = "251"`. Earlier IDE
versions use an older Kotlin runtime that is missing coroutine classes introduced in
Kotlin 2.2.x (`SpillingKt` etc.).

**Kotlin serialization dependency scopes** — `kotlinx-serialization-json` is declared
`compileOnly` (platform provides it at runtime) and `testImplementation` (needed
explicitly in tests where the platform classloader is absent).

**`buildSearchableOptions` is disabled** — this task launches a headless IDE to index
settings for the search UI and is fragile in CI. Re-enable before Marketplace submission
if needed.

---

## License

GPL-3.0 — derivative of [trailofbits/vscode-weaudit](https://github.com/trailofbits/vscode-weaudit).  
Copyright of the original work remains with Trail of Bits.

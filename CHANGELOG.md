# Changelog

All notable changes to weAudit for IntelliJ will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.0.0] - 2026-06-01

### Added
- **Findings and Notes** — bookmark code regions as Findings or Notes with colored highlights;
  full VS Code `.weaudit` file format compatibility for mixed-IDE teams
- **Finding Details panel** — structured metadata per finding: severity, difficulty, type,
  description, exploit scenario, and recommendation
- **Resolved findings** — resolve findings to move them out of the active list while keeping
  them in the file; restore at any time
- **Multi-region findings** — attach multiple code locations to a single finding across files
- **Boundary editing** — adjust finding boundaries line-by-line using inlay hint controls
  or keyboard shortcuts (Ctrl/Cmd+B to enter; arrow keys to adjust; Escape to commit)
- **File review tracking** — mark files as fully reviewed (Ctrl/Cmd+7) or mark specific
  regions as reviewed (Ctrl/Cmd+Shift+7); navigate between regions (Ctrl/Cmd+0)
- **Project View badges** — `!` for files with findings, `✓` for fully reviewed files,
  `~` for partially reviewed files
- **Daily audit log** — automatic record of files reviewed per day
- **Markdown export** — export all findings to a structured Markdown report with optional
  permalinks (weAudit → Export Findings to Markdown)
- **GitHub/GitLab permalinks** — copy permanent links to specific lines using the HEAD
  commit SHA (weAudit → Copy Permalink)
- **GitHub/GitLab issue creation** — open a pre-filled issue from any finding via the
  Git Config panel
- **Co-auditor support** — automatically reloads teammates' `.weaudit` files when they
  change on disk (git pull, file sync)
- **Settings** — configurable username, highlight colors, and git remote (Settings → Tools → weAudit)
- **Tool window** with five panels: Findings, Resolved, Files, Finding Details, Git Config,
  Daily Log
- **Smart storage location** — `.weaudit` files are stored in `.vscode/` when that
  directory exists (preserving VS Code compatibility), or in `.idea/` for pure
  IntelliJ and new projects
### Keyboard shortcuts

| Action | macOS | Linux/Windows |
|--------|-------|---------------|
| New Finding | Cmd+3 | Ctrl+3 |
| New Note | Cmd+4 | Ctrl+4 |
| Delete Finding | Cmd+5 | Ctrl+5 |
| Edit Finding | Cmd+6 | Ctrl+6 |
| Mark File Reviewed | Cmd+7 | Ctrl+7 |
| Mark Region Reviewed | Cmd+Shift+7 | Ctrl+Shift+7 |
| Resolve/Restore | Cmd+8 | Ctrl+8 |
| Navigate Regions | Cmd+0 | Ctrl+0 |
| Edit Boundary | Cmd+B | Ctrl+B |

[Unreleased]: https://github.com/bowiesolutions/weaudit-intellij/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/bowiesolutions/weaudit-intellij/releases/tag/v1.0.0

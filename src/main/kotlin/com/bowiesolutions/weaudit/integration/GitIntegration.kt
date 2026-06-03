package com.bowiesolutions.weaudit.integration

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitUtil
import git4idea.repo.GitRepository

/**
 * Thin wrapper around IntelliJ's `git4idea` API.
 *
 * Provides the two pieces of information [PermalinkBuilder] needs:
 * - The HEAD commit SHA (for stable permalinks that survive branch changes)
 * - The configured remote URL (to construct the base of the permalink)
 *
 * ## Plugin dependency
 * This class uses `git4idea`, which is bundled with all JetBrains IDEs that
 * include Git support.  The dependency is declared as a soft dependency in
 * `plugin.xml` so the plugin still loads in IDEs without git support — the
 * permalink feature simply won't be available.
 *
 * ## Fallback
 * If no git repository is found for the project, both methods return null.
 * Callers should notify the user gracefully rather than crashing.
 */
object GitIntegration {

    private val log = logger<GitIntegration>()

    /**
     * Return the HEAD commit SHA for the git repository containing the project.
     * Returns null if there is no git repository or the HEAD is unborn.
     */
    fun headCommitSha(project: Project): String? = try {
        val repo = findRepo(project) ?: return null
        repo.currentRevision
    } catch (e: Exception) {
        log.warn("weAudit: could not get HEAD SHA", e)
        null
    }

    /**
     * Return the URL of the `origin` remote (or the first remote if `origin`
     * is not present).  Returns null if there are no remotes.
     *
     * The URL is returned as-is from git config — it may be HTTPS or SSH.
     * [PermalinkBuilder] handles normalisation.
     */
    fun remoteUrl(project: Project): String? = try {
        val repo = findRepo(project) ?: return null
        val remotes = repo.remotes
        val origin  = remotes.firstOrNull { it.name == "origin" }
            ?: remotes.firstOrNull()
            ?: return null
        origin.firstUrl
    } catch (e: Exception) {
        log.warn("weAudit: could not get remote URL", e)
        null
    }

    /**
     * Return all remotes for user selection (used in the Git Config panel
     * dropdown in a future UX improvement).
     */
    fun allRemoteUrls(project: Project): List<Pair<String, String>> = try {
        val repo = findRepo(project) ?: return emptyList()
        repo.remotes.flatMap { remote ->
            remote.urls.map { url -> remote.name to url }
        }
    } catch (e: Exception) {
        log.warn("weAudit: could not list remotes", e)
        emptyList()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun findRepo(project: Project): GitRepository? {
        val basePath = project.basePath ?: return null
        val baseVf   = LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return null
        return try {
            GitUtil.getRepositoryManager(project)
                .getRepositoryForFileQuick(baseVf)
        } catch (e: Exception) {
            log.warn("weAudit: GitUtil not available", e)
            null
        }
    }
}

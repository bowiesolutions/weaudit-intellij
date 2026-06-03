package com.bowiesolutions.weaudit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.bowiesolutions.weaudit.editor.EditorHighlightManager
import com.bowiesolutions.weaudit.store.WeAuditStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs once after the project has fully opened.
 *
 * Phase 1: loads .weaudit data.
 * Phase 2: after loading, re-applies highlights to all currently-open editors,
 *          and wires the store change listener so any subsequent mutation
 *          (add/delete/resolve entry) immediately refreshes the editor decorations.
 */
class WeAuditStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val store   = WeAuditStore.getInstance(project)
        val manager = EditorHighlightManager.getInstance(project)

        // Load data on the IO dispatcher — disk reads off EDT.
        withContext(Dispatchers.IO) {
            store.loadFromDisk()
            store.loadAllUsersData()
        }

        // After loading, re-apply highlights on the EDT.
        // Also wire a change listener so every future store mutation
        // triggers an immediate highlight refresh — replacing the VS Code
        // pattern of calling setDecorations() at the end of every command.
        ApplicationManager.getApplication().invokeLater {
            store.addChangeListener {
                // changeListener is already called on EDT by WeAuditStore.mutate.
                manager.reapplyAll()
            }
            manager.reapplyAll()
        }
    }
}


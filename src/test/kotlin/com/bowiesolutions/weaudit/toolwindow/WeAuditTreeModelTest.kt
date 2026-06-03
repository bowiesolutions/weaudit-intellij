package com.bowiesolutions.weaudit.toolwindow

import com.bowiesolutions.weaudit.model.AuditedFile
import com.bowiesolutions.weaudit.model.Entry
import com.bowiesolutions.weaudit.model.EntryType
import com.bowiesolutions.weaudit.model.Location
import com.bowiesolutions.weaudit.model.PartiallyAuditedFile
import com.bowiesolutions.weaudit.model.SerializedData
import com.bowiesolutions.weaudit.model.TreeViewMode
import com.bowiesolutions.weaudit.toolwindow.tree.AuditedFilesTreeModel
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeModel
import com.bowiesolutions.weaudit.toolwindow.tree.WeAuditTreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Unit tests for [WeAuditTreeModel] and [AuditedFilesTreeModel].
 *
 * Uses [FakeWeAuditStore] to avoid any IntelliJ platform dependency.
 * All tree operations are pure Swing [DefaultTreeModel] mutations.
 */
class WeAuditTreeModelTest {

    private lateinit var fakeStore: FakeWeAuditStore

    @BeforeEach
    fun setup() {
        fakeStore = FakeWeAuditStore()
    }

    // ── List mode — basic rendering ───────────────────────────────────────────

    @Test
    fun `list mode shows active entries as direct children of root`() {
        fakeStore.entries = listOf(
            entry("Finding A", EntryType.Finding, "src/Foo.kt", 1, 5, resolved = false),
            entry("Note B",    EntryType.Note,    "src/Bar.kt", 10, 10, resolved = false),
        )
        val model = WeAuditTreeModel(fakeStore, showResolved = false)
        model.rebuild()

        assertEquals(2, rootChildCount(model))
        val first = rootChildNode(model, 0)
        assertInstanceOf(WeAuditTreeNode.EntryNode::class.java, first)
        assertEquals("Finding A", (first as WeAuditTreeNode.EntryNode).entry.label)
    }

    @Test
    fun `resolved panel shows only resolved entries`() {
        fakeStore.entries = listOf(
            entry("Active",   resolved = false),
            entry("Resolved", resolved = true),
        )
        val activeModel   = WeAuditTreeModel(fakeStore, showResolved = false)
        val resolvedModel = WeAuditTreeModel(fakeStore, showResolved = true)
        activeModel.rebuild()
        resolvedModel.rebuild()

        assertEquals(1, rootChildCount(activeModel))
        assertEquals("Active", (rootChildNode(activeModel, 0) as WeAuditTreeNode.EntryNode).entry.label)

        assertEquals(1, rootChildCount(resolvedModel))
        assertEquals("Resolved", (rootChildNode(resolvedModel, 0) as WeAuditTreeNode.EntryNode).entry.label)
    }

    @Test
    fun `empty store produces empty tree`() {
        fakeStore.entries = emptyList()
        val model = WeAuditTreeModel(fakeStore, showResolved = false)
        model.rebuild()
        assertEquals(0, rootChildCount(model))
    }

    // ── Multi-region entries ──────────────────────────────────────────────────

    @Test
    fun `single-location entry has no location children`() {
        fakeStore.entries = listOf(entry("Single", locations = listOf(loc("a.kt", 0, 5))))
        val model = WeAuditTreeModel(fakeStore)
        model.rebuild()

        val entryMutableNode = (model.root as DefaultMutableTreeNode).getChildAt(0)
            as DefaultMutableTreeNode
        assertEquals(0, entryMutableNode.childCount)
    }

    @Test
    fun `multi-location entry has LocationNode children`() {
        fakeStore.entries = listOf(
            entry("Multi", locations = listOf(loc("a.kt", 0, 5), loc("b.kt", 10, 15)))
        )
        val model = WeAuditTreeModel(fakeStore)
        model.rebuild()

        val entryMutableNode = (model.root as DefaultMutableTreeNode).getChildAt(0)
            as DefaultMutableTreeNode
        assertEquals(2, entryMutableNode.childCount)

        val locNode = (entryMutableNode.getChildAt(0) as DefaultMutableTreeNode).userObject
        assertInstanceOf(WeAuditTreeNode.LocationNode::class.java, locNode)
        assertEquals("a.kt", (locNode as WeAuditTreeNode.LocationNode).location.path)
    }

    // ── GroupByFile mode ──────────────────────────────────────────────────────

    @Test
    fun `GroupByFile groups entries by primary location file`() {
        fakeStore.entries = listOf(
            entry("F1", path = "src/Vault.kt"),
            entry("F2", path = "src/Vault.kt"),
            entry("F3", path = "src/Token.kt"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.viewMode = TreeViewMode.GroupByFile  // also calls rebuild()

        assertEquals(2, rootChildCount(model), "Expected 2 file groups")

        val group0 = rootChildNode(model, 0)
        assertInstanceOf(WeAuditTreeNode.FileGroupNode::class.java, group0)
        assertEquals("src/Vault.kt", (group0 as WeAuditTreeNode.FileGroupNode).path)

        val vaultGroup = (model.root as DefaultMutableTreeNode).getChildAt(0)
            as DefaultMutableTreeNode
        assertEquals(2, vaultGroup.childCount, "Vault group should have 2 entries")

        val group1 = rootChildNode(model, 1)
        assertEquals("src/Token.kt", (group1 as WeAuditTreeNode.FileGroupNode).path)
    }

    @Test
    fun `switching from GroupByFile to List rebuilds correctly`() {
        fakeStore.entries = listOf(
            entry("A", path = "foo.kt"),
            entry("B", path = "bar.kt"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.viewMode = TreeViewMode.GroupByFile
        assertEquals(2, rootChildCount(model)) // 2 groups

        model.viewMode = TreeViewMode.List
        assertEquals(2, rootChildCount(model)) // 2 entry nodes, no groups
        assertInstanceOf(WeAuditTreeNode.EntryNode::class.java, rootChildNode(model, 0))
    }

    // ── Search / filter ───────────────────────────────────────────────────────

    @Test
    fun `filterText matches on label case-insensitively`() {
        fakeStore.entries = listOf(
            entry("Reentrancy bug"),
            entry("Integer overflow"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.filterText = "reentran"
        assertEquals(1, rootChildCount(model))
        assertEquals("Reentrancy bug",
            (rootChildNode(model, 0) as WeAuditTreeNode.EntryNode).entry.label)
    }

    @Test
    fun `filterText matches on file path`() {
        fakeStore.entries = listOf(
            entry("F1", path = "src/Vault.kt"),
            entry("F2", path = "src/Token.kt"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.filterText = "vault"
        assertEquals(1, rootChildCount(model))
    }

    @Test
    fun `filterText matches on author`() {
        fakeStore.entries = listOf(
            entry("F1", author = "alice"),
            entry("F2", author = "bob"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.filterText = "bob"
        assertEquals(1, rootChildCount(model))
        assertEquals("bob", (rootChildNode(model, 0) as WeAuditTreeNode.EntryNode).author)
    }

    @Test
    fun `empty filterText shows all entries`() {
        fakeStore.entries = listOf(entry("A"), entry("B"), entry("C"))
        val model = WeAuditTreeModel(fakeStore)
        model.filterText = "x" // no matches
        assertEquals(0, rootChildCount(model))
        model.filterText = ""  // clear filter
        assertEquals(3, rootChildCount(model))
    }

    // ── Author visibility ─────────────────────────────────────────────────────

    @Test
    fun `hideAuthor removes that author's entries from tree`() {
        fakeStore.entries = listOf(
            entry("Alice's finding", author = "alice"),
            entry("Bob's finding",   author = "bob"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.rebuild()
        assertEquals(2, rootChildCount(model))

        model.hideAuthor("alice")
        assertEquals(1, rootChildCount(model))
        assertEquals("bob", (rootChildNode(model, 0) as WeAuditTreeNode.EntryNode).author)
    }

    @Test
    fun `showAuthor after hide restores entries`() {
        fakeStore.entries = listOf(entry("X", author = "carol"))
        val model = WeAuditTreeModel(fakeStore)
        model.hideAuthor("carol")
        assertEquals(0, rootChildCount(model))
        model.showAuthor("carol")
        assertEquals(1, rootChildCount(model))
    }

    @Test
    fun `toggleAuthor alternates hidden state`() {
        val model = WeAuditTreeModel(fakeStore)
        assertFalse(model.isHidden("dave"))
        model.toggleAuthor("dave")
        assertTrue(model.isHidden("dave"))
        model.toggleAuthor("dave")
        assertFalse(model.isHidden("dave"))
    }

    // ── storeIndex correctness ────────────────────────────────────────────────

    @Test
    fun `storeIndex matches position in store entries list`() {
        fakeStore.entries = listOf(
            entry("E0"),
            entry("E1"),
            entry("E2"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.rebuild()

        for (i in 0..2) {
            val node = rootChildNode(model, i) as WeAuditTreeNode.EntryNode
            assertEquals(i, node.storeIndex, "Entry at position $i should have storeIndex $i")
        }
    }

    @Test
    fun `storeIndex is correct after filter skips entries`() {
        fakeStore.entries = listOf(
            entry("Alpha"),
            entry("Beta"),
            entry("Gamma"),
        )
        val model = WeAuditTreeModel(fakeStore)
        model.filterText = "gamma"
        assertEquals(1, rootChildCount(model))
        // "Gamma" is at index 2 in the store
        val node = rootChildNode(model, 0) as WeAuditTreeNode.EntryNode
        assertEquals(2, node.storeIndex)
    }

    // ── AuditedFilesTreeModel ─────────────────────────────────────────────────

    @Test
    fun `audited files model renders fully-audited files as leaf nodes`() {
        fakeStore.auditedFiles = listOf(
            AuditedFile("src/Foo.kt"),
            AuditedFile("src/Bar.kt"),
        )
        val model = AuditedFilesTreeModel(fakeStore)
        model.rebuild()
        assertEquals(2, rootChildCount(model))
        assertInstanceOf(WeAuditTreeNode.AuditedFileNode::class.java, rootChildNode(model, 0))
    }

    @Test
    fun `audited files model renders partially-audited files with region children`() {
        fakeStore.partiallyAuditedFiles = listOf(
            PartiallyAuditedFile("src/Big.kt", listOf(
                loc("src/Big.kt", 0,  50),
                loc("src/Big.kt", 100, 120),
            ))
        )
        val model = AuditedFilesTreeModel(fakeStore)
        model.rebuild()
        assertEquals(1, rootChildCount(model))

        val partialMutableNode = (model.root as DefaultMutableTreeNode).getChildAt(0)
            as DefaultMutableTreeNode
        assertInstanceOf(WeAuditTreeNode.PartialFileNode::class.java,
            model.nodeAt(partialMutableNode))
        assertEquals(2, partialMutableNode.childCount)

        val regionNode = model.nodeAt(
            partialMutableNode.getChildAt(0) as DefaultMutableTreeNode
        )
        assertInstanceOf(WeAuditTreeNode.PartialRegionNode::class.java, regionNode)
        assertEquals(0, (regionNode as WeAuditTreeNode.PartialRegionNode).location.startLine)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rootChildCount(model: javax.swing.tree.DefaultTreeModel): Int =
        (model.root as DefaultMutableTreeNode).childCount

    private fun rootChildNode(model: javax.swing.tree.DefaultTreeModel, index: Int): WeAuditTreeNode? {
        val root  = model.root as DefaultMutableTreeNode
        val child = root.getChildAt(index) as DefaultMutableTreeNode
        return child.userObject as? WeAuditTreeNode
    }

    private fun entry(
        label:     String,
        type:      EntryType = EntryType.Finding,
        path:      String    = "src/Fake.kt",
        startLine: Int       = 0,
        endLine:   Int       = 1,
        resolved:  Boolean   = false,
        author:    String    = "alice",
        locations: List<Location>? = null,
    ) = Entry(
        label     = label,
        entryType = type.jsonValue,
        author    = author,
        locations = locations ?: listOf(loc(path, startLine, endLine)),
        resolved  = resolved,
    )

    private fun loc(path: String, start: Int, end: Int) = Location(path, start, end)
}

package com.lucent.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectoryBrowseTest {

    private fun tree(): File {
        val root = File(System.getProperty("java.io.tmpdir"), "lucent-browse-${System.nanoTime()}")
        File(root, "Beta").mkdirs()
        File(root, "alpha").mkdirs()
        File(root, ".hidden").mkdirs()
        File(root, "notes.txt").writeText("x")
        return root
    }

    @Test
    fun normalizeKeepsRootsAndDropsTrailingSeparators() {
        assertEquals("/sdcard/Documents", DirectoryBrowse.normalize("/sdcard/Documents/"))
        assertEquals("/", DirectoryBrowse.normalize("/"))
        assertEquals("/sdcard/Documents", DirectoryBrowse.normalize("  /sdcard/Documents  "))
        assertEquals("", DirectoryBrowse.normalize("   "))
    }

    @Test
    fun parentWalksUpAndStopsAtTheRoot() {
        val root = tree()
        val nested = File(root, "alpha").path
        val up = DirectoryBrowse.parent(nested).orEmpty()
        assertEquals(DirectoryBrowse.normalize(root.path), DirectoryBrowse.normalize(up))
        assertNull(DirectoryBrowse.parent("/"))
        assertTrue(DirectoryBrowse.isRoot("/"))
    }

    @Test
    fun listKeepsOnlyFoldersAndSortsThemByName() {
        val root = tree()
        val listed = DirectoryBrowse.list(root.path, showHidden = false).orEmpty()
        assertEquals(listOf("alpha", "Beta"), listed.map { it.name })
        assertEquals(File(root, "alpha").path, listed.first().path)
    }

    @Test
    fun listRevealsHiddenFoldersWhenAsked() {
        val root = tree()
        val listed = DirectoryBrowse.list(root.path, showHidden = true).orEmpty()
        assertEquals(listOf(".hidden", "alpha", "Beta"), listed.map { it.name })
    }

    @Test
    fun listReportsUnreadableTargetsInsteadOfPretendingTheyAreEmpty() {
        val root = tree()
        assertNull(DirectoryBrowse.list(File(root, "notes.txt").path, showHidden = false))
        assertNull(DirectoryBrowse.list(File(root, "missing").path, showHidden = false))
    }

    @Test
    fun createMakesTheFolderAndRefusesBlankNames() {
        val root = tree()
        val made = DirectoryBrowse.create(root.path, "made-here")
        assertEquals(File(root, "made-here").path, made)
        assertTrue(File(made!!).isDirectory)
        assertEquals(made, DirectoryBrowse.create(root.path, "made-here"))
        assertNull(DirectoryBrowse.create(root.path, "   "))
    }

    @Test
    fun startingPointPrefersARealFolder() {
        val root = tree()
        assertEquals(root.path, DirectoryBrowse.startingPoint(root.path))
        assertTrue(File(DirectoryBrowse.startingPoint(File(root, "nowhere").path)).isDirectory)
    }
}

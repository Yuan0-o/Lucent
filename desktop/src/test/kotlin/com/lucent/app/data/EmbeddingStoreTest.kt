package com.lucent.app.data

import android.content.Context
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class EmbeddingStoreTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-embedding-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private suspend fun use(dir: File, block: suspend () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        DataKeys.resetCacheForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
            DataKeys.resetCacheForTesting()
        }
    }

    @Test
    fun encodeDecodeRoundTripsExactly() {
        val original = floatArrayOf(0.5f, -1.25f, 0f, 3.140001f, -0.0001f)
        val decoded = EmbeddingStore.decode(EmbeddingStore.encode(original))
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun decodeRejectsAByteLengthThatIsNotAMultipleOfFour() {
        var threw = false
        try {
            EmbeddingStore.decode(byteArrayOf(1, 2, 3))
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "expected decode to reject a non-multiple-of-4 byte length")
    }

    @Test
    fun searchRanksByCosineSimilarityNotInsertionOrder() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val noteA = db.noteDao().insert(Note(title = "A", body = "far from the query"))
            val noteB = db.noteDao().insert(Note(title = "B", body = "close to the query"))
            val noteC = db.noteDao().insert(Note(title = "C", body = "identical to the query"))

            EmbeddingStore.store(context, noteA, "test-model", floatArrayOf(1f, 0f, 0f))
            EmbeddingStore.store(context, noteB, "test-model", floatArrayOf(0.9f, 0.1f, 0f))
            EmbeddingStore.store(context, noteC, "test-model", floatArrayOf(0f, 1f, 0f))

            val query = floatArrayOf(0f, 1f, 0f)
            val results = EmbeddingStore.search(context, query, "test-model", topK = 3)

            assertEquals(listOf(noteC, noteB, noteA), results.map { it.noteId })
            assertTrue(results[0].similarity > results[1].similarity)
            assertTrue(results[1].similarity > results[2].similarity)
            assertTrue(kotlin.math.abs(results[0].similarity - 1f) < 0.0001f)
        }
    }

    @Test
    fun topKLimitsResultCount() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            repeat(5) { i ->
                val id = db.noteDao().insert(Note(title = "N$i", body = "note $i"))
                EmbeddingStore.store(context, id, "test-model", floatArrayOf(i.toFloat(), 1f, 0f))
            }
            val results = EmbeddingStore.search(context, floatArrayOf(0f, 1f, 0f), "test-model", topK = 2)
            assertEquals(2, results.size)
        }
    }

    @Test
    fun differentModelsCoexistForTheSameNoteWithoutColliding() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val noteId = db.noteDao().insert(Note(title = "N", body = "body"))

            EmbeddingStore.store(context, noteId, "local-model", floatArrayOf(1f, 0f))
            EmbeddingStore.store(context, noteId, "cloud-model", floatArrayOf(0f, 1f))

            val localHit = EmbeddingStore.search(context, floatArrayOf(1f, 0f), "local-model", topK = 5)
            val cloudHit = EmbeddingStore.search(context, floatArrayOf(0f, 1f), "cloud-model", topK = 5)
            assertEquals(listOf(noteId), localHit.map { it.noteId })
            assertEquals(listOf(noteId), cloudHit.map { it.noteId })
            assertTrue(EmbeddingStore.search(context, floatArrayOf(1f, 0f), "another-model", topK = 5).isEmpty())
        }
    }

    @Test
    fun deletingANoteCascadesToItsEmbeddings() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val noteId = db.noteDao().insert(Note(title = "N", body = "body"))
            EmbeddingStore.store(context, noteId, "test-model", floatArrayOf(1f, 0f))

            assertEquals(1, db.noteEmbeddingDao().getForNote(noteId).size)
            db.noteDao().delete(Note(id = noteId, title = "", body = ""))
            assertTrue(
                db.noteEmbeddingDao().getForNote(noteId).isEmpty(),
                "expected the AFTER-DELETE trigger (MIGRATION_18_19) to remove the note's embeddings"
            )
        }
    }

    @Test
    fun clearAllRemovesEveryVectorAcrossEveryModel() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val noteId = db.noteDao().insert(Note(title = "N", body = "body"))
            EmbeddingStore.store(context, noteId, "local-model", floatArrayOf(1f, 0f))
            EmbeddingStore.store(context, noteId, "cloud-model", floatArrayOf(0f, 1f))

            EmbeddingStore.clearAll(context)

            assertTrue(db.noteEmbeddingDao().getForNote(noteId).isEmpty())
        }
    }
}

package com.lucent.app.data

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Storage and brute-force similarity search for note embedding vectors.
 *
 * **This file is the data layer only.** Nothing here calls an embedding model — not the local
 * llama.cpp bridge, not a cloud provider's endpoint. Generating a vector and handing it to [store]
 * is a separate, later piece of work; see the class doc on `NoteEmbedding` (Android) /
 * `MIGRATION_18_19` for why this ordering was chosen (schema and storage proven first, on data no
 * generation path depends on, before anything calls out to a model). Two sources will eventually
 * feed this, user's choice, default local: the on-device llama.cpp bridge, or the configured cloud
 * provider's embedding endpoint — the latter means note text leaves the device, which is why it
 * must default off and be explicit opt-in wherever that setting lands.
 *
 * **Design choices, and why:**
 * - **Brute-force cosine, no vector index.** At personal-notebook scale (thousands, not millions,
 *   of notes) a linear scan comparing one query vector against every stored vector for the same
 *   model is milliseconds of work — see [search]. An index (e.g. an approximate-nearest-neighbour
 *   structure) would be premature complexity for a workload this small, and this project has a
 *   standing rule against exactly that: `SearchQuery`'s own header tells the same story about why
 *   FTS5 was tried and abandoned for text search — reach for the simple, provably-correct approach
 *   first, and only reach for more machinery once real numbers say it's needed.
 * - **Vectors stored as raw bytes, not JSON/text.** A `FloatArray` becomes a `ByteArray` (4 bytes
 *   per dimension, little-endian — see [encode]/[decode]) rather than a serialised number list.
 *   For a typical embedding (hundreds to low thousands of dimensions) this is both smaller on disk
 *   and avoids float-to-string-to-float round-trip precision loss; it costs nothing in return since
 *   nothing needs to read a vector without going through this file first.
 * - **Excluded from `.lcb` backups, deliberately.** A vector is fully reconstructible from the
 *   note's own text (title + body), which the backup *does* carry — so carrying the vectors too
 *   would only be a size cost with no corresponding safety benefit, and a stale vector restored
 *   onto a device running a different embedding model would be actively wrong to reuse. See the
 *   `note_embeddings` table's own schema-level doc comment for the full reasoning, and
 *   `BackupManifestBuilder`/`BackupImport` — neither should ever be taught this table's name.
 */
object EmbeddingStore {

    /** One search hit: which note, and how similar its vector was to the query (1.0 = identical direction). */
    data class ScoredNote(val noteId: Long, val similarity: Float)

    /**
     * `vec` as bytes: 4 bytes per dimension, little-endian, no header — [dim] (stored alongside,
     * see [NoteEmbedding]) is what tells a reader how many floats to expect back out of [decode].
     */
    fun encode(vec: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vec.forEach { buf.putFloat(it) }
        return buf.array()
    }

    /** The inverse of [encode]. Throws if [bytes]'s length isn't a multiple of 4 — a corrupt row. */
    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "embedding byte length ${bytes.size} is not a multiple of 4" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }

    /**
     * Save (or replace) [noteId]'s vector for [model]. [updatedAt] defaults to now; callers that
     * are backfilling historical data can pass the note's own `updatedAt` instead, so a freshly
     * computed vector for an old, unedited note doesn't read as more recent than it is.
     */
    suspend fun store(
        context: Context,
        noteId: Long,
        model: String,
        vec: FloatArray,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val db = AppDatabase.getInstance(context)
        db.noteEmbeddingDao().upsert(
            NoteEmbedding(noteId = noteId, model = model, dim = vec.size, vec = encode(vec), updatedAt = updatedAt)
        )
    }

    /**
     * The [topK] notes whose stored [model] vector is most similar to [queryVec], best first.
     * Notes with no stored vector for [model] are simply absent from the result — this function
     * does not know or care whether that's because they were never embedded or the model changed;
     * that policy question belongs to whatever calls this, not to the search itself.
     *
     * A malformed stored vector (wrong byte length, or a `dim` that doesn't match the decoded
     * length) is skipped rather than allowed to throw and fail the whole search — one corrupt row
     * should cost that one note, not every note's recall.
     */
    suspend fun search(context: Context, queryVec: FloatArray, model: String, topK: Int): List<ScoredNote> {
        if (topK <= 0 || queryVec.isEmpty()) return emptyList()
        val db = AppDatabase.getInstance(context)
        val queryNorm = norm(queryVec)
        if (queryNorm == 0f) return emptyList()

        return db.noteEmbeddingDao().getForModel(model)
            .mapNotNull { row ->
                if (row.vec.size != row.dim * 4) return@mapNotNull null
                val vec = try {
                    decode(row.vec)
                } catch (_: IllegalArgumentException) {
                    return@mapNotNull null
                }
                if (vec.size != queryVec.size) return@mapNotNull null
                val similarity = cosineSimilarity(queryVec, vec, queryNorm)
                ScoredNote(row.noteId, similarity)
            }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    /** Remove [noteId]'s vector for [model] — e.g. after a manual re-embed, before [store]-ing the new one. */
    suspend fun delete(context: Context, noteId: Long, model: String) {
        AppDatabase.getInstance(context).noteEmbeddingDao().delete(noteId, model)
    }

    /** Drop every vector for [model] — for when a model is switched away from or retired. */
    suspend fun deleteAllForModel(context: Context, model: String) {
        AppDatabase.getInstance(context).noteEmbeddingDao().deleteAllForModel(model)
    }

    /** Drop every stored vector, for every model (used when wiping all data — see AppWipe). */
    suspend fun clearAll(context: Context) {
        AppDatabase.getInstance(context).noteEmbeddingDao().clearAll()
    }

    // ---------------------------------------------------------------------------------------

    private fun norm(v: FloatArray): Float {
        var sumSq = 0.0
        for (x in v) sumSq += x.toDouble() * x.toDouble()
        return sqrt(sumSq).toFloat()
    }

    /** Cosine similarity between [a] and [b], where [normA] is [a]'s precomputed norm (the query
     *  vector's norm is the same for every comparison in one [search] call, so it's computed once
     *  by the caller rather than once per row). Returns 0f if [b] is a zero vector. */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray, normA: Float): Float {
        var dot = 0.0
        var sumSqB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            sumSqB += b[i].toDouble() * b[i].toDouble()
        }
        val normB = sqrt(sumSqB).toFloat()
        if (normB == 0f) return 0f
        return (dot / (normA.toDouble() * normB.toDouble())).toFloat()
    }
}

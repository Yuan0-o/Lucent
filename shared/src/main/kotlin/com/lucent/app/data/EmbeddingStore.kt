package com.lucent.app.data

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object EmbeddingStore {

    data class ScoredNote(val noteId: Long, val similarity: Float)

    fun encode(vec: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vec.forEach { buf.putFloat(it) }
        return buf.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "embedding byte length ${bytes.size} is not a multiple of 4" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }

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

    suspend fun delete(context: Context, noteId: Long, model: String) {
        AppDatabase.getInstance(context).noteEmbeddingDao().delete(noteId, model)
    }

    suspend fun deleteAllForModel(context: Context, model: String) {
        AppDatabase.getInstance(context).noteEmbeddingDao().deleteAllForModel(model)
    }

    suspend fun clearAll(context: Context) {
        AppDatabase.getInstance(context).noteEmbeddingDao().clearAll()
    }


    private fun norm(v: FloatArray): Float {
        var sumSq = 0.0
        for (x in v) sumSq += x.toDouble() * x.toDouble()
        return sqrt(sumSq).toFloat()
    }

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

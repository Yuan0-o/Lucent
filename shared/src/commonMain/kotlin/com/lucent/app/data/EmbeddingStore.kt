package com.lucent.app.data

import com.lucent.app.data.createAppDatabase

import com.lucent.app.platform.PlatformContext
import kotlin.math.sqrt

object EmbeddingStore {

    data class ScoredNote(val noteId: Long, val similarity: Float)

    fun encode(vec: FloatArray): ByteArray {
        val bytes = ByteArray(vec.size * 4)
        for ((i, f) in vec.withIndex()) {
            val bits = f.toBits()
            bytes[i * 4] = (bits and 0xFF).toByte()
            bytes[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        return bytes
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "embedding byte length ${bytes.size} is not a multiple of 4" }
        return FloatArray(bytes.size / 4) { i ->
            val bits = (bytes[i * 4].toInt() and 0xFF) or
                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
            Float.fromBits(bits)
        }
    }

    suspend fun store(
        context: PlatformContext,
        noteId: Long,
        model: String,
        vec: FloatArray,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val db = createAppDatabase(context)
        db.noteEmbeddingDao.upsert(
            NoteEmbedding(noteId = noteId, model = model, dim = vec.size, vec = encode(vec), updatedAt = updatedAt)
        )
    }

    suspend fun search(context: PlatformContext, queryVec: FloatArray, model: String, topK: Int): List<ScoredNote> {
        if (topK <= 0 || queryVec.isEmpty()) return emptyList()
        val db = createAppDatabase(context)
        val queryNorm = norm(queryVec)
        if (queryNorm == 0f) return emptyList()

        return db.noteEmbeddingDao.getForModel(model)
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

    suspend fun delete(context: PlatformContext, noteId: Long, model: String) {
        createAppDatabase(context).noteEmbeddingDao.delete(noteId, model)
    }

    suspend fun deleteAllForModel(context: PlatformContext, model: String) {
        createAppDatabase(context).noteEmbeddingDao.deleteAllForModel(model)
    }

    suspend fun clearAll(context: PlatformContext) {
        createAppDatabase(context).noteEmbeddingDao.clearAll()
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

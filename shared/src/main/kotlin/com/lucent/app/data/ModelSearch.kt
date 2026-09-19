package com.lucent.app.data

object ModelSearch {

    fun rankModels(candidates: List<String>, query: String): List<String> {
        val q = normalise(query)
        if (q.isEmpty()) return candidates
        val pairs = q.windowed(2).toSet()
        return candidates
            .map { c -> c to score(normalise(c), q, pairs) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun score(name: String, q: String, qPairs: Set<String>): Int {
        if (name.isEmpty()) return 0
        if (name == q) return 100
        if (name.startsWith(q)) return 60
        if (name.contains(q)) return 35
        var qi = 0
        for (ch in name) {
            if (qi < q.length && ch == q[qi]) qi++
        }
        if (qi < q.length) return 0
        val namePairs = name.windowed(2).toSet()
        return 18 + minOf(4, namePairs.intersect(qPairs).size)
    }

    private fun normalise(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }
}

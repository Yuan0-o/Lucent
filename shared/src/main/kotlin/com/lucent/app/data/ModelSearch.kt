package com.lucent.app.data

/**
 * Fuzzy ("does-not-need-to-be-exact") model-name search, shared by every surface that lets the
 * user type to find a model — the Settings → API model list (R3 report) and, later, any list the
 * chat switcher shows.
 *
 * ### What "fuzzy" means here
 *
 * The user asked for a search that does NOT require every character, nor the exact order, to
 * match — typing `gpt4` should find `gpt-4o`, `GPT-4 Turbo`, and `deepseek-v3-gpt-4` style names
 * without the user reproducing separators or case. Scoring is deliberately simple and monotone:
 *
 *  100  exact match (after normalisation)
 *   60  one name starts with the query
 *   35  the query appears as a contiguous substring anywhere in the name
 *   18  the query is a *subsequence* of the name (its characters appear in order, not necessarily
 *       together) — this is the "loose order" case
 *    0  no relation at all
 *
 * A small bonus (≤ 4) rewards query characters appearing as consecutive pairs inside the name,
 * which breaks ties between two equally-loose subsequence hits in favour of the closer one.
 *
 * Normalisation strips case and every separator-like character (`-_:/ .`), so `gpt-4o` and
 * `gpt4o` compare equal before any rule above runs.
 *
 * The whole list is always returned, ordered best-first: a search that hides results confuses
 * more than it helps, and an empty query returns the original order untouched.
 */
object ModelSearch {

    /**
     * Rank [candidates] by how well each matches [query], best first. An empty or blank query
     * returns the candidates in their original order.
     */
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
        // Subsequence: walk the name, consuming one query character whenever it appears. If every
        // query character is consumed in order, the query "occurs" inside the name loosely.
        var qi = 0
        for (ch in name) {
            if (qi < q.length && ch == q[qi]) qi++
        }
        if (qi < q.length) return 0
        // Consecutive-pair bonus: for each query bigram that survives in the name, +1. Cheap and
        // effective at ordering "closer" subsequence matches above "looser" ones.
        val namePairs = name.windowed(2).toSet()
        return 18 + minOf(4, namePairs.intersect(qPairs).size)
    }

    private fun normalise(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }
}

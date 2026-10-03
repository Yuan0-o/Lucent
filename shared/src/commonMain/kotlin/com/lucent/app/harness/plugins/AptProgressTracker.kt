package com.lucent.app.harness.plugins

class AptProgressTracker(private val onProgress: (Float, String) -> Unit) {
    private var total = 0
    private var fetched = 0
    private var staged = 0
    private val summary = Regex("""(\d+) upgraded, (\d+) newly installed""")

    fun onLine(line: String) {
        val t = line.trim()
        val m = summary.find(t)
        if (m != null) {
            total = m.groupValues[1].toInt() + m.groupValues[2].toInt()
            fetched = 0
            staged = 0
            return
        }
        if (total <= 0) return
        when {
            t.startsWith("Get:") -> {
                fetched++
                onProgress((0.60f + 0.15f * fetched.toFloat() / total).coerceAtMost(0.75f), "downloading packages")
            }
            t.startsWith("Unpacking ") || t.startsWith("Setting up ") -> {
                staged++
                onProgress((0.75f + 0.24f * staged.toFloat() / (2 * total)).coerceAtMost(0.99f), "installing packages")
            }
        }
    }
}

package dev.droidtop.shared.version

/**
 * Loose version ordering for strings that are not strict dotted integers: folder names,
 * store versions, image tags. Use [Version] for plugin and engine versions, which are.
 */
object Versions {
    /**
     * Compares dot, underscore or dash separated components, numerically where a component
     * starts with digits and as text after that (`0.10` is newer than `0.9`, `0.8.3b` is newer
     * than `0.8.3`, and a missing component is zero, so `1.2` is newer than `1.1.9`). An empty
     * string is the oldest: it compares by length, so anything with text outranks it.
     */
    fun compareLoose(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return a.length.compareTo(b.length)
        val left = a.split('.', '_', '-')
        val right = b.split('.', '_', '-')
        for (index in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(index) { "0" }
            val r = right.getOrElse(index) { "0" }
            val ln = l.takeWhile { it.isDigit() }
            val rn = r.takeWhile { it.isDigit() }
            val byNumber = (ln.toLongOrNull() ?: 0L).compareTo(rn.toLongOrNull() ?: 0L)
            if (byNumber != 0) return byNumber
            val byText = l.dropWhile { it.isDigit() }.compareTo(r.dropWhile { it.isDigit() })
            if (byText != 0) return byText
        }
        return 0
    }
}

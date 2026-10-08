package io.github.jqssun.gpssetter.update

// True only if `remote` is a strictly higher version than `current` (both "vX.Y.Z" or "X.Y.Z").
// The old check fired on any difference, so a newer local build was offered the older release.
// Pure so it's unit-tested.
fun isNewerVersion(remote: String?, current: String?): Boolean {
    val r = parseVersion(remote) ?: return false
    val c = parseVersion(current) ?: return false
    for (i in 0 until maxOf(r.size, c.size)) {
        val rv = r.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (rv != cv) return rv > cv
    }
    return false
}

private fun parseVersion(v: String?): List<Int>? {
    val s = v?.trim()?.trimStart('v', 'V')?.takeIf { it.isNotEmpty() } ?: return null
    return s.split('.').map { it.toIntOrNull() ?: return null }
}

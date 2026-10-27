package com.stormpanda.megingiard.update

object SemVerComparator {
    /**
     * Compares [currentVersion] against [latestTag] using semantic versioning rules.
     *
     * Handles leading 'v'/'V' prefixes, build metadata ('+'), and pre-release suffixes
     * (e.g. "0.3.2-SNAPSHOT" is treated as older than release "0.3.2").
     */
    fun isUpdateAvailable(
        currentVersion: String,
        latestTag: String
    ): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
        if (cleanCurrent.isEmpty() || cleanLatest.isEmpty()) return false
        if (cleanCurrent == cleanLatest) return false

        val currentNoMeta = cleanCurrent.substringBefore('+')
        val latestNoMeta = cleanLatest.substringBefore('+')

        val currentCore = currentNoMeta.substringBefore('-')
        val latestCore = latestNoMeta.substringBefore('-')

        val currentPreRelease = currentNoMeta.contains('-')
        val latestPreRelease = latestNoMeta.contains('-')

        val currentParts = currentCore.split('.').map { it.toIntOrNull() ?: 0 }
        val latestParts = latestCore.split('.').map { it.toIntOrNull() ?: 0 }
        val maxParts = maxOf(currentParts.size, latestParts.size)

        for (i in 0 until maxParts) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        // Core versions are equal: a pre-release (e.g., 0.3.2-SNAPSHOT) is older than a release (0.3.2)
        return currentPreRelease && !latestPreRelease
    }
}

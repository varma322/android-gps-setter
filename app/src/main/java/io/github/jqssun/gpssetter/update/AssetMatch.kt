package io.github.jqssun.gpssetter.update

// A release asset belongs to this build only if it's "app-<flavor>-...apk".
// Matching any .apk let the updater offer the wrong flavor (foss<->full). Pure so it's unit-tested.
fun updateAssetMatches(name: String?, flavor: String): Boolean =
    name != null && name.startsWith("app-$flavor-") && name.endsWith(".apk")

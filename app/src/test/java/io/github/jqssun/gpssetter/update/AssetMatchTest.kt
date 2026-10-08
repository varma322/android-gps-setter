package io.github.jqssun.gpssetter.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetMatchTest {
    @Test fun matchesOwnFlavor() {
        assertTrue(updateAssetMatches("app-foss-arm64-v8a-release.apk", "foss"))
        assertTrue(updateAssetMatches("app-full-arm64-v8a-release.apk", "full"))
    }

    @Test fun rejectsOtherFlavor() {
        assertFalse(updateAssetMatches("app-full-arm64-v8a-release.apk", "foss"))
        assertFalse(updateAssetMatches("app-foss-arm64-v8a-release.apk", "full"))
    }

    @Test fun rejectsNonApkAndNull() {
        assertFalse(updateAssetMatches("app-foss-sources.zip", "foss"))
        assertFalse(updateAssetMatches(null, "foss"))
    }
}

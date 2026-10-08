package io.github.jqssun.gpssetter.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newerIsOffered() {
        assertTrue(isNewerVersion("v0.2.0", "v0.1.0"))
        assertTrue(isNewerVersion("v0.1.1", "v0.1.0"))
        assertTrue(isNewerVersion("v1.0.0", "v0.9.9"))
        assertTrue(isNewerVersion("0.2.0", "0.1.0")) // no v prefix
    }

    @Test fun sameOrOlderIsNot() {
        assertFalse(isNewerVersion("v0.1.0", "v0.1.0"))
        assertFalse(isNewerVersion("v0.0.6", "v0.1.0")) // older remote, the bug this guards
        assertFalse(isNewerVersion("v0.1.0", "v0.2.0"))
    }

    @Test fun garbageIsNot() {
        assertFalse(isNewerVersion(null, "v0.1.0"))
        assertFalse(isNewerVersion("vnightly", "v0.1.0"))
        assertFalse(isNewerVersion("v0.1.0", null))
    }
}

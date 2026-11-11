package io.github.jqssun.gpssetter.utils

import org.junit.Assert.assertEquals
import org.junit.Test

// what system_server runs with vs the saved "Hook system" setting (it's read once, at boot)
class SystemHookTest {
    @Test fun unchangedSinceBootIsTheSavedSetting() {
        assertEquals(true, systemHookedThisBoot(saved = true, recordedBoot = -1, recordedValue = false, boot = 7))
    }

    @Test fun changedThisBootIsWhatTheBootStartedWith() {
        // turned off after booting with it on: system_server still has it on until the reboot
        assertEquals(true, systemHookedThisBoot(saved = false, recordedBoot = 7, recordedValue = true, boot = 7))
        assertEquals(false, systemHookedThisBoot(saved = true, recordedBoot = 7, recordedValue = false, boot = 7))
    }

    @Test fun aRecordFromAnEarlierBootNoLongerApplies() {
        assertEquals(false, systemHookedThisBoot(saved = false, recordedBoot = 6, recordedValue = true, boot = 7))
    }

    @Test fun anUnknownBootCountFallsBackToTheSavedSetting() {
        assertEquals(false, systemHookedThisBoot(saved = false, recordedBoot = -1, recordedValue = true, boot = -1))
    }
}

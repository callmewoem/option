package com.locke.app.ui.home

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.locke.app.ui.theme.LockeTheme
import org.junit.Rule
import org.junit.Test

/**
 * JVM screenshot tests for the redesigned Today screen (no emulator/device needed --
 * Paparazzi renders via layoutlib on the host JVM). Run with:
 * `./gradlew :app:testDebugUnitTest --tests "com.locke.app.ui.home.TodayScreenScreenshotTest"`
 * and inspect the PNGs under app/out/failures/ (recorded on every run, not just failures,
 * since these tests never call verify()) or app/build/paparazzi/.
 */
class TodayScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun today_twelveHabits() {
        paparazzi.snapshot {
            LockeTheme { TodayScreenPreviewScaffold(rows = TWELVE_HABIT_ROWS) }
        }
    }

    @Test
    fun today_fewHabits() {
        paparazzi.snapshot {
            LockeTheme { TodayScreenPreviewScaffold(rows = FEW_HABIT_ROWS) }
        }
    }

    @Test
    fun today_empty() {
        paparazzi.snapshot {
            LockeTheme { TodayScreenPreviewScaffold(rows = emptyList()) }
        }
    }
}

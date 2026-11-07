package com.lume.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while scrolling the catalogue grid and a chapter in the webtoon reader, with the
 * baseline profile: `gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 *
 * Both need the default source to answer (network). When nothing loads, the iteration only
 * records the frames it got, so read the numbers together with the frame count.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun catalogGrid() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            tap(By.desc("Catalog"), timeout = 3_000)
            device.wait(Until.hasObject(By.scrollable(true)), 10_000)
        },
    ) {
        flingScrollable()
    }

    @Test
    fun webtoonReader() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            openFirstChapter()
        },
    ) {
        repeat(4) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 15)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(device.displayWidth / 2, device.displayHeight / 4, device.displayWidth / 2, device.displayHeight * 3 / 4, 15)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.flingScrollable() {
        val list = device.findObject(By.scrollable(true)) ?: return
        list.setGestureMargin(device.displayWidth / 5)
        repeat(2) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        list.fling(Direction.UP)
        device.waitForIdle()
    }

    /** Home → first spotlight title → chapter list → first chapter, as in [browseApp]. */
    private fun MacrobenchmarkScope.openFirstChapter() {
        tap(By.desc("Home"), timeout = 3_000)
        Thread.sleep(5_000)
        device.click((device.displayWidth * 0.19).toInt(), (device.displayHeight * 0.24).toInt())
        device.waitForIdle()
        if (!tap(By.textStartsWith("Chapter list"), timeout = 10_000)) return
        if (tap(By.textStartsWith("Chapter "), timeout = 10_000)) Thread.sleep(4_000)
    }
}

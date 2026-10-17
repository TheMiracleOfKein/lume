package com.lume.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "com.lume.app"

/**
 * The paths the profile should make fast: start-up, switching tabs, scrolling a cover grid,
 * opening a title, its chapter list and a chapter in the reader. Steps that need a working
 * source are skipped when nothing loads, so the journey never fails without network.
 */
fun MacrobenchmarkScope.browseApp() {
    device.waitForIdle()
    for (tab in listOf("Catalog", "Library", "Updates", "Home")) {
        tap(By.desc(tab), timeout = 3_000)
        device.waitForIdle()
    }
    // Catalog: scroll the grid both ways.
    tap(By.desc("Catalog"))
    device.wait(Until.hasObject(By.scrollable(true)), 8_000)
    runCatching {
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }
    // Home: open the first spotlight title (by position; its name depends on the source).
    tap(By.desc("Home"))
    Thread.sleep(5_000)
    device.click((device.displayWidth * 0.19).toInt(), (device.displayHeight * 0.24).toInt())
    device.waitForIdle()
    // Title → chapter list → first chapter in the reader, scroll a little, then all the way back.
    if (!tap(By.textStartsWith("Chapter list"), timeout = 10_000)) {
        device.pressBack()
        return
    }
    if (tap(By.textStartsWith("Chapter "), timeout = 10_000)) {
        Thread.sleep(4_000)
        repeat(3) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()
    }
    repeat(2) {
        device.pressBack()
        device.waitForIdle()
    }
}

/** Waits for [selector] and clicks it; finds it again if the screen changed in between. */
private fun MacrobenchmarkScope.tap(selector: BySelector, timeout: Long = 8_000): Boolean {
    repeat(3) {
        device.wait(Until.hasObject(selector), timeout)
        val clicked = runCatching { device.findObject(selector)?.also { it.click() } != null }.getOrDefault(false)
        if (clicked) return true
        device.waitForIdle()
    }
    return false
}

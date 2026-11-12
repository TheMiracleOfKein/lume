package com.lume.app.ui.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The three looks every main screen is checked in. */
enum class Look(val suffix: String, val dark: Boolean, val fontScale: Float) {
    Light("light", dark = false, fontScale = 1f),
    Dark("dark", dark = true, fontScale = 1f),
    LargeFont("large", dark = false, fontScale = 2f),
}

/**
 * Base of the screenshot tests. Each test renders a stateless screen composable with fixed data
 * (no network, no database) and compares it with the golden image in `src/test/screenshots`.
 * Without a Roborazzi mode switched on the capture is skipped, so a plain `testDebugUnitTest`
 * only checks that the screens still compose.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-port-xxhdpi")
abstract class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /** Renders [content] in [look] and captures it as `<name>_<look>.png`. */
    protected fun shoot(name: String, look: Look, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, look.fontScale)) {
                LumeTheme(darkTheme = look.dark) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.background) {
                        content()
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/${name}_${look.suffix}.png",
            roborazziOptions = RoborazziOptions(
                // A sliver of anti-aliasing noise between operating systems is not a regression.
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }
}

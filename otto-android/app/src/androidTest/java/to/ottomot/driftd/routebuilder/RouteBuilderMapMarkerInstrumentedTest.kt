package to.ottomot.driftd.routebuilder

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import to.ottomot.driftd.R
import java.io.File

/**
 * Instrumented Route Builder map marker tests — requires emulator/device + MAPBOX_ACCESS_TOKEN.
 * Saves screenshot artifacts under app internal/external files for local debugging.
 */
@RunWith(JUnit4::class)
class RouteBuilderMapMarkerInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun requireMapboxToken() {
        val token =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getString(R.string.mapbox_access_token)
                .trim()
        assumeTrue("MAPBOX_ACCESS_TOKEN required for map render", token.isNotBlank() && token != " ")
    }

    @Test
    fun seededMap_rendersVisibleCheckpointAnnotations() {
        val mapContent = RouteBuilderMapTestFixtures.seededMapContent(waypointCount = 2)
        val camera = RouteBuilderMapTestFixtures.seededCameraTarget()
        composeRule.setContent {
            RouteBuilderMapHost(
                mapContent = mapContent,
                programmaticCameraTarget = camera,
                allowsInteraction = true,
                onCameraChanged = { _, _, _ -> },
                onGestureStarted = {},
                onGestureEnded = {},
                onMapLongPress = { _, _ -> },
                onMarkerLongPress = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(8_000)
        val screenshot =
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("Expected uiAutomation screenshot")
        saveScreenshotArtifact("route-builder-checkpoint-markers.png", screenshot)
        val blueClusterCount = screenshot.countBlueCheckpointMarkerClusters()
        assertTrue(
            "Expected >= 2 visible blue checkpoint markers on map (found $blueClusterCount)",
            blueClusterCount >= 2,
        )
    }

    private fun saveScreenshotArtifact(filename: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val internalDir = File(context.filesDir, "route-builder-test-artifacts")
        internalDir.mkdirs()
        File(internalDir, filename).outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        val externalDir = File(context.getExternalFilesDir(null), "route-builder-test-artifacts")
        externalDir.mkdirs()
        File(externalDir, filename).outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}

/** GL checkpoint circles (~#3895FA) — Mapbox interior markers are not in Compose semantics. */
internal fun Bitmap.countBlueCheckpointMarkerClusters(minClusterPixels: Int = 10): Int {
    val step = 6
    val visited = BooleanArray(width * height)
    var clusters = 0
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            if (!isBlueCheckpointPixel(getPixel(x, y))) continue
            val index = y * width + x
            if (visited[index]) continue
            var clusterSize = 0
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue.add(x to y)
            while (queue.isNotEmpty()) {
                val (cx, cy) = queue.removeFirst()
                if (cx !in 0 until width || cy !in 0 until height) continue
                val ci = cy * width + cx
                if (visited[ci]) continue
                if (!isBlueCheckpointPixel(getPixel(cx, cy))) continue
                visited[ci] = true
                clusterSize++
                queue.add(cx + step to cy)
                queue.add(cx - step to cy)
                queue.add(cx to cy + step)
                queue.add(cx to cy - step)
            }
            if (clusterSize >= minClusterPixels) {
                clusters++
            }
        }
    }
    return clusters
}

private fun isBlueCheckpointPixel(pixel: Int): Boolean {
    val r = Color.red(pixel)
    val g = Color.green(pixel)
    val b = Color.blue(pixel)
    return b > 180 && g > 80 && r < 120
}

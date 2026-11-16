package app.loki.lightthere

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import app.loki.kit.ArtCanvas
import app.loki.kit.CanvasView
import app.loki.kit.EngineClient
import app.loki.kit.renderScaleFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The most the engine is asked to render per surface unit: the engine caps the bitmap itself. */
const val MAX_RENDER_SCALE = 2f

/**
 * The surface on the engine: what `loki.surface` says is shown (its `width` and `height` in surface
 * units, `current` page), rendered at the zoom it is viewed at (again when the zoom settles and after every change),
 * with two fingers or the wheel to pan and zoom. Editing on the canvas is this app's next step.
 */
@OptIn(FlowPreview::class)
@Composable
fun ArtSurface(native: SurfaceEngine, revision: Int, modifier: Modifier) {
    val client = remember(native) { EngineClient(native.transport) }
    val view = remember { CanvasView() }
    var page by remember { mutableStateOf<Rect?>(null) }
    var current by remember { mutableStateOf(0) }
    var count by remember { mutableStateOf(0) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    LaunchedEffect(view) {
        snapshotFlow { view.zoom }.debounce(150).collect { z -> if (z > 0f) scale = renderScaleFor(z, MAX_RENDER_SCALE) }
    }
    LaunchedEffect(revision, scale) {
        withContext(Dispatchers.Default) {
            val info = runCatching { client.call("loki.surface") }.getOrNull() as? JsonObject
            fun num(k: String) = (info?.get(k) as? JsonPrimitive)?.doubleOrNull
            val w = num("width"); val h = num("height"); val x0 = num("x") ?: 0.0; val y0 = num("y") ?: 0.0
            count = num("pages")?.toInt() ?: 0
            current = num("current")?.toInt() ?: 0
            page = if (w != null && h != null && w > 0 && h > 0) Rect(x0.toFloat(), y0.toFloat(), (x0 + w).toFloat(), (y0 + h).toFloat()) else null
            image = native.renderPage(current, scale)?.let { rgbaToImageBitmap(it.width, it.height, it.rgba) }
        }
    }
    Box(modifier) {
        ArtCanvas(page, image, view, onInput = {}, modifier = Modifier.fillMaxSize(), panTool = true)
        if (image == null) {
            Text(
                if (count == 0) "No photos yet: File › Import…" else "the engine rendered nothing",
                Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

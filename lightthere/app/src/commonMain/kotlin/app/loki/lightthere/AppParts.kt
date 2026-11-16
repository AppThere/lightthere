package app.loki.lightthere

// This file is the app's own (egui2compose writes it once). LightCraft is a library of photos and
// an editor for the one in hand: the Photos panel lists the library, Develop edits the photo in hand
// with the engine's own controls (`develop.controls`), Presets applies a preset.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.loki.kit.DockPanel
import app.loki.kit.EngineClient
import app.loki.kit.RailTool
import app.loki.kit.formatNumber
import app.loki.kit.sliderPosition
import app.loki.kit.sliderValue
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Images
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Read after every change: the library's photos, its state (selection, sort, source) and the develop controls of the photo in hand. */
val STATE_COMMANDS: List<String> = listOf("catalog.query", "library.state", "develop.controls", "presets.list")

private fun JsonElement?.obj() = this as? JsonObject
private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
private fun JsonElement?.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.num(key: String): Double? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonElement?.long(key: String): Long? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
private fun JsonElement?.bool(key: String): Boolean? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

private fun answer(state: JsonElement?, id: String): JsonElement? = state.obj()?.get(id)

/** The photos of the current view (`catalog.query`). */
private fun photos(state: JsonElement?): List<JsonElement> = answer(state, "catalog.query").obj()?.get("photos").arr()

/** The photo in hand. */
private fun activePhoto(state: JsonElement?): JsonElement? {
    val id = answer(state, "library.state").obj()?.get("selection").long("active") ?: return null
    return photos(state).firstOrNull { it.long("id") == id }
}

/** The document's description, for the Properties-style lookups: the photo in hand. */
fun docOf(state: JsonElement?): JsonElement? = activePhoto(state)

/** Commands this file runs, so the coverage test checks the engine still has them. */
val PANEL_COMMANDS: Set<String> = setOf("library.filter", "library.sort", "library.select", "develop.set", "develop.resetSection", "preset.apply", "photo.rate", "photo.flag", "photo.rotateLeft", "photo.rotateRight", "library.next", "library.previous", "library.import")

/** The panel open at the start (null: the dock starts collapsed). */
val FIRST_PANEL: String? = "develop"

/** Panels that share a column, shown as tabs together. */
val PANEL_GROUPS: List<List<String>> = listOf(listOf("develop", "presets"))

fun documentTitle(state: JsonElement?): String = activePhoto(state).str("fileName") ?: APP_NAME

fun documentDirty(state: JsonElement?): Boolean = activePhoto(state).bool("edited") == true

fun statusSegments(state: JsonElement?): List<String> {
    val p = activePhoto(state)
    val total = answer(state, "catalog.query").obj()?.get("total").let { (it as? JsonPrimitive)?.longOrNull } ?: 0
    return listOfNotNull(
        answer(state, "library.state").str("sourceLabel"),
        "$total photo${if (total == 1L) "" else "s"}",
        p?.let { "${it.long("width") ?: 0} × ${it.long("height") ?: 0}" },
        p?.str("format"),
    )
}

/** LightCraft has no tool palette: a photo is edited with the Develop panel. */
fun loadTools(engine: EngineClient): List<RailTool> = emptyList()

fun toolCommand(id: String): Pair<String, JsonObject>? = null

fun toolInHand(state: JsonElement?): String? = null

/** The options bar: the photo in hand, with its rating, flag, rotation and the way to the next and previous. */
@Composable
fun OptionsBar(tool: String, tools: List<RailTool>, state: JsonElement?, run: (String, JsonObject) -> Unit) {
    val p = activePhoto(state)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        fun go(command: String) = run(command, JsonObject(emptyMap()))
        IconButton({ go("library.previous") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronLeft, "Previous photo", Modifier.size(20.dp)) }
        IconButton({ go("library.next") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronRight, "Next photo", Modifier.size(20.dp)) }
        val rating = (p.long("rating") ?: 0).toInt()
        for (n in 1..5) IconButton(
            { run("photo.rate", buildJsonObject { put("rating", if (n == rating) 0 else n) }) },
            Modifier.size(32.dp), enabled = p != null,
        ) { Icon(Lucide.Star, "Rate $n", Modifier.size(18.dp), tint = if (n <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        val flag = p.str("flag") ?: "none"
        IconButton(
            { run("photo.flag", buildJsonObject { put("flag", if (flag == "pick") "none" else "pick") }) },
            Modifier.size(36.dp), enabled = p != null,
        ) { Icon(Lucide.Flag, "Pick", Modifier.size(18.dp), tint = if (flag == "pick") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton({ go("photo.rotateLeft") }, Modifier.size(36.dp), enabled = p != null) { Icon(Lucide.RotateCcw, "Rotate left", Modifier.size(18.dp)) }
        IconButton({ go("photo.rotateRight") }, Modifier.size(36.dp), enabled = p != null) { Icon(Lucide.RotateCw, "Rotate right", Modifier.size(18.dp)) }
    }
}

/** No tools, so nothing under them. */
@Composable
fun ToolFooter(state: JsonElement?, run: (String, JsonObject) -> Unit) {}

/** Import is the desktop's file dialog; the engine takes paths. */
val APP_ACTIONS: List<app.loki.kit.RibbonItem> = listOf(
    app.loki.kit.RibbonItem("app.import", "Import Photos…", ""),
    app.loki.kit.RibbonItem("app.exportPhoto", "Export Photo…", ""),
)
val APP_ACTION_IDS: Set<String> = APP_ACTIONS.map { it.id }.toSet()

fun withAppActions(tabs: List<app.loki.kit.RibbonTab>): List<app.loki.kit.RibbonTab> = app.loki.kit.addToFileMenu(tabs, APP_ACTIONS)

/** Runs one of [APP_ACTIONS] (off the UI thread: it may wait on a file picker). */
suspend fun appAction(id: String, state: JsonElement?, io: app.loki.kit.FileIo) {
    when (id) {
        "app.import" -> {
            val p = io.pick(false, "") ?: return
            val r = io.call("engine.execute", buildJsonObject { put("command", "library.import"); put("params", buildJsonObject { put("paths", buildJsonArray { add(JsonPrimitive(p)) }) }) })
            if (r != null) {
                io.refresh()
                val n = r.obj()?.get("imported").arr().size
                io.status(if (n == 0) "Nothing imported (already in the library, or not a photo)" else "Imported $n photo${if (n == 1) "" else "s"}")
            }
        }
        "app.exportPhoto" -> {
            val name = (activePhoto(state).str("fileName") ?: "photo").substringBeforeLast('.')
            val p = io.pick(true, "$name.jpg") ?: return
            if (io.call("app.export", buildJsonObject { put("path", p) }) != null) { io.publish(p); io.refresh(); io.status("Exported ${p.substringAfterLast('/')}") }
        }
    }
}

/** The dock: the library's photos, the develop controls and the presets. */
fun dockPanels(state: JsonElement?, revision: Int, run: (String, JsonObject) -> Unit): List<DockPanel> = listOf(
    DockPanel("photos", "Photos", Lucide.Images) { m -> PhotosPanel(state, run, m) },
    DockPanel("develop", "Develop", Lucide.SlidersHorizontal) { m -> DevelopPanel(state, run, m) },
    DockPanel("presets", "Presets", Lucide.Sparkles) { m -> PresetsPanel(state, run, m) },
)

private fun exec(run: (String, JsonObject) -> Unit, command: String, params: JsonObject) =
    run(command, params)

@Composable
private fun PhotosPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val list = photos(state)
    val active = answer(state, "library.state").obj()?.get("selection").long("active")
    if (list.isEmpty()) {
        Text("No photos yet: File › Import Photos…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(modifier) {
        items(list) { p ->
            val id = p.long("id") ?: return@items
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { exec(run, "library.select", buildJsonObject { put("ids", buildJsonArray { add(JsonPrimitive(id)) }); put("active", id) }) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.str("fileName") ?: "photo $id", style = MaterialTheme.typography.bodyMedium, maxLines = 1, fontWeight = if (id == active) FontWeight.Bold else FontWeight.Normal)
                    Text("${p.long("width") ?: 0} × ${p.long("height") ?: 0}  ${p.str("format").orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val r = (p.long("rating") ?: 0).toInt()
                if (r > 0) Text("★".repeat(r), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The develop controls by section, each a slider that tells the engine when it is let go; a tap on the name resets it. */
@Composable
private fun DevelopPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val controls = answer(state, "develop.controls").arr()
    if (activePhoto(state) == null || controls.isEmpty()) {
        Text("Open a photo to edit it.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val sections = controls.groupBy { it.str("section") ?: "" }
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        for ((section, items) in sections) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(section.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton({ exec(run, "develop.resetSection", buildJsonObject { put("section", section) }) }) { Text("Reset") }
            }
            for (c in items) ControlRow(c, run)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ControlRow(c: JsonElement, run: (String, JsonObject) -> Unit) {
    val id = c.str("id") ?: return
    val min = c.num("min") ?: 0.0; val max = c.num("max") ?: 1.0
    val decimals = (c.long("decimals") ?: 0).toInt()
    val value = c.num("value") ?: c.num("default") ?: min
    var held by remember(id) { mutableStateOf<Double?>(null) }
    val now = held ?: value
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.str("label") ?: id, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).clickable {
                    c.num("default")?.let { d -> exec(run, "develop.set", buildJsonObject { put("control", id); put("value", d) }) }
                },
            )
            Text(formatNumber(now, decimals), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            sliderPosition(now, min, max, false),
            { t -> held = sliderValue(t, min, max, false) },
            modifier = Modifier.heightIn(max = 28.dp),
            onValueChangeFinished = { held?.let { v -> held = null; exec(run, "develop.set", buildJsonObject { put("control", id); put("value", v) }) } },
        )
    }
}

@Composable
private fun PresetsPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val raw = answer(state, "presets.list")
    // The answer is a list, or an object with the lists in it; show whatever has a name.
    val rows = (raw.arr().ifEmpty { raw.obj()?.values?.flatMap { it.arr() } ?: emptyList() }).filter { it.str("name") != null }
    if (rows.isEmpty()) { Text("No presets.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    LazyColumn(modifier) {
        items(rows) { p ->
            val id = p.str("id") ?: p.str("name") ?: return@items
            Text(
                p.str("name").orEmpty(), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { exec(run, "preset.apply", buildJsonObject { put("preset", id); put("id", id) }) }.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

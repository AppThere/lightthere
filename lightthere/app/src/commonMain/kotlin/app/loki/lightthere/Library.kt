package app.loki.lightthere

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.loki.kit.ChoiceChip
import app.loki.kit.ToggleChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A photo as the library lists it (`catalog.query`). */
data class LibPhoto(val id: Long, val fileName: String, val rating: Int, val flag: String, val label: String?, val width: Long, val height: Long, val edited: Boolean)

/** The filter and sort the library is showing, from `library.state`. */
data class LibFilter(val text: String, val rating: Int, val flag: String?)
data class LibSort(val key: String, val ascending: Boolean)

private fun JsonElement?.o() = this as? JsonObject
private fun JsonElement?.a(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.longOrNull

/** The photos of the view, in the order the grid shows them, which is the order of the pages the engine renders. */
fun libraryPhotos(state: JsonElement?): List<LibPhoto> =
    state.o()?.get("catalog.query").o()?.get("photos").a().mapNotNull { e ->
        val p = e.o() ?: return@mapNotNull null
        LibPhoto(
            p.l("id") ?: return@mapNotNull null, p.s("fileName") ?: "photo", (p.l("rating") ?: 0L).toInt().coerceIn(0, 5), p.s("flag") ?: "none", p.s("label"),
            p.l("width") ?: 0L, p.l("height") ?: 0L, (p["edited"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

private fun libraryState(state: JsonElement?) = state.o()?.get("library.state").o()

fun selectedIds(state: JsonElement?): Set<Long> = libraryState(state)?.get("selection").o()?.get("ids").a().mapNotNull { (it as? JsonPrimitive)?.longOrNull }.toSet()
fun activeId(state: JsonElement?): Long? = libraryState(state)?.get("selection").o()?.l("active")

fun libraryFilter(state: JsonElement?): LibFilter {
    val f = libraryState(state)?.get("filter").o()
    return LibFilter(f?.s("text").orEmpty(), (f?.l("rating") ?: 0L).toInt().coerceIn(0, 5), f?.s("flag"))
}

fun librarySort(state: JsonElement?): LibSort {
    val s = libraryState(state)?.get("sort").o()
    return LibSort(s?.s("key") ?: "captureDate", (s?.get("ascending") as? JsonPrimitive)?.booleanOrNull ?: false)
}

/** The command and params that make the library filter by [text], [rating] (at least) and [flag] (null: any). */
fun filterCall(text: String, rating: Int, flag: String?): Pair<String, JsonObject> = "library.filter" to JsonObject(
    mapOf("text" to JsonPrimitive(text), "rating" to JsonPrimitive(rating), "ratingOp" to JsonPrimitive("atLeast"), "flag" to (flag?.let { JsonPrimitive(it) } ?: JsonNull)),
)

fun sortCall(key: String, ascending: Boolean): Pair<String, JsonObject> = "library.sort" to buildJsonObject { put("key", key); put("ascending", ascending) }

fun selectCall(id: Long): Pair<String, JsonObject> = "library.select" to buildJsonObject { put("ids", buildJsonArray { add(JsonPrimitive(id)) }); put("active", id) }

private val SORT_KEYS = listOf("captureDate" to "Capture date", "importDate" to "Import date", "editDate" to "Edit date", "fileName" to "File name", "rating" to "Rating", "fileSize" to "File size")

/** The search box, rating, flag and sort of the library, and the Grid / Loupe switch. */
@Composable
fun LibraryBar(state: JsonElement?, grid: Boolean, onGrid: (Boolean) -> Unit, run: (String, JsonObject) -> Unit, modifier: Modifier = Modifier) {
    val f = libraryFilter(state)
    val sort = librarySort(state)
    var text by remember { mutableStateOf(f.text) }
    // The engine is asked once the typing has paused; its own value (after Clear, say) comes back into the box.
    LaunchedEffect(text) { if (text != f.text) { delay(300); filterCall(text, f.rating, f.flag).let { (c, p) -> run(c, p) } } }
    LaunchedEffect(f.text) { if (f.text != text) text = f.text }
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToggleChip("Grid", grid) { onGrid(true) }
        ToggleChip("Loupe", !grid) { onGrid(false) }
        OutlinedTextField(text, { text = it }, Modifier.width(180.dp), singleLine = true, placeholder = { Text("Search", style = TextStyle(fontSize = 13.sp)) }, textStyle = TextStyle(fontSize = 13.sp))
        ChoiceChip("Rating", f.rating.toString(), listOf("0" to "Any", "1" to "★ and up", "2" to "★★ and up", "3" to "★★★ and up", "4" to "★★★★ and up", "5" to "★★★★★"), onChange = { filterCall(f.text, it.toInt(), f.flag).let { (c, p) -> run(c, p) } })
        ChoiceChip("Flag", f.flag ?: "any", listOf("any" to "Any", "pick" to "Picked", "reject" to "Rejected"), onChange = { filterCall(f.text, f.rating, if (it == "any") null else it).let { (c, p) -> run(c, p) } })
        ChoiceChip("Sort", sort.key, SORT_KEYS, onChange = { sortCall(it, sort.ascending).let { (c, p) -> run(c, p) } })
        ToggleChip(if (sort.ascending) "Ascending" else "Descending", sort.ascending) { sortCall(sort.key, it).let { (c, p) -> run(c, p) } }
    }
}

/**
 * The library as thumbnails, in the order the engine shows it. A tap selects a photo, a double tap opens
 * it in the loupe; each shows its rating and flag. Only the cells in view are rendered (the engine's page
 * for that photo, small), and the photo in hand is rendered again after each change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGrid(native: SurfaceEngine, state: JsonElement?, revision: Int, run: (String, JsonObject) -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val photos = libraryPhotos(state)
    val active = activeId(state)
    val chosen = selectedIds(state)
    val thumbs = remember(native) { mutableStateMapOf<Long, ImageBitmap>() }
    // A page is a place in the engine's visible list, so a thumbnail belongs to its photo only while that order stands.
    val order = photos.map { it.id }
    val orderNow by androidx.compose.runtime.rememberUpdatedState(order)
    LaunchedEffect(order) { thumbs.clear() }
    // An edit changes the photo's look: its thumbnail is made again.
    LaunchedEffect(revision) { active?.let { thumbs.remove(it) } }
    if (photos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photos match: File › Import…, or clear the search and filters.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(168.dp), modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(photos, key = { _, p -> p.id }) { index, p ->
            val img = thumbs[p.id]
            LaunchedEffect(p.id, index, img == null) {
                if (img == null) {
                    val made = order
                    val bitmap = withContext(Dispatchers.Default) { native.renderPage(index, THUMB_SCALE)?.let { rgbaToImageBitmap(it.width, it.height, it.rgba) } }
                    // Kept only if the list is still the one this was rendered for.
                    if (bitmap != null && made == orderNow) thumbs[p.id] = bitmap
                }
            }
            val on = p.id == active
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.35f).background(Color(0xFF1B1B1B)).border(if (on) 3.dp else if (p.id in chosen) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else if (p.id in chosen) MaterialTheme.colorScheme.secondary else Color(0xFF3A3A3A))
                        .combinedClickable(onDoubleClick = { selectCall(p.id).let { (c, q) -> run(c, q) }; onOpen() }) { selectCall(p.id).let { (c, q) -> run(c, q) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (img != null) Image(img, p.fileName, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    if (p.flag == "pick") Text("⚑", Modifier.align(Alignment.TopStart).padding(6.dp), color = Color(0xFFFFD54F))
                    if (p.flag == "reject") Text("✕", Modifier.align(Alignment.TopStart).padding(6.dp), color = Color(0xFFEF5350))
                    if (p.edited) Text("✎", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Color(0xFFB0BEC5))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.fileName, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    if (p.rating > 0) Text("★".repeat(p.rating), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** The engine renders a photo at this many times its 2000-unit reference edge for a thumbnail. */
const val THUMB_SCALE = 0.1f

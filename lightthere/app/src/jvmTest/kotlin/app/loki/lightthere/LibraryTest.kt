package app.loki.lightthere

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryTest {
    private val state = Json.parseToJsonElement(
        """{"catalog.query":{"total":3,"photos":[
            {"id":1,"fileName":"a.png","rating":4,"flag":"pick","label":"red","width":1200,"height":800,"edited":true},
            {"id":2,"fileName":"b.png","rating":9,"flag":"none","width":10,"height":10},
            {"fileName":"no-id.png"},"junk"]},
           "library.state":{"selection":{"active":2,"ids":[1,2]},"sort":{"key":"fileName","ascending":true},"filter":{"text":"sun","rating":3,"flag":"pick"}}}""",
    )

    @Test fun thePhotosAreReadWithTheirRatingAndFlagAndABadOneIsLeftOut() {
        val p = libraryPhotos(state)
        assertEquals(listOf(1L, 2L), p.map { it.id })
        assertEquals(LibPhoto(1, "a.png", 4, "pick", "red", 1200, 800, true), p[0])
        assertEquals(5, p[1].rating, "a rating past five is five"); assertEquals("none", p[1].flag)
        assertEquals(emptyList(), libraryPhotos(null))
    }

    @Test fun theSelectionFilterAndSortAreRead() {
        assertEquals(2L, activeId(state)); assertEquals(setOf(1L, 2L), selectedIds(state))
        assertEquals(LibFilter("sun", 3, "pick"), libraryFilter(state)); assertEquals(LibSort("fileName", true), librarySort(state))
        assertEquals(LibFilter("", 0, null), libraryFilter(null)); assertEquals(LibSort("captureDate", false), librarySort(null))
    }

    @Test fun theCallsAreWhatTheEngineTakes() {
        val (c, p) = filterCall("sun", 2, null)
        assertEquals("library.filter", c); assertEquals(JsonNull, p["flag"]); assertEquals(JsonPrimitive("atLeast"), p["ratingOp"]); assertEquals(JsonPrimitive(2), p["rating"])
        assertEquals(JsonPrimitive("pick"), filterCall("", 0, "pick").second["flag"])
        assertEquals("library.sort" to listOf("key", "ascending"), sortCall("rating", false).let { it.first to it.second.keys.toList() })
        assertEquals(JsonPrimitive(7L), selectCall(7).second["active"])
    }

    /** Every command the library bar sends is one the engine has and documents, with these keys. */
    @Test fun everyLibraryCallIsOnACommandTheEngineDocumentsWithThatKey() {
        val docs = (Json.parseToJsonElement(java.io.File("src/jvmTest/resources/engine-commands.json").readText()) as kotlinx.serialization.json.JsonArray)
            .associate { c -> val o = c as kotlinx.serialization.json.JsonObject; (o["id"] as JsonPrimitive).content to (o["params"] as JsonPrimitive).content }
        for ((command, keys) in mapOf("library.filter" to filterCall("", 0, null).second.keys, "library.sort" to sortCall("rating", true).second.keys, "library.select" to selectCall(1).second.keys)) {
            val doc = docs[command] ?: error("the engine has no $command")
            for (k in keys) assertEquals(true, Regex("[{, ]$k[?:,}]").containsMatchIn(doc), "$command does not document $k: $doc")
        }
    }
}

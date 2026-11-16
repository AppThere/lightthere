package app.loki.lightthere.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cull body: translated from `dialogs.rs` (fn `cull_body`, line 844) by egui2compose.
 *
 * The controls and their order are the desktop dialog's. NOT translated: the starting values (the
 * desktop reads them from its own state), anything it draws by hand, and enabling rules. The
 * parameter names are guessed from the Rust fields each control was bound to: check them against
 * the command's params doc before trusting OK.
 *   command: (not found)
 *   params:  (unknown)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CullBodyDialog(onRun: (command: String, params: JsonObject) -> Unit, onDismiss: () -> Unit) {
    var f0 by remember { mutableStateOf(0.0f) }
    var f1 by remember { mutableStateOf(false) }
    var f2 by remember { mutableStateOf(1.0f) }
    var f3 by remember { mutableStateOf(0.7f) }
    SheetDialog(
        "Cull body", "", onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val params = buildJsonObject {
                put("rejectBelow", f0)
                put("pickBest", f1)
                put("burstGap", f2)
                put("similarity", f3)
                }
                /* TODO: which command this dialog runs was not found */
                onDismiss()
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reject below focus" + ": " + (kotlin.math.round(f0 * 100f) / 100f), style = MaterialTheme.typography.labelLarge)
            Slider(f0, { f0 = it }, valueRange = minOf(0.0f, 80.0f)..maxOf(0.0f, 80.0f))
            Row(Modifier.fillMaxWidth().clickable { f1 = !f1 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f1, { f1 = it })
                Text("Pick the sharpest photo of each burst")
            }
            Text("Blurry photos below the score are flagged as rejects." + ": " + (kotlin.math.round(f2 * 100f) / 100f), style = MaterialTheme.typography.labelLarge)
            Slider(f2, { f2 = it }, valueRange = minOf(1.0f, 600.0f)..maxOf(1.0f, 600.0f))
            Text("Similarity" + ": " + (kotlin.math.round(f3 * 100f) / 100f), style = MaterialTheme.typography.labelLarge)
            Slider(f3, { f3 = it }, valueRange = minOf(0.7f, 0.99f)..maxOf(0.7f, 0.99f))
        }
    }
}

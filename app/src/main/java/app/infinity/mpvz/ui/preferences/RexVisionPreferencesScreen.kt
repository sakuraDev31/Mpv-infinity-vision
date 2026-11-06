/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.infinity.mpvz.preferences.DecoderPreferences
import app.infinity.mpvz.preferences.preference.collectAsState
import app.infinity.mpvz.presentation.Screen
import app.infinity.mpvz.ui.player.RexAlgorithm
import app.infinity.mpvz.ui.player.RexVisionDiagnostics
import app.infinity.mpvz.ui.player.RexVisionPolicy
import app.infinity.mpvz.ui.player.controls.components.GlassSlider
import app.infinity.mpvz.ui.preferences.components.ListPreference
import app.infinity.mpvz.ui.preferences.components.SettingsScaffold
import app.infinity.mpvz.ui.preferences.components.SettingsTopAppBar
import app.infinity.mpvz.ui.preferences.components.SwitchPreference
import app.infinity.mpvz.ui.utils.LocalBackStack
import app.infinity.mpvz.ui.utils.currentMpvConfigOverrideOptions
import app.infinity.mpvz.ui.utils.popSafely
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object RexVisionPreferencesScreen : Screen {
  @Composable
  override fun Content() {
    val decoder = koinInject<DecoderPreferences>()
    val preferences = decoder.rexVision
    val enabled by preferences.enabled.collectAsState()
    val adaptive by preferences.adaptive.collectAsState()
    val selected by preferences.algorithms.collectAsState()
    val preset by preferences.preset.collectAsState()
    val diagnostics by RexVisionDiagnostics.state.collectAsState()
    val configOwned = currentMpvConfigOverrideOptions()
    val backStack = LocalBackStack.current
    RexScreen("REX Vision") {
    item {
      PreferenceCard {
        Text("Advanced Video Enhancement Engine", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        Text("Cinematic defaults: source cadence, no interpolation, no added grain, restrained restoration and no SDR-to-HDR boost. REX uses native MPV passes; it does not replace the decoder or create a CPU frame pipeline.", modifier = Modifier.padding(horizontal = 16.dp))
        SwitchPreference(enabled, { preferences.enabled.set(it) }, title = { Text("Enable REX Vision") }, summary = { Text("Opt-in. Turning off restores the pre-REX player settings. Changes apply during MPV playback, within two seconds; inactive playback uses them on the next start.") })
        ListPreference(preset, { preferences.selectPreset(it) }, listOf("Battery Saver", "Balanced", "High Quality", "Maximum Quality"), title = { Text("Performance preset") }, summary = { Text(preset) })
        SwitchPreference(adaptive, { preferences.adaptive.set(it) }, title = { Text("Automatic performance budget") }, summary = { Text("Reduce GPU work on sustained frame drops, Android thermal pressure or battery saver. Recover after 60 seconds of stable playback. Does not change saved controls.") })
        Text(diagnostics.message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
        if (!diagnostics.probed) Text("Device capabilities are not yet verified. Play an MPV video once to unlock verified controls. Presets can be saved now; unavailable stages will be skipped at playback.", modifier = Modifier.padding(16.dp))
        diagnostics.errors.forEach { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { backStack.add(DecoderInformationScreen) }) { Text("Decoder Information") }
      }
    }
    RexVisionPolicy.algorithms.groupBy { it.category }.forEach { (category, algorithms) ->
      item(key = category) {
        var expanded by rememberSaveable(category) { mutableStateOf(false) }
        PreferenceCard {
          TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text("${if (expanded) "−" else "+"} $category (${algorithms.size})")
          }
          AnimatedVisibility(expanded) {
            Column {
              algorithms.forEach { algorithm ->
                val reason = algorithm.unavailable
                  ?: if (algorithm.options.any { it in configOwned }) "Owned by mpv.conf; release option ownership before using REX."
                  else diagnostics.unavailable[algorithm.id]
                    ?: if (!diagnostics.probed) "Backend support not yet verified." else null
                RexAlgorithmControl(algorithm, selected.contains(algorithm.id), enabled && reason == null, reason,
                  onToggle = { checked ->
                    preferences.algorithms.set(RexVisionPolicy.selection(preferences.algorithms.get(), algorithm.id, checked))
                    preferences.preset.set("Custom")
                  },
                )
              }
            }
          }
        }
      }
    }
    item {
      Text("Unsupported techniques are intentionally disabled, not simulated with sharpening or fake switches. An enabled request is not proof a pass ran: consult active native options and pipeline diagnostics. Native renderer defaults may already include some operations.", modifier = Modifier.padding(16.dp))
    }
    }
  }
}

@Composable
private fun RexAlgorithmControl(algorithm: RexAlgorithm, selected: Boolean, editable: Boolean, reason: String?, onToggle: (Boolean) -> Unit) {
  val preferences = koinInject<DecoderPreferences>().rexVision
  val valuePreference = preferences.values.getValue(algorithm.id)
  val stored by valuePreference.collectAsState()
  Column {
    SwitchPreference(selected && reason == null, onToggle,
      title = { Text(algorithm.title) }, enabled = editable,
      summary = { Text("${algorithm.description}\nPerformance: ${algorithm.impact}${reason?.let { "\nUnsupported / unavailable: $it${if (selected) " (saved request retained)" else ""}" }.orEmpty()}") },
    )
    if (algorithm.choices.isNotEmpty()) {
      ListPreference(stored, { valuePreference.set(it); preferences.preset.set("Custom") }, algorithm.choices,
        title = { Text("${algorithm.title}: mode") }, enabled = editable && selected,
        summary = { Text(if (stored == "auto") "Automatic (MPV renderer)" else stored) },
      )
    }
    algorithm.range?.let { range ->
      val persisted = (stored.toIntOrNull() ?: range.first).coerceIn(range).toFloat()
      var slider by remember(stored) { mutableFloatStateOf(persisted) }
      Text("${algorithm.title}: ${slider.roundToInt()}", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
      GlassSlider(value = slider, onValueChange = { slider = it },
        onValueChangeFinished = { valuePreference.set(slider.roundToInt().toString()); preferences.preset.set("Custom") },
        enabled = editable && selected, valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0), modifier = Modifier.padding(horizontal = 16.dp),
      )
    }
    PreferenceDivider()
  }
}

@Serializable
object DecoderInformationScreen : Screen {
  @Composable
  override fun Content() {
    val snapshot by RexVisionDiagnostics.state.collectAsState()
    RexScreen("Decoder Information") {
    item { Text(snapshot.message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
    item { Text(if (snapshot.running) "Live MPV snapshot · refreshed every 2 seconds" else "Inactive · last MPV snapshot, not current Native Media3 output", modifier = Modifier.padding(16.dp)) }
    listOf("Source information (before output processing)" to snapshot.source, "Playback output & processing" to snapshot.output).forEach { (title, facts) ->
      item {
        PreferenceCard {
          Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
          if (facts.isEmpty()) Text("Unavailable — play a video using the MPV engine first.", modifier = Modifier.padding(16.dp))
          facts.forEach { (label, value) ->
            SelectionContainer {
              Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RexScreen(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  val backStack = LocalBackStack.current
  SettingsScaffold(topBar = {
    SettingsTopAppBar(title = { Text(title) }, navigationIcon = {
      TextButton(onClick = { backStack.popSafely() }) { Text("Back") }
    })
  }) { padding ->
    ProvidePreferenceLocals {
      LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
  }
}

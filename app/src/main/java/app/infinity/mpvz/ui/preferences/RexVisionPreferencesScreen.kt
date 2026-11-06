/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.infinity.mpvz.preferences.DecoderPreferences
import app.infinity.mpvz.preferences.preference.collectAsState
import app.infinity.mpvz.presentation.Screen
import app.infinity.mpvz.ui.player.RexAlgorithm
import app.infinity.mpvz.ui.player.RexImplementationTier
import app.infinity.mpvz.ui.player.RexVisionDiagnostics
import app.infinity.mpvz.ui.player.RexVisionPolicy
import app.infinity.mpvz.ui.player.controls.components.GlassSlider
import app.infinity.mpvz.ui.player.controls.components.GlassSwitch
import app.infinity.mpvz.ui.preferences.components.ListPreference
import app.infinity.mpvz.ui.preferences.components.SettingsScaffold as Scaffold
import app.infinity.mpvz.ui.preferences.components.SettingsTopAppBar as TopAppBar
import app.infinity.mpvz.ui.preferences.components.SwitchPreference
import app.infinity.mpvz.ui.utils.LocalBackStack
import app.infinity.mpvz.ui.utils.currentMpvConfigOverrideOptions
import app.infinity.mpvz.ui.utils.popSafely
import app.infinity.mpvz.utils.clipboard.SafeClipboard
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
    val bypassOriginal by preferences.bypassToOriginal.collectAsState()
    val adaptive by preferences.adaptive.collectAsState()
    val selected by preferences.algorithms.collectAsState()
    val preset by preferences.preset.collectAsState()
    val diagnostics by RexVisionDiagnostics.state.collectAsState()
    val configOwned = currentMpvConfigOverrideOptions()
    val backStack = LocalBackStack.current
    RexScreen("REX Vision") {
      // JSON-based code method at the very top of the page
      item(key = "rex_json_top_card") {
        RexJsonConfigurationCard()
      }
      item(key = "rex_overview_card") {
        PreferenceCard {
          Text(
            "GMPV INFINITY — Complete Video Enhancement Engine (${RexVisionPolicy.TOTAL_ALGORITHM_COUNT} Algorithms)",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
          )
          Text(
            "Global defaults: Preserve original cinematic FPS (24 FPS) · Frame interpolation disabled by default · All 121 algorithms individually configurable · GPU acceleration wherever possible · Experimental algorithms accessible · No fake settings · One-tap bypass to original unprocessed video.",
            modifier = Modifier.padding(horizontal = 16.dp),
          )
          SwitchPreference(
            enabled,
            { preferences.enabled.set(it) },
            title = { Text("Enable REX Vision") },
            summary = {
              Text("Opt-in. Turning off restores the pre-REX player settings. Changes apply during MPV playback, within two seconds; inactive playback uses them on the next start.")
            },
          )
          SwitchPreference(
            bypassOriginal,
            { preferences.bypassToOriginal.set(it) },
            title = { Text("One-Tap Bypass to Original Unprocessed Video") },
            summary = {
              Text("Instantly bypass all enhancement passes to view original unprocessed video at native 24 FPS cadence while retaining your saved 121-algorithm selection.")
            },
          )
          ListPreference(
            preset,
            { preferences.selectPreset(it) },
            listOf("Battery Saver", "Balanced", "High Quality", "Maximum Quality"),
            title = { Text("Performance preset") },
            summary = { Text(preset) },
          )
          SwitchPreference(
            adaptive,
            { preferences.adaptive.set(it) },
            title = { Text("Automatic performance budget") },
            summary = {
              Text("Reduce GPU work on sustained frame drops, Android thermal pressure or battery saver. Recover after 60 seconds of stable playback. Does not change saved controls.")
            },
          )
          Text(diagnostics.message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
          if (!diagnostics.probed) {
            Text(
              "Device capabilities are not yet verified. Play an MPV video once to unlock verified controls. Presets can be saved now; unavailable stages will be skipped at playback.",
              modifier = Modifier.padding(16.dp),
            )
          }
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
                  RexAlgorithmControl(
                    algorithm = algorithm,
                    selected = selected.contains(algorithm.id),
                    editable = true,
                    reason = reason,
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
        Text(
          "Unsupported techniques are intentionally reported with their native status, not simulated with sharpening or fake switches. An enabled request is not proof a pass ran: consult active native options and pipeline diagnostics.",
          modifier = Modifier.padding(16.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RexJsonConfigurationCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val decoder = koinInject<DecoderPreferences>()
  val preferences = decoder.rexVision
  val selected by preferences.algorithms.collectAsState()
  val enabled by preferences.enabled.collectAsState()
  val bypassOriginal by preferences.bypassToOriginal.collectAsState()

  var jsonExpanded by rememberSaveable { mutableStateOf(true) }
  var jsonInput by rememberSaveable {
    mutableStateOf(
      """{"masterEnabled": true, "bypassToOriginal": false, "enable": [1, 4, 5, 6, 8, 17, 21, 22, 23], "disable": [12]}""",
    )
  }
  var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var isError by rememberSaveable { mutableStateOf(false) }

  PreferenceCard(modifier = modifier) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "JSON Algorithm Code Controller (1–121)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Enable/disable selective algorithms via JSON (by index 1..121, id, or title) • Active: ${selected.size}/${RexVisionPolicy.TOTAL_ALGORITHM_COUNT}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(onClick = { jsonExpanded = !jsonExpanded }) {
          Text(if (jsonExpanded) "Hide JSON" else "Show JSON")
        }
      }

      // One-Tap Bypass & Master Enable Quick Row
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (bypassOriginal) "Bypass Active: Original Unprocessed Video (24 FPS)" else "One-Tap Bypass to Original Video",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Preserve original 24 FPS cinematic cadence · Frame interpolation off by default",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          GlassSwitch(
            checked = bypassOriginal,
            onCheckedChange = { preferences.bypassToOriginal.set(it) },
          )
        }
      }

      AnimatedVisibility(visible = jsonExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = jsonInput,
            onValueChange = { jsonInput = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            label = { Text("JSON Selective Enable / Disable Code") },
            placeholder = {
              Text("""{"enable": [1, 6, "sigmoid"], "disable": [12, "sharpening"], "bypassToOriginal": false}""")
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Button(
              onClick = {
                val res = preferences.applyJson(jsonInput)
                if (res.error != null) {
                  isError = true
                  statusMessage = res.error
                } else {
                  isError = false
                  statusMessage = "Applied JSON configuration (${res.enabledIds.size} algorithms enabled)."
                }
              },
            ) {
              Text("Apply JSON")
            }
            OutlinedButton(
              onClick = {
                jsonInput = preferences.exportJson()
                isError = false
                statusMessage = "Loaded current 121-algorithm state into JSON editor."
              },
            ) {
              Text("Load Current JSON")
            }
            OutlinedButton(
              onClick = {
                val exported = preferences.exportJson()
                SafeClipboard.copyPlainText(context, "GMPV Infinity Algorithms JSON", exported)
                Toast.makeText(context, "Copied JSON configuration", Toast.LENGTH_SHORT).show()
              },
            ) {
              Text("Copy JSON")
            }
            OutlinedButton(
              onClick = {
                preferences.algorithms.set(RexVisionPolicy.defaults)
                preferences.bypassToOriginal.set(false)
                preferences.preset.set("Balanced")
                jsonInput = preferences.exportJson()
                isError = false
                statusMessage = "Reset to Global Cinematic Defaults (24 FPS preserved, interpolation off)."
              },
            ) {
              Text("Cinematic Defaults")
            }
          }

          statusMessage?.let { msg ->
            Text(
              text = msg,
              style = MaterialTheme.typography.labelMedium,
              color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

/**
 * Embeds the complete 121-algorithm GMPV Infinity catalogue with individual on/off switches,
 * implementation tiers (Tier 1, Tier 2, Tier 3), and global defaults directly into a LazyColumn
 * (used in Settings -> Codecs and REX Vision).
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.rexCatalogueItems(
  selected: Set<String>,
  onToggleAlgorithm: (RexAlgorithm, Boolean) -> Unit,
  onEnableTier: (RexImplementationTier, Boolean) -> Unit,
) {
  item(key = "rex_catalogue_tier_header") {
    PreferenceCard {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "GMPV INFINITY — COMPLETE VIDEO ENHANCEMENT ALGORITHM CATALOGUE (${RexVisionPolicy.TOTAL_ALGORITHM_COUNT})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "Implementation Tiers: Tier 1 (Real-Time Native MPV / GPU) · Tier 2 (Advanced GPU / Multi-Frame) · Tier 3 (Experimental AI / Neural Restoration)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          RexImplementationTier.entries.forEach { tier ->
            val tierAlgs = RexVisionPolicy.algorithms.filter { it.tier == tier }
            val activeInTier = tierAlgs.count { it.id in selected }
            OutlinedButton(
              onClick = {
                val shouldEnable = activeInTier < tierAlgs.size / 2
                onEnableTier(tier, shouldEnable)
              },
            ) {
              Text("Tier ${tier.code} ($activeInTier/${tierAlgs.size})")
            }
          }
        }
      }
    }
  }

  RexImplementationTier.entries.forEach { tier ->
    val tierAlgorithms = RexVisionPolicy.algorithms.filter { it.tier == tier }
    item(key = "rex_tier_${tier.code}") {
      var expanded by rememberSaveable(tier.name) { mutableStateOf(tier == RexImplementationTier.TIER_1) }
      val enabledCount = tierAlgorithms.count { it.id in selected }
      PreferenceCard {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(
              onClick = { expanded = !expanded },
              modifier = Modifier.weight(1f),
            ) {
              Text(
                text = "${if (expanded) "−" else "+"} ${tier.label} ($enabledCount/${tierAlgorithms.size} ON)",
                fontWeight = FontWeight.Bold,
              )
            }
          }
          AnimatedVisibility(visible = expanded) {
            Column {
              tierAlgorithms.forEach { algorithm ->
                val isChecked = algorithm.id in selected
                SwitchPreference(
                  value = isChecked,
                  onValueChange = { checked -> onToggleAlgorithm(algorithm, checked) },
                  enabled = true,
                  title = {
                    Text(
                      text = "${algorithm.index}. ${algorithm.title}",
                      fontWeight = FontWeight.SemiBold,
                    )
                  },
                  summary = {
                    val statusNote = algorithm.unavailable?.let { " · Native note: $it" }.orEmpty()
                    Text(
                      text = "[Tier ${algorithm.tier.code} · ${algorithm.category} · Impact: ${algorithm.impact}] ${algorithm.description}$statusNote",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
                PreferenceDivider()
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RexAlgorithmControl(
  algorithm: RexAlgorithm,
  selected: Boolean,
  editable: Boolean,
  reason: String?,
  onToggle: (Boolean) -> Unit,
) {
  val preferences = koinInject<DecoderPreferences>().rexVision
  val valuePreference = preferences.values.getValue(algorithm.id)
  val stored by valuePreference.collectAsState()
  Column {
    SwitchPreference(
      value = selected,
      onValueChange = onToggle,
      title = { Text("${algorithm.index}. ${algorithm.title}") },
      enabled = editable,
      summary = {
        Text(
          "[${algorithm.tier.label}]\n${algorithm.description}\nPerformance: ${algorithm.impact}${
            reason?.let { "\nNative status: $it${if (selected) " (saved request retained)" else ""}" }.orEmpty()
          }",
        )
      },
    )
    if (algorithm.choices.isNotEmpty()) {
      ListPreference(
        stored,
        {
          valuePreference.set(it)
          preferences.preset.set("Custom")
        },
        algorithm.choices,
        title = { Text("${algorithm.title}: mode") },
        enabled = editable && selected,
        summary = { Text(if (stored == "auto") "Automatic (MPV renderer)" else stored) },
      )
    }
    algorithm.range?.let { range ->
      val persisted = (stored.toIntOrNull() ?: range.first).coerceIn(range).toFloat()
      var slider by remember(stored) { mutableFloatStateOf(persisted) }
      Text(
        "${algorithm.title}: ${slider.roundToInt()}",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.labelLarge,
      )
      GlassSlider(
        value = slider,
        onValueChange = { slider = it },
        onValueChangeFinished = {
          valuePreference.set(slider.roundToInt().toString())
          preferences.preset.set("Custom")
        },
        enabled = editable && selected,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        modifier = Modifier.padding(horizontal = 16.dp),
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
      item {
        Text(
          if (snapshot.running) "Live MPV snapshot · refreshed every 2 seconds"
          else "Inactive · last MPV snapshot, not current Native Media3 output",
          modifier = Modifier.padding(16.dp),
        )
      }
      listOf(
        "Source information (before output processing)" to snapshot.source,
        "Playback output & processing" to snapshot.output,
      ).forEach { (title, facts) ->
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
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          TextButton(onClick = { backStack.popSafely() }) { Text("Back") }
        },
      )
    },
  ) { padding ->
    ProvidePreferenceLocals {
      LazyColumn(
        Modifier
          .fillMaxSize()
          .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
      )
    }
  }
}

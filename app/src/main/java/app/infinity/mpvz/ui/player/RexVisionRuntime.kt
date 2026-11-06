/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.player

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.os.PowerManager
import app.infinity.mpvz.preferences.DecoderPreferences
import app.infinity.mpvz.preferences.MpvConfigOverridePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class RexVisionSnapshot(
  val running: Boolean = false,
  val message: String = "Start an MPV video to inspect this device/backend. Settings are saved between sessions.",
  val unavailable: Map<String, String> = emptyMap(),
  val probed: Boolean = false,
  val active: List<String> = emptyList(),
  val source: Map<String, String> = emptyMap(),
  val output: Map<String, String> = emptyMap(),
  val errors: List<String> = emptyList(),
)

/** UI sees only cached native facts; opening settings never creates an EGL context or decoder. */
internal object RexVisionDiagnostics {
  private val mutable = MutableStateFlow(RexVisionSnapshot())
  val state = mutable.asStateFlow()
  fun publish(snapshot: RexVisionSnapshot) { mutable.value = snapshot }
}

/**
 * A controller for existing renderer options, not an extra renderer. MPV/libplacebo owns the
 * actual order: decode/import -> native deband -> scale/antiring -> gpu-only sharpen -> colour
 * mapping -> output dither. Display interpolation uses MPV's synchronized VO queue. No vf,
 * shader-list replacement, CPU readback, invented optical flow or second tone-map pass.
 *
 * Owned options are restored on release and on master disable. Failed groups roll back together.
 * Calls are serialized by the owning view on Main and PlaybackSession's native lock.
 */
internal class RexVisionRuntime(context: Context, private val preferences: DecoderPreferences) {
  private val power = context.applicationContext.getSystemService(PowerManager::class.java)
  private val shaderManager = RexVisionShaderManager(context, PlaybackSessionShaderRuntime)
  private val mediaCodecs = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder && it.supportedTypes.any { mime -> mime.startsWith("video/") } }.map { it.name }
      .filterNot { name -> name.startsWith("OMX.google.") || name.startsWith("c2.android.") }
  }.getOrDefault(emptyList())
  private val baseline = linkedMapOf<String, String>()
  private val applied = linkedMapOf<String, String>()
  private val rejected = linkedMapOf<String, String>()
  private val supportCache = mutableMapOf<String, Boolean>()
  private var rendererKey = ""
  private var budget = RexVisionBudget()
  private var previousDropped: Long? = null
  private var generation = -1L

  private fun read(name: String): String? = runCatching { PlaybackSession.getPropertyString(name) }.getOrNull()
  private fun option(name: String): String? = read("options/$name")
  private fun supported(name: String): Boolean = supportCache.getOrPut(name) {
    read("option-info/$name/name") != null && option(name) != null
  }
  private fun owned(name: String) = MpvConfigOverridePolicy.isOwnedByMpvConf(name)

  private fun equivalent(actual: String?, value: String): Boolean = actual == value ||
    (actual?.toDoubleOrNull() != null && actual.toDoubleOrNull() == value.toDoubleOrNull())

  private fun write(name: String, value: String): Boolean {
    if (owned(name)) return false
    return runCatching {
      PlaybackSession.setPropertyString("options/$name", value)
      val actual = option(name)
      equivalent(actual, value)
    }.getOrDefault(false)
  }

  private fun restore(name: String) {
    val saved = baseline[name] ?: return
    // Do not clobber a later change from another player control or mpv.conf owner.
    if (owned(name) || option(name) != applied[name]) {
      applied.remove(name)
      baseline.remove(name)
    } else if (write(name, saved)) {
      applied.remove(name)
      baseline.remove(name)
    }
  }

  fun stop() {
    shaderManager.clear()
    applied.keys.toList().asReversed().forEach(::restore)
    val old = RexVisionDiagnostics.state.value
    RexVisionDiagnostics.publish(old.copy(running = false, active = emptyList(), message = if (applied.isEmpty()) "MPV surface inactive; last observed device capabilities and source/output facts shown." else "MPV surface inactive. Could not restore native options: ${applied.keys.joinToString()}. Restart playback before switching engines."))
  }

  fun tick() {
    if (!PlaybackSession.isInitialized) return
    val vo = read("current-vo") ?: read("vo") ?: "unknown"
    val key = "$vo/${option("gpu-api")}/${read("mpv-version")}"
    if (rendererKey != key) {
      applied.keys.toList().asReversed().forEach(::restore)
      rejected.clear()
      supportCache.clear()
      rendererKey = key
    }
    val state = PlaybackSession.state.value
    if (generation != state.generation) {
      previousDropped = null
      budget = RexVisionBudget()
      rejected.clear()
      supportCache.clear()
      generation = state.generation
    }
    val gpu = vo == "gpu" || vo == "gpu-next"
    val external = when {
      preferences.mpvFlowFrameInterpolation.get() -> "Existing MPVFlow owns the temporal pipeline. Disable it before using REX."
      preferences.enableAnime4K.get() && preferences.anime4kMode.get() != "OFF" -> "Anime4K owns reconstruction. Disable it before using REX to avoid double processing."
      preferences.hdrScreenOutput.get() || preferences.hdrScreenMode.get() != HdrScreenMode.OFF -> "Existing HDR shader output is enabled. Disable it before using REX colour processing."
      preferences.debanding.get() == Debanding.CPU -> "CPU debanding is enabled. Disable it before using REX to avoid duplicate processing."
      !gpu -> "Current renderer is $vo, not a supported MPV GPU renderer. Native Media3 is not controlled by REX."
      !state.surfaceAttached -> "No active MPV surface; start MPV playback to apply REX."
      else -> null
    }
    val requested = preferences.rexVision.algorithms.get()
    val unavailable = RexVisionPolicy.algorithms.mapNotNull { a ->
      val reason = a.unavailable ?: external ?: when {
        a.id == "sharpening" && vo != "gpu" -> "MPV --sharpen is implemented only by vo=gpu, not gpu-next."
        a.id == "deringing" && requested.none { it in RexVisionPolicy.scalers } -> "Select a convolution scaler first; anti-ringing has no effect on bilinear scaling."
        a.id == "hardware_decoding" && mediaCodecs.isEmpty() -> "No platform MediaCodec candidates found. Software decoding remains available."
        a.options.any(::owned) -> "Owned by mpv.conf; change option ownership first."
        a.options.any { !supported(it) } -> "Required native option unavailable on this MPV build/backend."
        a.id in rejected -> rejected[a.id]
        else -> null
      }
      reason?.let { a.id to it }
    }.toMap().toMutableMap()
    val drops = read("frame-drop-count")?.toLongOrNull()
    val delta = if (drops != null && previousDropped != null) (drops - previousDropped!!).coerceAtLeast(0) else 0
    previousDropped = drops
    val hot = (Build.VERSION.SDK_INT >= 29 && (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE) || power?.isPowerSaveMode == true
    val severe = Build.VERSION.SDK_INT >= 29 && (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE
    val constrained = severe || (preferences.rexVision.adaptive.get() && budget.sample(delta, hot, state.paused))
    val bypassOriginal = preferences.rexVision.bypassToOriginal.get()
    val enabled = requested - unavailable.keys
    val transfer = read("video-dec-params/gamma")?.lowercase().orEmpty()
    val hdrSource = transfer in setOf("pq", "hlg", "smpte-st-2084", "arib-std-b67")
    val shaderIds = enabled.filter { id -> RexVisionPolicy.algorithms.firstOrNull { it.id == id }?.shaderPasses?.isNotEmpty() == true }.toSet()
    val shaderResult = if (preferences.rexVision.enabled.get() && !bypassOriginal && external == null && "gpu_shaders" in enabled) shaderManager.apply(shaderIds, hdrSource) else { shaderManager.clear(); RexShaderApplyResult(emptySet(), emptyMap(), emptySet()) }
    shaderResult.unavailable.forEach { (id, reason) -> unavailable[id] = reason }
    val desired = if (preferences.rexVision.enabled.get() && !bypassOriginal && external == null) {
      RexVisionPolicy.plan(enabled, preferences.rexVision.snapshot(), constrained).filterKeys { name ->
        !owned(name) && supported(name) && RexVisionPolicy.algorithms.any { name in it.options && it.id !in unavailable }
      }
    } else emptyMap()
    val errors = mutableListOf<String>()
    // Remove stages no longer requested, without disturbing unrelated MPV/Anime4K/HDR options.
    (applied.keys - desired.keys).toList().asReversed().forEach(::restore)
    for ((name, value) in desired) {
      if (applied[name] != null && equivalent(applied[name], value) && equivalent(option(name), value)) continue
      if (RexVisionPolicy.algorithms.any { name in it.options && it.id in rejected }) continue
      if (name !in baseline) option(name)?.let { baseline[name] = it }
      if (name !in baseline) continue
      if (write(name, value)) {
        // Store the renderer's normalized representation (eg 0.700000 rather than 0.7).
        applied[name] = option(name) ?: value
      } else {
        val reason = "Native renderer rejected $name=$value; this stage was rolled back."
        errors += reason
        val affected = RexVisionPolicy.algorithms.filter { name in it.options }
        // Include a potentially partially-applied write in rollback.
        applied[name] = option(name) ?: value
        affected.forEach { a ->
          rejected[a.id] = reason
          unavailable[a.id] = reason
          a.options.toList().asReversed().forEach(::restore)
        }
      }
    }
    (applied.keys - desired.keys).forEach { name -> errors += "Native option restoration pending: $name. Restart playback if this persists." }
    rejected.keys.forEach { id ->
      RexVisionPolicy.algorithms.first { it.id == id }.options.toList().asReversed().forEach(::restore)
    }
    val active = if (preferences.rexVision.enabled.get() && !bypassOriginal && external == null) RexVisionPolicy.algorithms.filter { a ->
      a.id in enabled && a.id !in unavailable && (
        a.id == "gpu_shaders" ||
          (a.options.isNotEmpty() && a.options.all { applied[it] != null }) ||
          (a.shaderPasses.isNotEmpty() && a.id in shaderResult.activeAlgorithms)
      ) &&
        !(constrained && a.id in setOf("interpolation", "debanding", "sigmoid", "sharpening"))
    }.map { it.title } else emptyList()
    val hdr = when { transfer.isEmpty() -> "Unavailable"; hdrSource -> "HDR"; else -> "SDR" }
    fun facts(vararg fields: Pair<String, String>): Map<String, String> = fields.associate { (label, prop) -> label to (read(prop) ?: "Unavailable") }
    val source = facts(
      "Video codec" to "current-tracks/video/codec", "Source width" to "video-dec-params/w", "Source height" to "video-dec-params/h",
      "Source FPS (container)" to "container-fps",
      "Video bitrate (bit/s, if reported)" to "video-bitrate", "Colour primaries" to "video-dec-params/primaries",
      "Transfer characteristics" to "video-dec-params/gamma", "Matrix" to "video-dec-params/colormatrix", "Range" to "video-dec-params/colorlevels",
    ) + ("HDR/SDR" to hdr)
    val hwdec = read("hwdec-current")
    val output = facts(
      "MPV version" to "mpv-version", "Decoder implementation" to "current-tracks/video/decoder-desc",
      "Active hardware decoder / copy mode" to "hwdec-current", "Rendering backend" to "current-vo",
      "GPU API (requested)" to "options/gpu-api", "GPU context (requested)" to "options/gpu-context",
      "GPU renderer (if exposed)" to "gpu-context/renderer", "Active GPU context" to "current-gpu-context",
      "Render surface width" to "osd-dimensions/w", "Render surface height" to "osd-dimensions/h",
      "Post-filter frame width (before VO scaling)" to "video-out-params/w", "Post-filter frame height (before VO scaling)" to "video-out-params/h",
      "VO target primaries (if exposed)" to "video-target-params/primaries", "VO target transfer (if exposed)" to "video-target-params/gamma",
      "Estimated post-filter FPS (not presentation FPS)" to "estimated-vf-fps",
      "Display refresh Hz (not source FPS)" to "display-fps", "Estimated display Hz" to "estimated-display-fps",
      "Measured presentation FPS (if reported)" to "output-fps", "Active video synchronisation" to "video-sync-active",
      "Dropped output frames" to "frame-drop-count", "Decoder dropped frames" to "decoder-frame-drop-count",
      "Delayed frames" to "vo-delayed-frame-count", "A/V difference (s)" to "avsync",
      "GPU pass timing/load (if exposed)" to "vo-passes",
    ) + mapOf(
      "Active decoder" to when { hwdec == null -> "Unavailable"; hwdec == "no" -> "Software"; hwdec.startsWith("mediacodec") -> "MediaCodec"; else -> "Other: $hwdec" },
      "Hardware acceleration" to when (hwdec) { null -> "Unavailable"; "no" -> "Inactive"; else -> "Active ($hwdec)" },
      "MediaCodec candidates (not proof of codec/profile support)" to mediaCodecs.joinToString().ifEmpty { "None reported" },
      "Original video bypass" to if (bypassOriginal) "Active (One-tap bypass to original unprocessed video)" else "Inactive",
      "Adaptive quality" to if (constrained) "Reduced: thermal/power-save or sustained frame drops" else "Requested quality",
      "Processing load" to "No portable GPU utilization counter; frame drops, thermal state and available pass timings are used.",
      "Enabled REX stages (verified options; content-dependent)" to active.joinToString().ifEmpty { "None" },
      "Native filter chain" to (read("vf") ?: "Unavailable"),
      "External shader chain" to (read("glsl-shaders") ?: "Unavailable"),
      "Native REX options" to applied.entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "None" },
      "REX GLSL passes" to shaderResult.activePasses.joinToString().ifEmpty { "None" },
      "Pipeline" to "Decode/import → optional REX single-frame GLSL spatial passes → native MPV scaling/colour/dither → timed display. No frame interpolation or temporal shader history is claimed.",
    )
    RexVisionDiagnostics.publish(RexVisionSnapshot(
      running = state.surfaceAttached, probed = true, unavailable = unavailable, active = active, source = source, output = output,
      errors = errors + rejected.values.distinct(),
      message = external ?: when {
        !preferences.rexVision.enabled.get() -> "REX disabled; existing playback settings are unchanged. Toggle on to apply."
        bypassOriginal -> "One-tap bypass active: showing original unprocessed video (24 FPS cadence preserved)."
        constrained -> "Adaptive fallback active; source timing and audio sync preserved."
        else -> "Native MPV processing. REX switches control the native passes; master-off restores prior player settings. No forced output FPS."
      },
    ))
  }
}

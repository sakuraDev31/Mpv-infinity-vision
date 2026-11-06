/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.player

/** Native renderer stages, not a second frame queue or a CPU filter graph. */
internal data class RexAlgorithm(
  val id: String,
  val title: String,
  val category: String,
  val description: String,
  val impact: String,
  val options: Set<String> = emptySet(),
  val choices: List<String> = emptyList(),
  val range: IntRange? = null,
  val unavailable: String? = null,
)

internal object RexVisionPolicy {
  val scalers = setOf("lanczos", "spline36", "mitchell")
  val defaults = setOf("spline36", "correct_downscaling", "tone_mapping", "dithering", "hardware_decoding", "gpu_shaders")
  val algorithms = listOf(
    RexAlgorithm("lanczos", "Lanczos Scaling", "Image Scaling & Reconstruction", "Windowed-sinc reconstruction. Select one scaler; radius controls taps.", "Medium", setOf("scale", "scale-radius"), range = 2..4),
    RexAlgorithm("spline36", "Spline36 Scaling", "Image Scaling & Reconstruction", "Six-tap spline reconstruction with restrained ringing. Select one scaler.", "Medium", setOf("scale")),
    RexAlgorithm("mitchell", "Mitchell-Netravali Scaling", "Image Scaling & Reconstruction", "Soft bicubic reconstruction that avoids aggressive sharpening. Select one scaler.", "Low", setOf("scale")),
    RexAlgorithm("sigmoid", "Sigmoid Upscaling", "Image Scaling & Reconstruction", "Sigmoidal light domain reduces scaling halos; only used during upscaling.", "Medium", setOf("sigmoid-upscaling")),
    RexAlgorithm("correct_downscaling", "Correct Downscaling", "Image Scaling & Reconstruction", "Linear-light downscaling with antialiased filter support.", "Medium", setOf("dscale", "linear-downscaling", "correct-downscaling")),
    RexAlgorithm("debanding", "Debanding", "Compression & Image Restoration", "GPU gradient smoothing; low thresholds preserve texture. Strength is MPV's threshold.", "Medium", setOf("deband", "deband-threshold", "deband-iterations", "deband-grain"), range = 4..48),
    RexAlgorithm("deringing", "Deringing", "Compression & Image Restoration", "Scaler anti-ringing clamp, not restoration of ringing baked into the source.", "Low", setOf("scale-antiring"), range = 0..100),
    RexAlgorithm("deblocking", "Deblocking", "Compression & Image Restoration", "Remove block-boundary compression artefacts.", "High", unavailable = "No independent zero-copy deblocking stage is integrated. Codec in-loop deblocking remains decoder-controlled."),
    RexAlgorithm("denoising", "Denoising", "Compression & Image Restoration", "Remove spatial noise while retaining fine texture.", "High", unavailable = "No validated spatial denoiser is integrated; CPU lavfi filters would force frame downloads on hardware decode."),
    RexAlgorithm("sharpening", "Sharpening", "Compression & Image Restoration", "MPV renderer sharpening, limited to a conservative 0–0.3 to reduce halos.", "Low", setOf("sharpen"), range = 0..30),
    RexAlgorithm("grain", "Grain Restoration", "Compression & Image Restoration", "Restore natural grain without destroying detail.", "High", unavailable = "Deband noise and codec film-grain synthesis are not grain restoration; no restoration model is integrated."),
    RexAlgorithm("interpolation", "Frame Interpolation", "Temporal & Motion Processing", "MPV display-timed frame blending, NOT motion estimation. Off by default; explicitly enabling changes presentation cadence.", "High", setOf("interpolation", "video-sync", "tscale")),
    RexAlgorithm("resampling", "Display Resampling", "Temporal & Motion Processing", "Resample the audio clock to the display clock without synthesizing video frames. Shares video-sync with Display-Resample Synchronisation.", "Low", setOf("video-sync")),
    RexAlgorithm("motion_deblur", "Motion Deblurring", "Temporal & Motion Processing", "Recover detail lost to exposure-time motion blur, not ordinary sharpening.", "High", unavailable = "No motion-aware exposure deconvolution is integrated."),
    RexAlgorithm("temporal_denoise", "Temporal Denoising", "Temporal & Motion Processing", "Motion-aligned multi-frame noise reduction.", "High", unavailable = "No history-buffer, scene-cut and motion-aligned denoiser is integrated."),
    RexAlgorithm("motion_compensated", "Motion-Compensated Processing", "Temporal & Motion Processing", "Motion-estimated processing using synchronized neighbouring frames.", "High", unavailable = "No independent REX stage. Existing MPVFlow is configured separately in Decoder settings and must not be stacked with REX interpolation."),
    RexAlgorithm("tone_mapping", "Tone Mapping", "Lighting, Colour & HDR", "Native HDR luminance mapping. Auto follows MPV; never promotes SDR to artificial HDR.", "Medium", setOf("tone-mapping"), listOf("auto", "bt.2390", "hable", "reinhard", "clip")),
    RexAlgorithm("hdr_sdr", "HDR-to-SDR Conversion", "Lighting, Colour & HDR", "Explicit SDR target override; off leaves native automatic output handling. Mapping uses the same native colour pass, not a second conversion.", "Medium", setOf("target-trc", "target-prim")),
    RexAlgorithm("gamma", "Gamma Correction", "Lighting, Colour & HDR", "Small native gamma adjustment. Zero is neutral; disabled is neutral while REX is active.", "Low", setOf("gamma"), range = -20..20),
    RexAlgorithm("colour_management", "Colour Management", "Lighting, Colour & HDR", "Native output-primary conversion. When off, the existing target is restored. Auto uses the renderer's target; not calibrated Android ICC support.", "Low", setOf("target-prim"), listOf("auto", "bt.709", "bt.2020")),
    RexAlgorithm("dithering", "Dithering", "Lighting, Colour & HDR", "Dither after colour conversion at the output bit depth. Auto lets MPV detect output depth.", "Low", setOf("dither-depth"), listOf("auto", "8", "10")),
    RexAlgorithm("gpu_shaders", "GPU Shader Processing", "GPU & Playback Processing", "Allow REX native GPU stages. No new CPU frame copies or custom shader chain. Does not disable the renderer or external shaders.", "Medium"),
    RexAlgorithm("hardware_decoding", "Hardware Video Decoding (MediaCodec)", "GPU & Playback Processing", "On requests MediaCodec with software fallback; off requests software decoding while REX is active. Actual copy mode is reported below; zero-copy is not guaranteed.", "Low", setOf("hwdec")),
    RexAlgorithm("display_sync", "Display-Resample Synchronisation", "GPU & Playback Processing", "The same native video-sync option as Display Resampling. Audio remains synchronized; this does not force 48 or 60 FPS.", "Low", setOf("video-sync")),
  )

  fun selection(enabled: Set<String>, id: String, value: Boolean): Set<String> {
    val syncAliases = setOf("resampling", "display_sync")
    if (id in syncAliases) return if (value) enabled + syncAliases else enabled - syncAliases - "interpolation"
    if (id == "interpolation" && value) return enabled + syncAliases + id
    val base = if (value && id in scalers) enabled - scalers else enabled
    return if (value) base + id else base - id
  }

  fun preset(name: String): Set<String> = when (name) {
    "Battery Saver" -> setOf("mitchell", "dithering", "tone_mapping", "hardware_decoding", "gpu_shaders")
    "High Quality" -> defaults - "spline36" + setOf("lanczos", "sigmoid", "deringing", "debanding")
    "Maximum Quality" -> defaults - "spline36" + setOf("lanczos", "sigmoid", "deringing", "debanding")
    else -> defaults
  }

  /** Master-on owns the native switches. Master-off restores the pre-REX player settings. */
  fun plan(enabled: Set<String>, values: Map<String, String>, constrained: Boolean): Map<String, String> = buildMap {
    put("hwdec", if ("hardware_decoding" in enabled) "mediacodec,auto" else "no")
    if ("gpu_shaders" !in enabled) return@buildMap
    fun intensity(id: String, fallback: Int, range: IntRange): Int =
      (values[id]?.toIntOrNull() ?: fallback).coerceIn(range)
    fun choice(id: String, fallback: String): String =
      values[id]?.takeIf { it in algorithms.first { a -> a.id == id }.choices } ?: fallback
    val scaler = listOf("lanczos", "spline36", "mitchell").firstOrNull { it in enabled }
    put("scale", if (constrained) "bilinear" else scaler ?: "bilinear")
    put("sigmoid-upscaling", "no")
    put("linear-downscaling", "no")
    put("correct-downscaling", "no")
    put("deband", "no")
    put("scale-antiring", "0")
    put("sharpen", "0")
    put("gamma", "0")
    put("tone-mapping", "clip")
    put("dither-depth", "no")
    if (scaler == "lanczos" && !constrained) put("scale-radius", intensity("lanczos", 3, 2..4).toString())
    if ("sigmoid" in enabled) put("sigmoid-upscaling", if (constrained) "no" else "yes")
    if ("correct_downscaling" in enabled) {
      put("dscale", if (constrained) "bilinear" else scaler ?: "spline36")
      put("linear-downscaling", if (constrained) "no" else "yes")
      put("correct-downscaling", "yes")
    }
    if ("debanding" in enabled) {
      put("deband", if (constrained) "no" else "yes")
      put("deband-threshold", intensity("debanding", 12, 4..48).toString())
      put("deband-iterations", if (values["preset"] == "Maximum Quality" && !constrained) "2" else "1")
      put("deband-grain", "0") // Never masquerade added noise as restored grain.
    }
    if ("deringing" in enabled) put("scale-antiring", (intensity("deringing", 70, 0..100) / 100.0).toString())
    if ("sharpening" in enabled) put("sharpen", if (constrained) "0" else (intensity("sharpening", 10, 0..30) / 100.0).toString())
    // Prime the native display queue before enabling blending; no explicit FPS conversion.
    put("video-sync", if (enabled.any { it in setOf("interpolation", "resampling", "display_sync") } && !constrained) "display-resample" else "audio")
    put("interpolation", if ("interpolation" in enabled && !constrained) "yes" else "no")
    if ("interpolation" in enabled && !constrained) put("tscale", "oversample")
    if ("tone_mapping" in enabled) put("tone-mapping", choice("tone_mapping", "auto"))
    if ("colour_management" in enabled) put("target-prim", choice("colour_management", "auto"))
    if ("hdr_sdr" in enabled) {
      put("target-trc", "bt.1886")
      put("target-prim", "bt.709") // SDR target overrides manual primaries; one colour pass.
    }
    if ("gamma" in enabled) put("gamma", intensity("gamma", 0, -20..20).toString())
    if ("dithering" in enabled) put("dither-depth", choice("dithering", "auto"))
  }
}

/** Hysteresis: shed work after sustained drops; recover only after a long stable interval. */
internal class RexVisionBudget {
  private var bad = 0
  private var good = 0
  var constrained = false
    private set
  fun sample(droppedDelta: Long, hot: Boolean, paused: Boolean): Boolean {
    if (paused) return constrained
    if (hot || droppedDelta >= 3) { bad++; good = 0 } else { good++; bad = 0 }
    if (hot || bad >= 3) constrained = true
    if (!hot && good >= 30) constrained = false
    return constrained
  }
}

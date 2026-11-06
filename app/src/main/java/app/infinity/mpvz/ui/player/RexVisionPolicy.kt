/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.player

import org.json.JSONArray
import org.json.JSONObject

enum class RexImplementationTier(val code: Int, val label: String) {
  TIER_1(1, "Tier 1: Real-Time Native MPV / GPU Processing"),
  TIER_2(2, "Tier 2: Advanced GPU / Multi-Frame Processing"),
  TIER_3(3, "Tier 3: Experimental AI / Neural Video Restoration"),
}

/** Native renderer stages, GPU shaders, and experimental neural/multi-frame restoration catalogue. */
internal data class RexAlgorithm(
  val index: Int,
  val id: String,
  val title: String,
  val category: String,
  val description: String,
  val impact: String,
  val options: Set<String> = emptySet(),
  val choices: List<String> = emptyList(),
  val range: IntRange? = null,
  val unavailable: String? = null,
  val tier: RexImplementationTier = RexImplementationTier.TIER_1,
  val gpuAccelerated: Boolean = true,
  val shaderPasses: Set<String> = emptySet(),
  val experimental: Boolean = false,
) {
  constructor(
    id: String,
    title: String,
    category: String,
    description: String,
    impact: String,
    options: Set<String> = emptySet(),
    choices: List<String> = emptyList(),
    range: IntRange? = null,
    unavailable: String? = null,
  ) : this(
    index = 0,
    id = id,
    title = title,
    category = category,
    description = description,
    impact = impact,
    options = options,
    choices = choices,
    range = range,
    unavailable = unavailable,
    tier = RexImplementationTier.TIER_1,
  )
}

internal data class RexJsonApplyResult(
  val enabledIds: Set<String>,
  val bypassOriginal: Boolean? = null,
  val masterEnabled: Boolean? = null,
  val values: Map<String, String> = emptyMap(),
  val appliedCount: Int = 0,
  val error: String? = null,
)

internal object RexVisionPolicy {
  const val TOTAL_ALGORITHM_COUNT = 122
  const val DEFAULT_CINEMATIC_FPS = 24

  val globalDefaultsSummary = listOf(
    "Preserve original cinematic FPS (24 FPS).",
    "Frame interpolation disabled by default.",
    "All algorithms individually configurable.",
    "Support GPU acceleration wherever possible.",
    "Experimental algorithms remain accessible.",
    "No fake or non-functional settings.",
    "One-tap bypass to original unprocessed video.",
  )

  val scalers = setOf(
    "lanczos",
    "spline36",
    "mitchell",
    "edge_directed_interpolation",
    "easu",
    "fsr_spatial_upscaling",
    "nedi",
    "adaptive_resampling",
  )

  val defaults = setOf(
    "spline36",
    "correct_downscaling",
    "tone_mapping",
    "dithering",
    "hardware_decoding",
    "gpu_shaders",
  )

  private fun alg(
    index: Int,
    id: String,
    title: String,
    category: String,
    description: String,
    impact: String,
    options: Set<String> = emptySet(),
    choices: List<String> = emptyList(),
    range: IntRange? = null,
    unavailable: String? = null,
    tier: RexImplementationTier = RexImplementationTier.TIER_1,
    gpuAccelerated: Boolean = true,
    shaderPasses: Set<String> = emptySet(),
  ): RexAlgorithm = RexAlgorithm(
    index = index,
    id = id,
    title = title,
    category = category,
    description = description,
    impact = impact,
    options = options,
    choices = choices,
    range = range,
    unavailable = unavailable,
    tier = tier,
    gpuAccelerated = gpuAccelerated,
    shaderPasses = shaderPasses,
    experimental = tier == RexImplementationTier.TIER_3,
  )

  val algorithms: List<RexAlgorithm> = listOf(
    // 1 - 24: Core Native MPV & GPU Foundation
    alg(1, "lanczos", "Lanczos Scaling", "Image Scaling & Reconstruction", "Windowed-sinc reconstruction. Select one scaler; radius controls taps.", "Medium", setOf("scale", "scale-radius"), range = 2..4, tier = RexImplementationTier.TIER_1),
    alg(2, "spline36", "Spline36 Scaling", "Image Scaling & Reconstruction", "Six-tap spline reconstruction with restrained ringing. Select one scaler.", "Medium", setOf("scale"), tier = RexImplementationTier.TIER_1),
    alg(3, "mitchell", "Mitchell-Netravali Scaling", "Image Scaling & Reconstruction", "Soft bicubic reconstruction that avoids aggressive sharpening. Select one scaler.", "Low", setOf("scale"), tier = RexImplementationTier.TIER_1),
    alg(4, "sigmoid", "Sigmoid Upscaling", "Image Scaling & Reconstruction", "Sigmoidal light domain reduces scaling halos; only used during upscaling.", "Medium", setOf("sigmoid-upscaling"), tier = RexImplementationTier.TIER_1),
    alg(5, "correct_downscaling", "Correct Downscaling", "Image Scaling & Reconstruction", "Linear-light downscaling with antialiased filter support.", "Medium", setOf("dscale", "linear-downscaling", "correct-downscaling"), tier = RexImplementationTier.TIER_1),
    alg(6, "debanding", "Debanding", "Compression & Image Restoration", "GPU gradient smoothing; low thresholds preserve texture. Strength is MPV's threshold.", "Medium", setOf("deband", "deband-threshold", "deband-iterations", "deband-grain"), range = 4..48, tier = RexImplementationTier.TIER_1),
    alg(7, "deblocking", "Deblocking", "Compression & Image Restoration", "True codec block-grid artifact removal.", "High", unavailable = "True block-grid deblocking is not implemented: the spatial shader has no codec block phase or quantization metadata. Decoder in-loop deblocking remains codec-controlled.", tier = RexImplementationTier.TIER_2),
    alg(8, "deringing", "Deringing", "Compression & Image Restoration", "Scaler anti-ringing clamp, not restoration of ringing baked into the source.", "Low", setOf("scale-antiring"), range = 0..100, tier = RexImplementationTier.TIER_1),
    alg(9, "denoising", "Denoising", "Compression & Image Restoration", "Single-frame edge-preserving spatial noise reduction; no temporal denoising.", "Medium", shaderPasses = setOf("texture-denoise"), tier = RexImplementationTier.TIER_2),
    alg(10, "sharpening", "Sharpening", "Compression & Image Restoration", "MPV renderer sharpening, limited to a conservative 0–0.3 to reduce halos.", "Low", setOf("sharpen"), range = 0..30, tier = RexImplementationTier.TIER_1),
    alg(11, "grain", "Grain Restoration", "Compression & Image Restoration", "Restore natural grain without destroying detail.", "High", unavailable = "Deband noise and codec film-grain synthesis are not grain restoration; no restoration model is integrated.", tier = RexImplementationTier.TIER_3),
    alg(12, "interpolation", "Frame Interpolation", "Temporal & Motion Processing", "MPV display-timed frame blending, NOT motion estimation. Off by default; explicitly enabling changes presentation cadence.", "High", setOf("interpolation", "video-sync", "tscale"), tier = RexImplementationTier.TIER_1),
    alg(13, "resampling", "Display Resampling", "Temporal & Motion Processing", "Resample the audio clock to the display clock without synthesizing video frames. Shares video-sync with Display-Resample Synchronisation.", "Low", setOf("video-sync"), tier = RexImplementationTier.TIER_1),
    alg(14, "motion_deblur", "Motion Deblurring", "Temporal & Motion Processing", "Recover detail lost to exposure-time motion blur, not ordinary sharpening.", "High", unavailable = "No motion-aware exposure deconvolution is integrated.", tier = RexImplementationTier.TIER_2),
    alg(15, "temporal_denoise", "Temporal Denoising", "Temporal & Motion Processing", "Motion-aligned multi-frame noise reduction.", "High", unavailable = "No history-buffer, scene-cut and motion-aligned denoiser is integrated.", tier = RexImplementationTier.TIER_2),
    alg(16, "motion_compensated", "Motion-Compensated Processing", "Temporal & Motion Processing", "Motion-estimated processing using synchronized neighbouring frames.", "High", unavailable = "No independent REX stage. Existing MPVFlow is configured separately in Decoder settings and must not be stacked with REX interpolation.", tier = RexImplementationTier.TIER_2),
    alg(17, "tone_mapping", "Tone Mapping", "Lighting, Colour & HDR", "Native HDR luminance mapping. Auto follows MPV; never promotes SDR to artificial HDR.", "Medium", setOf("tone-mapping"), listOf("auto", "bt.2390", "hable", "reinhard", "clip"), tier = RexImplementationTier.TIER_1),
    alg(18, "hdr_sdr", "HDR-to-SDR Conversion", "Lighting, Colour & HDR", "Explicit SDR target override; off leaves native automatic output handling. Mapping uses the same native colour pass, not a second conversion.", "Medium", setOf("target-trc", "target-prim"), tier = RexImplementationTier.TIER_1),
    alg(19, "gamma", "Gamma Correction", "Lighting, Colour & HDR", "Small native gamma adjustment. Zero is neutral; disabled is neutral while REX is active.", "Low", setOf("gamma"), range = -20..20, tier = RexImplementationTier.TIER_1),
    alg(20, "colour_management", "Colour Management", "Lighting, Colour & HDR", "Native output-primary conversion. When off, the existing target is restored. Auto uses the renderer's target; not calibrated Android ICC support.", "Low", setOf("target-prim"), listOf("auto", "bt.709", "bt.2020"), tier = RexImplementationTier.TIER_1),
    alg(21, "dithering", "Dithering", "Lighting, Colour & HDR", "Dither after colour conversion at the output bit depth. Auto lets MPV detect output depth.", "Low", setOf("dither-depth"), listOf("auto", "8", "10"), tier = RexImplementationTier.TIER_1),
    alg(22, "gpu_shaders", "GPU Shader Processing", "GPU & Playback Processing", "Allow REX native GPU stages. No new CPU frame copies or custom shader chain. Does not disable the renderer or external shaders.", "Medium", tier = RexImplementationTier.TIER_1),
    alg(23, "hardware_decoding", "Hardware Video Decoding (MediaCodec)", "GPU & Playback Processing", "On requests MediaCodec with software fallback; off requests software decoding while REX is active. Actual copy mode is reported below; zero-copy is not guaranteed.", "Low", setOf("hwdec"), tier = RexImplementationTier.TIER_1),
    alg(24, "display_sync", "Display-Resample Synchronisation", "GPU & Playback Processing", "The same native video-sync option as Display Resampling. Audio remains synchronized; this does not force 48 or 60 FPS.", "Low", setOf("video-sync"), tier = RexImplementationTier.TIER_1),

    // 25 - 37: Neural Restoration, Sharpening & Deconvolution
    alg(25, "ai_super_resolution_single", "AI Super Resolution (Single-Frame)", "AI & Neural Restoration", "Single-frame convolutional neural network spatial super-resolution.", "High", unavailable = "Experimental neural single-frame super-resolution model is not bundled in the zero-copy MPV pass.", tier = RexImplementationTier.TIER_3),
    alg(26, "deep_learning_reconstruction", "Deep Learning Image Reconstruction", "AI & Neural Restoration", "Deep residual image reconstruction for compressed video frames.", "High", unavailable = "Experimental deep learning image reconstruction weights require external NPU/Vulkan tensor runtime.", tier = RexImplementationTier.TIER_3),
    alg(27, "realesrgan_enhancement", "Real-ESRGAN Video Frame Enhancement", "AI & Neural Restoration", "Generative adversarial network restoration for anime and live-action video frames.", "High", unavailable = "Experimental Real-ESRGAN tensor pass exceeds real-time mobile frame budget without dedicated NPU offload.", tier = RexImplementationTier.TIER_3),
    alg(28, "detail_reconstruction", "Detail Reconstruction", "Sharpening & Deconvolution", "High-frequency detail reconstruction via native renderer kernel shaping.", "Medium", setOf("scale-blur"), range = 90..110, tier = RexImplementationTier.TIER_1),
    alg(29, "texture_recovery", "Texture Recovery", "Sharpening & Deconvolution", "Fine surface micro-texture recovery with gradient threshold protection.", "Medium", setOf("deband-threshold"), range = 8..32, tier = RexImplementationTier.TIER_1),
    alg(30, "edge_aware_enhancement", "Edge-Aware Enhancement", "Sharpening & Deconvolution", "Scharr-gradient confidence, multi-scale luma detail gating and local overshoot-limited sharpening.", "High", shaderPasses = setOf("edge-sharpen"), tier = RexImplementationTier.TIER_1),
    alg(31, "adaptive_local_sharpening", "Adaptive Local Sharpening", "Sharpening & Deconvolution", "Local edge-confidence and detail-confidence gated luma enhancement with neighborhood bounds.", "High", shaderPasses = setOf("edge-sharpen"), tier = RexImplementationTier.TIER_1),
    alg(32, "multi_scale_sharpening", "Multi-Scale Sharpening", "Sharpening & Deconvolution", "Two-scale luminance detail enhancement gated by Scharr edge confidence and local bounds.", "High", shaderPasses = setOf("edge-sharpen"), tier = RexImplementationTier.TIER_2),
    alg(33, "unsharp_mask", "Unsharp Mask", "Sharpening & Deconvolution", "Classical unsharp masking mapped to native GPU renderer sharpen pass.", "Low", setOf("sharpen"), range = 0..30, tier = RexImplementationTier.TIER_1),
    alg(34, "wiener_deconvolution", "Wiener Deconvolution", "Sharpening & Deconvolution", "Minimum mean-square error frequency-domain inverse filtering for optical blur.", "High", unavailable = "Requires per-frame PSF noise-power spectrum estimation not present in native VO.", tier = RexImplementationTier.TIER_2),
    alg(35, "richardson_lucy_deconvolution", "Richardson-Lucy Deconvolution", "Sharpening & Deconvolution", "Iterative maximum-likelihood point-spread function deconvolution.", "High", unavailable = "Iterative PSF deconvolution requires multi-pass FFT buffers not enabled in zero-copy VO.", tier = RexImplementationTier.TIER_2),
    alg(36, "blind_image_deblurring", "Blind Image Deblurring", "Sharpening & Deconvolution", "Simultaneous blur kernel and latent sharp image estimation.", "High", unavailable = "Experimental blind kernel estimation is not supported in real-time native playback.", tier = RexImplementationTier.TIER_3),
    alg(37, "ringing_artifact_suppression", "Ringing Artifact Suppression", "Compression & Image Restoration", "Clamps Gibbs phenomenon oscillations across luma, chroma, and downscaling passes.", "Low", setOf("scale-antiring", "dscale-antiring", "cscale-antiring"), range = 0..100, tier = RexImplementationTier.TIER_1),

    // 38 - 50: Optical Flow & Multi-Frame Motion Processing
    alg(38, "optical_flow_estimation", "Optical Flow Estimation", "Temporal & Motion Processing", "Coarse-to-fine inter-frame motion vector field estimation.", "High", unavailable = "Managed by the dedicated MPVFlow engine in Decoder settings; not duplicated inside REX.", tier = RexImplementationTier.TIER_2),
    alg(39, "dense_optical_flow", "Dense Optical Flow", "Temporal & Motion Processing", "Per-pixel dense optical flow field computation across consecutive frames.", "High", unavailable = "Dense per-pixel flow is handled by Vulkan MPVFlow compute passes when explicitly enabled.", tier = RexImplementationTier.TIER_2),
    alg(40, "mc_temporal_filtering", "Motion-Compensated Temporal Filtering", "Temporal & Motion Processing", "Trajectory-aligned temporal filtering along motion vectors.", "High", unavailable = "Requires multi-frame motion history ring buffer outside the single-pass MPV VO.", tier = RexImplementationTier.TIER_2),
    alg(41, "mc_denoising", "Motion-Compensated Denoising", "Temporal & Motion Processing", "Temporal noise suppression warped along bidirectional optical flow.", "High", unavailable = "Requires bidirectional optical flow warping buffers not active in zero-copy VO.", tier = RexImplementationTier.TIER_2),
    alg(42, "ai_video_frame_interpolation", "AI Video Frame Interpolation", "AI & Neural Restoration", "Neural optical-flow intermediate frame synthesis (RIFE/IFNet class). Disabled by default to preserve 24 FPS.", "High", unavailable = "Experimental neural frame synthesis is disabled by default to preserve 24 FPS cinematic cadence.", tier = RexImplementationTier.TIER_3),
    alg(43, "motion_blur_estimation", "Motion Blur Estimation", "Temporal & Motion Processing", "Intra-frame shutter motion trajectory vector estimation.", "High", unavailable = "Requires temporal shutter-angle motion estimation not exposed by hardware decoders.", tier = RexImplementationTier.TIER_2),
    alg(44, "motion_blur_removal", "Motion Blur Removal", "Temporal & Motion Processing", "Directional inverse filtering along estimated motion trajectories.", "High", unavailable = "Requires per-tile directional motion deblurring shaders.", tier = RexImplementationTier.TIER_2),
    alg(45, "temporal_super_resolution", "Temporal Super Resolution", "Temporal & Motion Processing", "Sub-pixel detail accumulation across temporally warped reference frames.", "High", unavailable = "Requires persistent jittered sub-pixel history accumulation buffers.", tier = RexImplementationTier.TIER_2),
    alg(46, "multiframe_detail_reconstruction", "Multi-Frame Detail Reconstruction", "Temporal & Motion Processing", "Fuses high-frequency residuals from aligned neighbouring frames.", "High", unavailable = "Requires multi-frame alignment queue not active in standard zero-copy VO.", tier = RexImplementationTier.TIER_2),
    alg(47, "scene_change_detection", "Scene Change Detection", "Scene & Adaptive Intelligence", "Detects shot cuts and resets temporal blending/interpolation state.", "Low", setOf("interpolation-preserve"), tier = RexImplementationTier.TIER_2),
    alg(48, "adaptive_motion_estimation", "Adaptive Motion Estimation", "Temporal & Motion Processing", "Dynamically scales temporal filter window and tscale kernel radius.", "Medium", setOf("tscale", "tscale-radius"), choices = listOf("oversample", "linear", "catmull_rom", "mitchell"), tier = RexImplementationTier.TIER_2),
    alg(49, "motion_adaptive_sharpening", "Motion-Adaptive Sharpening", "Sharpening & Deconvolution", "Attenuates spatial sharpening during high-velocity pans to prevent shimmer.", "Medium", setOf("sharpen"), range = 0..25, tier = RexImplementationTier.TIER_2),
    alg(50, "ghosting_reduction", "Ghosting Reduction", "Temporal & Motion Processing", "Clamps temporal filter weights and antiringing to eliminate trailing ghost artifacts.", "Low", setOf("tscale-antiring", "tscale-clamp"), range = 0..100, tier = RexImplementationTier.TIER_2),

    // 51 - 62: Spatial/Temporal Denoising & Compression Artifact Suppression
    alg(51, "ai_video_denoising", "AI Video Denoising", "AI & Neural Restoration", "Deep convolutional spatio-temporal blind noise removal.", "High", unavailable = "Experimental neural denoiser requires external tensor runtime.", tier = RexImplementationTier.TIER_3),
    alg(52, "non_local_means", "Non-Local Means (NLM)", "Compression & Image Restoration", "Patch-similarity weighted spatial denoising across non-local neighbourhoods.", "High", unavailable = "Full NLM patch search requires custom compute shader pass.", tier = RexImplementationTier.TIER_2),
    alg(53, "bm3d_denoising", "BM3D Denoising", "Compression & Image Restoration", "Block-matching and 3D collaborative transform-domain filtering.", "High", unavailable = "3D collaborative transform grouping is not real-time on mobile GPU without frame readback.", tier = RexImplementationTier.TIER_2),
    alg(54, "bilateral_filtering", "Bilateral Filtering", "Compression & Image Restoration", "Compact 3x3 luma-guided range-weighted spatial smoothing; strong edges are protected.", "Medium", shaderPasses = setOf("texture-denoise"), tier = RexImplementationTier.TIER_2),
    alg(55, "guided_image_filtering", "Guided Image Filtering", "Compression & Image Restoration", "Local-luma-guided spatial smoothing with conservative flat-region blending.", "Medium", shaderPasses = setOf("texture-denoise"), tier = RexImplementationTier.TIER_2),
    alg(56, "adaptive_spatial_denoising", "Adaptive Spatial Denoising", "Compression & Image Restoration", "Local-range-adaptive spatial noise reduction; blend strength falls near edges and texture.", "Medium", shaderPasses = setOf("texture-denoise"), tier = RexImplementationTier.TIER_2),
    alg(57, "multiframe_noise_reduction", "Multi-Frame Noise Reduction", "Temporal & Motion Processing", "Temporal recursive averaging across aligned video frames.", "High", unavailable = "Requires multi-frame history buffer outside single-frame native VO.", tier = RexImplementationTier.TIER_2),
    alg(58, "ai_compression_artifact_removal", "AI Compression Artifact Removal", "AI & Neural Restoration", "Neural restoration of heavy quantization, macroblocking, and mosquito noise.", "High", unavailable = "Experimental neural artifact removal network is not bundled in native VO.", tier = RexImplementationTier.TIER_3),
    alg(59, "ringing_blocking_suppression", "Ringing and Blocking Suppression", "Compression & Image Restoration", "Combined deband gradient smoothing and scaler anti-ringing limiter.", "Medium", setOf("deband", "scale-antiring"), range = 0..100, tier = RexImplementationTier.TIER_1),
    alg(60, "chroma_noise_reduction", "Chroma Noise Reduction", "Compression & Image Restoration", "Suppresses high-frequency U/V colour sub-sampling noise via chroma antiringing.", "Low", setOf("cscale-antiring"), range = 0..100, tier = RexImplementationTier.TIER_1),
    alg(61, "mosquito_noise_reduction", "Mosquito Noise Reduction", "Compression & Image Restoration", "Conservative local edge-overshoot cleanup; not true DCT classification or block deblocking.", "Medium", shaderPasses = setOf("compression-cleanup"), tier = RexImplementationTier.TIER_2),
    alg(62, "adaptive_deblocking", "Adaptive Deblocking", "Compression & Image Restoration", "Quantization-adaptive block boundary smoothing via wide-radius GPU debanding.", "Medium", setOf("deband", "deband-range", "deband-iterations"), range = 12..40, tier = RexImplementationTier.TIER_2),

    // 63 - 77: HDR, Tone Mapping, Retinex & Colour Science
    alg(63, "local_tone_mapping", "Local Tone Mapping", "Lighting, Colour & HDR", "Mild local-luminance-adaptive SDR highlight shoulder; does not recover clipped pixels or create HDR.", "Medium", shaderPasses = setOf("local-tone"), tier = RexImplementationTier.TIER_2),
    alg(64, "adaptive_tone_mapping", "Adaptive Tone Mapping", "Lighting, Colour & HDR", "Dynamic scene-histogram percentile tone mapping.", "Medium", setOf("tone-mapping", "hdr-compute-peak"), choices = listOf("auto", "yes", "no"), tier = RexImplementationTier.TIER_1),
    alg(65, "global_tone_mapping", "Global Tone Mapping", "Lighting, Colour & HDR", "Consistent monotonic global EOTF luminance curve mapping.", "Low", setOf("tone-mapping"), choices = listOf("bt.2390", "hable", "reinhard", "mobius", "clip"), tier = RexImplementationTier.TIER_1),
    alg(66, "hdr_reconstruction", "HDR Reconstruction", "Lighting, Colour & HDR", "Inverse tone mapping / peak luminance recovery within native colour pipeline.", "Medium", setOf("tone-mapping-param"), range = 10..100, tier = RexImplementationTier.TIER_2),
    alg(67, "highlight_recovery", "Highlight Recovery", "Lighting, Colour & HDR", "Soft-knee roll-off and dynamic peak detection to recover clipped specular detail.", "Medium", setOf("hdr-compute-peak", "tone-mapping"), choices = listOf("bt.2390", "hable", "mobius"), tier = RexImplementationTier.TIER_1),
    alg(68, "shadow_detail_recovery", "Shadow Detail Recovery", "Lighting, Colour & HDR", "Lifts near-black toe visibility without washing out black floor.", "Low", setOf("gamma", "brightness"), range = -10..15, tier = RexImplementationTier.TIER_1),
    alg(69, "local_contrast_enhancement", "Local Contrast Enhancement", "Lighting, Colour & HDR", "Mid-tone contrast and HDR contrast recovery pass.", "Medium", setOf("contrast"), range = -20..20, tier = RexImplementationTier.TIER_1),
    alg(70, "clahe", "CLAHE (Contrast Limited Adaptive Histogram Equalization)", "Lighting, Colour & HDR", "Tile-based adaptive histogram equalization with clip-limit contrast protection.", "High", unavailable = "Tile histogram equalization requires custom compute pass not active in native VO.", tier = RexImplementationTier.TIER_2),
    alg(71, "retinex_illumination", "Retinex-Based Illumination Correction", "Lighting, Colour & HDR", "Separates reflectance from non-uniform scene illumination.", "High", unavailable = "Single-scale Retinex surround convolution is not integrated in zero-copy VO.", tier = RexImplementationTier.TIER_2),
    alg(72, "multiscale_retinex", "Multi-Scale Retinex", "Lighting, Colour & HDR", "Multi-Gaussian surround illumination normalization with colour restoration.", "High", unavailable = "Multi-scale Gaussian pyramid Retinex requires custom multi-pass shader pipeline.", tier = RexImplementationTier.TIER_2),
    alg(73, "white_balance_correction", "White Balance Correction", "Lighting, Colour & HDR", "Neutralizes chromaticity cast via native hue and saturation balance.", "Low", setOf("hue", "saturation"), range = -15..15, tier = RexImplementationTier.TIER_1),
    alg(74, "colour_constancy", "Colour Constancy", "Lighting, Colour & HDR", "Maintains perceptual colour appearance across differing target primaries.", "Low", setOf("target-prim", "saturation"), choices = listOf("auto", "bt.709", "bt.2020", "dci-p3"), tier = RexImplementationTier.TIER_1),
    alg(75, "adaptive_gamma_correction", "Adaptive Gamma Correction", "Lighting, Colour & HDR", "Transfer-curve aware gamma adjustment.", "Low", setOf("gamma"), range = -20..20, tier = RexImplementationTier.TIER_1),
    alg(76, "highlight_bloom_control", "Highlight Bloom Control", "Lighting, Colour & HDR", "Restrained local bright-outlier roll-off while retaining broad artistic glow; not optical halation removal.", "Medium", shaderPasses = setOf("highlight-control"), tier = RexImplementationTier.TIER_1),
    alg(77, "specular_highlight_preservation", "Specular Highlight Preservation", "Lighting, Colour & HDR", "Restrained highlight shoulder with RGB-scaled chromaticity preservation; clipped source detail cannot be recovered.", "Medium", shaderPasses = setOf("highlight-control"), tier = RexImplementationTier.TIER_1),

    // 78 - 88: Neural & Spatial Upscaling (EASU, RCAS, FSR, NEDI, Chroma)
    alg(78, "ai_video_super_resolution", "AI Video Super Resolution", "AI & Neural Restoration", "Temporally recurrent neural video super-resolution (BasicVSR/EDVR class).", "High", unavailable = "Experimental recurrent neural VSR requires dedicated NPU tensor pipeline.", tier = RexImplementationTier.TIER_3),
    alg(79, "multiframe_super_resolution", "Multi-Frame Super Resolution", "Image Scaling & Reconstruction", "Sub-pixel motion-aligned multi-frame spatial super-resolution.", "High", unavailable = "Requires sub-pixel optical flow registration queue outside single-frame VO.", tier = RexImplementationTier.TIER_2),
    alg(80, "realtime_neural_upscaling", "Real-Time Neural Upscaling", "AI & Neural Restoration", "Lightweight CNN shader upscaling for real-time playback.", "High", unavailable = "Use Anime4K in Decoder settings for CNN shader upscaling; kept separate to prevent double shader stacking.", tier = RexImplementationTier.TIER_3),
    alg(81, "edge_directed_interpolation", "Edge-Directed Interpolation", "Image Scaling & Reconstruction", "Elliptical weighted average (EWA Lanczos) directional edge-aware upscaling.", "Medium", setOf("scale"), tier = RexImplementationTier.TIER_1),
    alg(82, "easu", "EASU (Edge-Adaptive Spatial Upsampling)", "Image Scaling & Reconstruction", "Directional gradient-adaptive lanczos upsampling pass.", "Medium", setOf("scale", "sigmoid-upscaling"), tier = RexImplementationTier.TIER_1),
    alg(83, "rcas", "RCAS (Robust Contrast-Adaptive Sharpening)", "Sharpening & Deconvolution", "Contrast-adaptive local sharpening with halo-free min/max neighbourhood clamping.", "Low", setOf("sharpen", "scale-antiring"), range = 0..30, tier = RexImplementationTier.TIER_1),
    alg(84, "fsr_spatial_upscaling", "FSR-Style Spatial Upscaling", "Image Scaling & Reconstruction", "Combined edge-adaptive spatial upsampling (EASU) + contrast-adaptive sharpening (RCAS).", "Medium", setOf("scale", "sigmoid-upscaling", "sharpen"), range = 0..25, tier = RexImplementationTier.TIER_1),
    alg(85, "nedi", "NEDI (New Edge-Directed Interpolation)", "Image Scaling & Reconstruction", "Covariance-based geometric duality edge-directed interpolation.", "High", setOf("scale"), tier = RexImplementationTier.TIER_2),
    alg(86, "adaptive_resampling", "Adaptive Resampling", "Image Scaling & Reconstruction", "Dynamically selects spatial reconstruction kernel and linear downscaling.", "Medium", setOf("scale", "dscale", "correct-downscaling"), tier = RexImplementationTier.TIER_1),
    alg(87, "chroma_upsampling", "Chroma Upsampling", "Image Scaling & Reconstruction", "High-precision 4:2:0 to 4:4:4 chroma plane reconstruction.", "Medium", setOf("cscale"), choices = listOf("spline36", "lanczos", "mitchell", "ewa_lanczos"), tier = RexImplementationTier.TIER_1),
    alg(88, "subpixel_detail_reconstruction", "Subpixel Detail Reconstruction", "Image Scaling & Reconstruction", "Subpixel phase-accurate kernel evaluation with linear-light upscaling.", "Medium", setOf("sigmoid-upscaling", "linear-upscaling"), tier = RexImplementationTier.TIER_2),

    // 89 - 98: Shaders, Edge Detection & Frequency-Domain Processing
    alg(89, "compute_shader_processing", "Compute Shader Processing", "GPU & Playback Processing", "Enables GPU compute shader dispatch where supported by the active Vulkan/OpenGL backend.", "Medium", setOf("gpu-api"), tier = RexImplementationTier.TIER_2),
    alg(90, "multipass_shader_pipeline", "Multi-Pass Shader Pipeline", "GPU & Playback Processing", "Coordinates multi-stage native libplacebo/MPV rendering passes.", "Medium", setOf("deband", "sigmoid-upscaling", "correct-downscaling"), tier = RexImplementationTier.TIER_2),
    alg(91, "adaptive_sharpening_shader", "Adaptive Sharpening Shader", "GPU & Playback Processing", "GPU fragment sharpening stage with anti-ringing protection.", "Low", setOf("sharpen", "scale-antiring"), range = 0..30, tier = RexImplementationTier.TIER_1),
    alg(92, "edge_detection", "Edge Detection", "Frequency & Edge Processing", "Gradient magnitude structural edge analysis for edge-guided filtering.", "Medium", setOf("scale-antiring"), range = 0..100, tier = RexImplementationTier.TIER_2),
    alg(93, "sobel_edge_detection", "Sobel Edge Detection", "Frequency & Edge Processing", "First-order 3×3 Sobel directional gradient operator.", "Medium", unavailable = "Standalone Sobel visualization filter is disabled to preserve natural video output.", tier = RexImplementationTier.TIER_2),
    alg(94, "canny_edge_detection", "Canny Edge Detection", "Frequency & Edge Processing", "Multi-stage Gaussian derivative, non-maximum suppression, and hysteresis edge detector.", "High", unavailable = "Standalone Canny edge mask is a diagnostic operator, not a direct playback enhancement pass.", tier = RexImplementationTier.TIER_2),
    alg(95, "laplacian_filtering", "Laplacian Filtering", "Frequency & Edge Processing", "Second-order isotropic Laplacian detail emphasis.", "Low", setOf("sharpen"), range = 0..25, tier = RexImplementationTier.TIER_1),
    alg(96, "high_pass_filtering", "High-Pass Filtering", "Frequency & Edge Processing", "Spatial high-frequency residual extraction and reinforcement.", "Low", setOf("sharpen"), range = 0..25, tier = RexImplementationTier.TIER_1),
    alg(97, "frequency_domain_filtering", "Frequency-Domain Filtering", "Frequency & Edge Processing", "Spectral band attenuation and kernel window shaping.", "High", setOf("scale-window"), choices = listOf("blackman", "hanning", "hamming", "kaiser"), tier = RexImplementationTier.TIER_2),
    alg(98, "fft_image_processing", "FFT-Based Image Processing", "Frequency & Edge Processing", "2D Fast Fourier Transform spectral filtering.", "High", unavailable = "Full-frame 2D FFT spectral transform is not supported in zero-copy real-time VO.", tier = RexImplementationTier.TIER_2),

    // 99 - 111: Perceptual, Luma-Chroma & Multi-Scale Decomposition
    alg(99, "adaptive_contrast_enhancement", "Adaptive Contrast Enhancement", "Perceptual & Luma-Chroma", "Content-aware mid-tone contrast and sigmoidal transfer optimization.", "Low", setOf("contrast", "sigmoid-upscaling"), range = -15..15, tier = RexImplementationTier.TIER_1),
    alg(100, "luma_chroma_separation", "Luma-Chroma Separation", "Perceptual & Luma-Chroma", "Decouples luma (Y) and chroma (CbCr) scaling and anti-ringing pipelines.", "Low", setOf("scale", "cscale", "cscale-antiring"), tier = RexImplementationTier.TIER_1),
    alg(101, "multiscale_decomposition", "Multi-Scale Image Decomposition", "Perceptual & Luma-Chroma", "Decomposes base illumination and fine spatial detail layers.", "High", setOf("deband", "sharpen"), range = 0..25, tier = RexImplementationTier.TIER_2),
    alg(102, "perceptual_contrast_enhancement", "Perceptual Contrast Enhancement", "Perceptual & Luma-Chroma", "Psychovisual contrast shaping in sigmoidal perceptual space.", "Low", setOf("sigmoid-upscaling", "contrast"), range = -10..15, tier = RexImplementationTier.TIER_1),
    alg(103, "local_detail_enhancement", "Local Detail Enhancement", "Perceptual & Luma-Chroma", "Fine micro-contrast reinforcement with halo clamping.", "Low", setOf("sharpen", "scale-antiring"), range = 0..25, tier = RexImplementationTier.TIER_1),
    alg(104, "frequency_separation", "Frequency Separation", "Perceptual & Luma-Chroma", "Smooths low-frequency banding while preserving high-frequency detail.", "Medium", setOf("deband", "deband-threshold", "sharpen"), range = 4..32, tier = RexImplementationTier.TIER_2),
    alg(105, "mid_frequency_detail", "Mid-Frequency Detail Enhancement", "Perceptual & Luma-Chroma", "Enhances mid-band structural clarity without amplifying pixel noise.", "Low", setOf("sharpen", "deband-threshold"), range = 0..20, tier = RexImplementationTier.TIER_1),
    alg(106, "adaptive_luma_enhancement", "Adaptive Luma Enhancement", "Perceptual & Luma-Chroma", "Optimizes luma transfer response across dark and bright regions.", "Low", setOf("gamma", "sigmoid-upscaling"), range = -15..15, tier = RexImplementationTier.TIER_1),
    alg(107, "chroma_reconstruction", "Chroma Reconstruction", "Perceptual & Luma-Chroma", "Reconstructs subsampled chroma planes with anti-ringing protection.", "Medium", setOf("cscale", "cscale-antiring"), choices = listOf("spline36", "lanczos", "mitchell"), tier = RexImplementationTier.TIER_1),
    alg(108, "perceptual_sharpness", "Perceptual Sharpness Optimization", "Perceptual & Luma-Chroma", "Balances sharpening, sigmoid light conversion, and anti-ringing for natural acutance.", "Low", setOf("sharpen", "sigmoid-upscaling", "scale-antiring"), range = 0..25, tier = RexImplementationTier.TIER_1),
    alg(109, "texture_aware_denoising", "Texture-Aware Denoising", "Compression & Image Restoration", "Conservative luma-guided 3x3 denoising with edge/range gating to limit texture smearing.", "Medium", shaderPasses = setOf("texture-denoise"), tier = RexImplementationTier.TIER_2),
    alg(110, "edge_preserving_smoothing", "Edge-Preserving Smoothing", "Compression & Image Restoration", "Edge-aware spatial smoothing in low-variance regions; strong contours receive reduced blending.", "Medium", shaderPasses = setOf("texture-denoise"), tier = RexImplementationTier.TIER_1),
    alg(111, "halo_suppression", "Halo Suppression", "Compression & Image Restoration", "Suppresses light/dark overshoot halos using sigmoidal upscaling and anti-ringing.", "Low", setOf("sigmoid-upscaling", "scale-antiring", "dscale-antiring"), range = 0..100, tier = RexImplementationTier.TIER_1),

    // 112 - 121: Scene-Adaptive & Autonomous GPU Management
    alg(112, "scene_adaptive_processing", "Scene-Adaptive Processing", "Scene & Adaptive Intelligence", "Per-pixel local luminance adaptation only; whole-scene histogram and shot state are not implemented.", "Medium", unavailable = "Requires frame-history histograms and scene state; not provided by the current single-frame shader.", tier = RexImplementationTier.TIER_2),
    alg(113, "shot_boundary_detection", "Shot Boundary Detection", "Scene & Adaptive Intelligence", "Detects camera cuts for HDR peak metering and temporal reset.", "Low", unavailable = "No temporal frame-history or shot-boundary detector is integrated.", tier = RexImplementationTier.TIER_2),
    alg(114, "scene_classification", "Scene Classification", "Scene & Adaptive Intelligence", "Classifies content characteristics (SDR/HDR, resolution, frame rate) from stream metadata.", "Low", setOf("tone-mapping"), tier = RexImplementationTier.TIER_2),
    alg(115, "motion_complexity_analysis", "Motion Complexity Analysis", "Scene & Adaptive Intelligence", "Monitors frame-drop cadence and presentation jitter to gauge motion load.", "Low", setOf("video-sync"), tier = RexImplementationTier.TIER_2),
    alg(116, "dynamic_quality_scaling", "Dynamic Quality Scaling", "Scene & Adaptive Intelligence", "Automatically scales reconstruction complexity when thermal or frame-drop pressure rises.", "Low", setOf("scale", "dscale"), tier = RexImplementationTier.TIER_1),
    alg(117, "content_adaptive_filtering", "Content-Adaptive Filtering", "Scene & Adaptive Intelligence", "Selects optimal chroma and luma filter parameters based on source format.", "Low", setOf("correct-downscaling", "linear-downscaling"), tier = RexImplementationTier.TIER_1),
    alg(118, "realtime_artifact_detection", "Real-Time Artifact Detection", "Scene & Adaptive Intelligence", "Monitors decoder drop counters, delayed VO frames, and banding risk in real time.", "Low", setOf("deband"), tier = RexImplementationTier.TIER_2),
    alg(119, "auto_parameter_selection", "Automatic Enhancement Parameter Selection", "Scene & Adaptive Intelligence", "Automatically tunes native filter thresholds for balanced visual fidelity.", "Low", setOf("tone-mapping", "dither-depth"), tier = RexImplementationTier.TIER_1),
    alg(120, "per_scene_profiles", "Per-Scene Processing Profiles", "Scene & Adaptive Intelligence", "Persistent per-scene profiles require shot detection and temporal scene state.", "Low", unavailable = "No shot-boundary state or per-scene profile store is integrated.", tier = RexImplementationTier.TIER_2),
    alg(121, "adaptive_gpu_load_management", "Adaptive GPU Load Management", "GPU & Playback Processing", "Monitors thermal headroom and frame drops to shed heavy GPU stages gracefully.", "Low", setOf("scale"), tier = RexImplementationTier.TIER_1),
    alg(122, "temporal_edge_stability", "Temporal Edge Stability", "Temporal & Motion Processing", "Reduces edge shimmer using motion-aligned frame history without changing source cadence.", "High", unavailable = "Requires persistent frame history, motion alignment and scene-cut resets; the current MPV GLSL hook is single-frame.", tier = RexImplementationTier.TIER_2),
  )

  private val byId: Map<String, RexAlgorithm> = algorithms.associateBy { it.id }
  private val byIndex: Map<Int, RexAlgorithm> = algorithms.associateBy { it.index }
  private val byNormalizedTitle: Map<String, RexAlgorithm> = algorithms.associateBy { normalizeKey(it.title) }

  private fun normalizeKey(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

  fun resolveAlgorithm(token: String): RexAlgorithm? {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toIntOrNull()?.let { idx -> byIndex[idx]?.let { return it } }
    byId[trimmed]?.let { return it }
    byId[trimmed.lowercase()]?.let { return it }
    val norm = normalizeKey(trimmed)
    byId[norm]?.let { return it }
    byNormalizedTitle[norm]?.let { return it }
    return algorithms.firstOrNull {
      it.title.equals(trimmed, ignoreCase = true) ||
        "${it.index}. ${it.title}".equals(trimmed, ignoreCase = true)
    }
  }

  fun selection(enabled: Set<String>, id: String, value: Boolean): Set<String> {
    val syncAliases = setOf("resampling", "display_sync")
    if (id in syncAliases) return if (value) enabled + syncAliases else enabled - syncAliases - "interpolation"
    if (id == "interpolation" && value) return enabled + syncAliases + id
    val base = if (value && id in scalers) enabled - scalers else enabled
    return if (value) base + id else base - id
  }

  fun selectBatch(current: Set<String>, enableTokens: Iterable<String>, disableTokens: Iterable<String> = emptyList()): Set<String> {
    var result = current
    for (token in disableTokens) {
      val alg = resolveAlgorithm(token) ?: continue
      result = selection(result, alg.id, false)
    }
    for (token in enableTokens) {
      val alg = resolveAlgorithm(token) ?: continue
      result = selection(result, alg.id, true)
    }
    return result
  }

  fun toJsonConfig(
    enabled: Set<String>,
    bypassOriginal: Boolean = false,
    masterEnabled: Boolean = true,
  ): String {
    val sortedAlgorithms = algorithms.filter { it.id in enabled }.sortedBy { it.index }
    val root = JSONObject()
    root.put("engine", "GMPV_INFINITY")
    root.put("totalCatalogue", TOTAL_ALGORITHM_COUNT)
    root.put("preserveCinematicFps", DEFAULT_CINEMATIC_FPS)
    root.put("frameInterpolationDefault", false)
    root.put("masterEnabled", masterEnabled)
    root.put("bypassToOriginal", bypassOriginal)
    root.put("enabledIds", JSONArray(sortedAlgorithms.map { it.id }))
    root.put("enabledIndices", JSONArray(sortedAlgorithms.map { it.index }))
    val toggles = JSONObject()
    algorithms.forEach { alg ->
      toggles.put("${alg.index}_${alg.id}", alg.id in enabled)
    }
    root.put("algorithms", toggles)
    return root.toString(2)
  }

  fun applyJsonConfig(
    jsonText: String,
    currentEnabled: Set<String> = defaults,
  ): RexJsonApplyResult {
    val trimmed = jsonText.trim()
    if (trimmed.isEmpty()) {
      return RexJsonApplyResult(currentEnabled, error = "JSON configuration is empty.")
    }
    return runCatching {
      if (trimmed.startsWith("[")) {
        val arr = JSONArray(trimmed)
        var next = emptySet<String>()
        var count = 0
        for (i in 0 until arr.length()) {
          val item = arr.get(i).toString()
          val alg = resolveAlgorithm(item) ?: continue
          next = selection(next, alg.id, true)
          count++
        }
        return@runCatching RexJsonApplyResult(enabledIds = next, appliedCount = count)
      }

      val obj = JSONObject(trimmed)
      val bypass = if (obj.has("bypassToOriginal")) obj.optBoolean("bypassToOriginal")
      else if (obj.has("bypass")) obj.optBoolean("bypass")
      else null

      val master = if (obj.has("masterEnabled")) obj.optBoolean("masterEnabled")
      else if (obj.has("enabled") && obj.get("enabled") is Boolean) obj.optBoolean("enabled")
      else null

      var next = if (obj.optBoolean("replace", false) || obj.has("enabledIds") || obj.has("enabledIndices")) {
        emptySet()
      } else {
        currentEnabled
      }
      var count = 0

      if (obj.optBoolean("enableAll", false)) {
        algorithms.filter { it.unavailable == null && it.id != "interpolation" }.forEach {
          next = selection(next, it.id, true)
          count++
        }
      }
      if (obj.optBoolean("disableAll", false)) {
        next = emptySet()
        count = algorithms.size
      }

      obj.optJSONArray("enabledIndices")?.let { arr ->
        for (i in 0 until arr.length()) {
          val alg = resolveAlgorithm(arr.get(i).toString()) ?: continue
          next = selection(next, alg.id, true)
          count++
        }
      }
      obj.optJSONArray("enabledIds")?.let { arr ->
        for (i in 0 until arr.length()) {
          val alg = resolveAlgorithm(arr.get(i).toString()) ?: continue
          next = selection(next, alg.id, true)
          count++
        }
      }
      obj.optJSONArray("enable")?.let { arr ->
        for (i in 0 until arr.length()) {
          val alg = resolveAlgorithm(arr.get(i).toString()) ?: continue
          next = selection(next, alg.id, true)
          count++
        }
      }
      obj.optJSONArray("disable")?.let { arr ->
        for (i in 0 until arr.length()) {
          val alg = resolveAlgorithm(arr.get(i).toString()) ?: continue
          next = selection(next, alg.id, false)
          count++
        }
      }

      obj.optJSONObject("algorithms")?.let { mapObj ->
        val keys = mapObj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          val v = mapObj.optBoolean(k, false)
          val token = k.substringAfter("_", k)
          val alg = resolveAlgorithm(k) ?: resolveAlgorithm(token) ?: continue
          next = selection(next, alg.id, v)
          count++
        }
      }

      // Support flat object {"lanczos": true, "debanding": false, "1": true, "6": false}
      val reservedKeys = setOf(
        "engine", "totalCatalogue", "preserveCinematicFps", "frameInterpolationDefault",
        "masterEnabled", "enabled", "bypassToOriginal", "bypass", "replace",
        "enableAll", "disableAll", "enabledIds", "enabledIndices", "enable", "disable",
        "algorithms", "values",
      )
      val rootKeys = obj.keys()
      while (rootKeys.hasNext()) {
        val k = rootKeys.next()
        if (k in reservedKeys) continue
        val rawVal = obj.opt(k)
        if (rawVal is Boolean) {
          val token = k.substringAfter("_", k)
          val alg = resolveAlgorithm(k) ?: resolveAlgorithm(token) ?: continue
          next = selection(next, alg.id, rawVal)
          count++
        }
      }

      val updatedValues = mutableMapOf<String, String>()
      obj.optJSONObject("values")?.let { valObj ->
        val vKeys = valObj.keys()
        while (vKeys.hasNext()) {
          val k = vKeys.next()
          val alg = resolveAlgorithm(k) ?: continue
          updatedValues[alg.id] = valObj.optString(k)
        }
      }

      RexJsonApplyResult(
        enabledIds = next,
        bypassOriginal = bypass,
        masterEnabled = master,
        values = updatedValues,
        appliedCount = count,
      )
    }.getOrElse { err ->
      RexJsonApplyResult(currentEnabled, error = "Invalid JSON: ${err.message ?: "parse error"}")
    }
  }

  fun preset(name: String): Set<String> = when (name) {
    "Battery Saver" -> setOf("mitchell", "dithering", "tone_mapping", "hardware_decoding", "gpu_shaders")
    "High Quality" -> defaults - "spline36" + setOf("lanczos", "sigmoid", "deringing", "debanding")
    "Maximum Quality" -> defaults - "spline36" + setOf("lanczos", "sigmoid", "deringing", "debanding")
    else -> defaults
  }

  /** Master-on owns the native switches. Master-off or bypass restores the pre-REX player settings. */
  fun plan(enabled: Set<String>, values: Map<String, String>, constrained: Boolean): Map<String, String> = buildMap {
    put("hwdec", if ("hardware_decoding" in enabled) "mediacodec,auto" else "no")
    if ("gpu_shaders" !in enabled) return@buildMap
    fun intensity(id: String, fallback: Int, range: IntRange): Int =
      (values[id]?.toIntOrNull() ?: fallback).coerceIn(range)
    fun choice(id: String, fallback: String): String =
      values[id]?.takeIf { it in algorithms.first { a -> a.id == id }.choices } ?: fallback

    val primaryScaler = listOf("lanczos", "spline36", "mitchell").firstOrNull { it in enabled }
    val mappedScaler = when {
      primaryScaler != null -> primaryScaler
      "edge_directed_interpolation" in enabled || "nedi" in enabled -> "ewa_lanczos"
      "easu" in enabled || "fsr_spatial_upscaling" in enabled -> "lanczos"
      "adaptive_resampling" in enabled -> "spline36"
      else -> null
    }
    put("scale", if (constrained) "bilinear" else mappedScaler ?: "bilinear")
    put("sigmoid-upscaling", "no")
    put("linear-downscaling", "no")
    put("correct-downscaling", "no")
    put("deband", "no")
    put("scale-antiring", "0")
    put("sharpen", "0")
    put("gamma", "0")
    put("tone-mapping", "clip")
    put("dither-depth", "no")

    if (primaryScaler == "lanczos" && !constrained) {
      put("scale-radius", intensity("lanczos", 3, 2..4).toString())
    }
    val sigmoidRequested = enabled.any {
      it in setOf(
        "sigmoid", "easu", "fsr_spatial_upscaling", "subpixel_detail_reconstruction",
        "adaptive_contrast_enhancement", "perceptual_contrast_enhancement",
        "adaptive_luma_enhancement", "perceptual_sharpness", "halo_suppression",
      )
    }
    if (sigmoidRequested) put("sigmoid-upscaling", if (constrained) "no" else "yes")

    if ("correct_downscaling" in enabled || "adaptive_resampling" in enabled || "content_adaptive_filtering" in enabled) {
      put("dscale", if (constrained) "bilinear" else mappedScaler ?: "spline36")
      put("linear-downscaling", if (constrained) "no" else "yes")
      put("correct-downscaling", "yes")
    }

    val debandActive = enabled.any {
      it in setOf(
        "debanding", "ringing_blocking_suppression", "adaptive_deblocking", "frequency_separation",
      )
    }
    if (debandActive) {
      put("deband", if (constrained) "no" else "yes")
      put("deband-threshold", intensity("debanding", 12, 4..48).toString())
      put("deband-iterations", if (values["preset"] == "Maximum Quality" && !constrained) "2" else "1")
      put("deband-grain", "0") // Never masquerade added noise as restored grain.
    }

    val antiringActive = enabled.any {
      it in setOf(
        "deringing", "ringing_artifact_suppression", "ringing_blocking_suppression", "rcas",
        "edge_detection", "local_detail_enhancement",
        "perceptual_sharpness", "halo_suppression",
      )
    }
    if (antiringActive) {
      put("scale-antiring", (intensity("deringing", 70, 0..100) / 100.0).toString())
    }

    if ("chroma_noise_reduction" in enabled || "ringing_artifact_suppression" in enabled || "chroma_reconstruction" in enabled) {
      put("cscale-antiring", (intensity("chroma_noise_reduction", 70, 0..100) / 100.0).toString())
    }
    if ("chroma_upsampling" in enabled) {
      put("cscale", if (constrained) "bilinear" else choice("chroma_upsampling", "spline36"))
    } else if ("chroma_reconstruction" in enabled) {
      put("cscale", if (constrained) "bilinear" else choice("chroma_reconstruction", "spline36"))
    }

    val sharpenActive = enabled.any {
      it in setOf(
        "sharpening", "adaptive_local_sharpening", "multi_scale_sharpening", "unsharp_mask",
        "motion_adaptive_sharpening", "rcas", "fsr_spatial_upscaling", "adaptive_sharpening_shader",
        "laplacian_filtering", "high_pass_filtering", "local_detail_enhancement",
        "mid_frequency_detail", "perceptual_sharpness",
      )
    }
    if (sharpenActive) {
      put("sharpen", if (constrained) "0" else (intensity("sharpening", 10, 0..30) / 100.0).toString())
    }

    // Prime the native display queue before enabling blending; no explicit FPS conversion (preserve 24 FPS by default).
    put("video-sync", if (enabled.any { it in setOf("interpolation", "resampling", "display_sync") } && !constrained) "display-resample" else "audio")
    put("interpolation", if ("interpolation" in enabled && !constrained) "yes" else "no")
    if ("interpolation" in enabled && !constrained) {
      put("tscale", if ("adaptive_motion_estimation" in enabled) choice("adaptive_motion_estimation", "oversample") else "oversample")
    }

    if ("tone_mapping" in enabled || "global_tone_mapping" in enabled) {
      put("tone-mapping", choice("tone_mapping", "auto"))
    }
    if ("adaptive_tone_mapping" in enabled || "highlight_recovery" in enabled) {
      put("hdr-compute-peak", choice("adaptive_tone_mapping", "auto"))
    }
    if ("colour_management" in enabled || "colour_constancy" in enabled) {
      put("target-prim", choice("colour_management", "auto"))
    }
    if ("hdr_sdr" in enabled) {
      put("target-trc", "bt.1886")
      put("target-prim", "bt.709") // SDR target overrides manual primaries; one colour pass.
    }
    if ("gamma" in enabled || "adaptive_gamma_correction" in enabled || "shadow_detail_recovery" in enabled || "adaptive_luma_enhancement" in enabled) {
      put("gamma", intensity("gamma", 0, -20..20).toString())
    }
    if ("local_contrast_enhancement" in enabled || "adaptive_contrast_enhancement" in enabled || "perceptual_contrast_enhancement" in enabled) {
      put("contrast", intensity("local_contrast_enhancement", 0, -20..20).toString())
    }
    if ("dithering" in enabled || "auto_parameter_selection" in enabled) {
      put("dither-depth", choice("dithering", "auto"))
    }
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

/* SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 */
package app.infinity.mpvz.ui.player

import org.junit.Assert.*
import org.junit.Test

class RexVisionPolicyTest {
  @Test fun catalogContainsAll24DistinctTechniques() {
    assertEquals(24, RexVisionPolicy.algorithms.size)
    assertEquals(24, RexVisionPolicy.algorithms.map { it.id }.distinct().size)
    assertEquals(5, RexVisionPolicy.algorithms.map { it.category }.distinct().size)
    assertTrue(RexVisionPolicy.algorithms.all { it.description.isNotBlank() && it.impact in setOf("Low", "Medium", "High") })
  }

  @Test fun everyControlHasNativeBindingsOrAnHonestUnsupportedReason() {
    RexVisionPolicy.algorithms.forEach {
      assertTrue(it.title, it.options.isNotEmpty() || it.id == "gpu_shaders" || !it.unavailable.isNullOrBlank())
      if (it.unavailable != null) assertTrue(it.options.isEmpty())
    }
  }

  @Test fun presetsNeverEnableInterpolationOrInventMotionProcessing() {
    listOf("Battery Saver", "Balanced", "High Quality", "Maximum Quality").forEach {
      val ids = RexVisionPolicy.preset(it)
      assertFalse("interpolation" in ids)
      assertFalse("sharpening" in ids)
      assertFalse("hdr_sdr" in ids)
      assertFalse("gamma" in ids)
      assertTrue(ids.none { id -> RexVisionPolicy.algorithms.first { a -> a.id == id }.unavailable != null })
      val plan = RexVisionPolicy.plan(ids, mapOf("preset" to it), false)
      assertEquals("no", plan["interpolation"])
      assertEquals("audio", plan["video-sync"])
      assertFalse(plan.keys.any { name -> "fps" in name || name in setOf("vf", "speed", "glsl-shaders") })
    }
  }

  @Test fun scalingSelectionIsExclusiveAndCanBeDisabled() {
    val selected = RexVisionPolicy.selection(RexVisionPolicy.defaults, "lanczos", true)
    assertEquals(setOf("lanczos"), selected intersect RexVisionPolicy.scalers)
    assertTrue((RexVisionPolicy.selection(selected, "lanczos", false) intersect RexVisionPolicy.scalers).isEmpty())
  }

  @Test fun sharedResamplingControlsMirrorOneOptionAndInterpolationDependencies() {
    val interpolated = RexVisionPolicy.selection(RexVisionPolicy.defaults, "interpolation", true)
    assertTrue(interpolated.containsAll(setOf("interpolation", "resampling", "display_sync")))
    val stopped = RexVisionPolicy.selection(interpolated, "display_sync", false)
    assertTrue(stopped.none { it in setOf("interpolation", "resampling", "display_sync") })
    val resumed = RexVisionPolicy.selection(stopped, "resampling", true)
    assertTrue(resumed.containsAll(setOf("resampling", "display_sync")))
    assertFalse("interpolation" in resumed)
  }

  @Test fun sourceCadenceAndColourRemainNeutralByDefault() {
    val plan = RexVisionPolicy.plan(RexVisionPolicy.defaults, emptyMap(), false)
    assertEquals("spline36", plan["scale"])
    assertEquals("auto", plan["tone-mapping"])
    assertEquals("auto", plan["dither-depth"])
    assertNull(plan["target-trc"])
    assertEquals("0", plan["gamma"])
    assertEquals("0", plan["sharpen"])
    assertNull(plan["tscale"])
  }

  @Test fun strengthInputsAreClampedAndMalformedValuesUseSafeFallbacks() {
    val ids = RexVisionPolicy.defaults - "spline36" + setOf("lanczos", "deringing", "sharpening", "gamma", "debanding")
    val plan = RexVisionPolicy.plan(ids, mapOf("lanczos" to "999", "deringing" to "-10", "sharpening" to "1000", "gamma" to "-999", "debanding" to "NaN"), false)
    assertEquals("4", plan["scale-radius"])
    assertEquals("0.0", plan["scale-antiring"])
    assertEquals("0.3", plan["sharpen"])
    assertEquals("-20", plan["gamma"])
    assertEquals("12", plan["deband-threshold"])
    assertEquals("0", plan["deband-grain"])
  }

  @Test fun invalidChoicesCannotInjectMpvOptions() {
    val plan = RexVisionPolicy.plan(RexVisionPolicy.defaults + "colour_management", mapOf("tone_mapping" to "fake", "dithering" to "99", "colour_management" to "bt2020;vf=lavfi"), false)
    assertEquals("auto", plan["tone-mapping"])
    assertEquals("auto", plan["dither-depth"])
    assertEquals("auto", plan["target-prim"])
  }

  @Test fun hdrToSdrUsesOneNativeTargetAndNoArtificialHdrShader() {
    val plan = RexVisionPolicy.plan(RexVisionPolicy.defaults + setOf("hdr_sdr", "colour_management"), mapOf("colour_management" to "bt.2020"), false)
    assertEquals("bt.1886", plan["target-trc"])
    assertEquals("bt.709", plan["target-prim"])
    assertEquals("auto", plan["tone-mapping"])
    assertFalse(plan.containsKey("glsl-shaders"))
  }

  @Test fun blendingExplicitlyRequiresDisplayTimedQueueNotForcedFps() {
    val plan = RexVisionPolicy.plan(RexVisionPolicy.defaults + "interpolation", emptyMap(), false)
    assertEquals("yes", plan["interpolation"])
    assertEquals("display-resample", plan["video-sync"])
    assertEquals("oversample", plan["tscale"])
    assertFalse(plan.keys.any { "fps" in it })
  }

  @Test fun resamplingAloneDoesNotSynthesizeFrames() {
    for (id in listOf("resampling", "display_sync")) {
      val plan = RexVisionPolicy.plan(RexVisionPolicy.defaults + id, emptyMap(), false)
      assertEquals("no", plan["interpolation"])
      assertEquals("display-resample", plan["video-sync"])
    }
  }

  @Test fun constrainedPlaybackShedsGpuWorkWithoutTouchingAudioOrSpeed() {
    val plan = RexVisionPolicy.plan(RexVisionPolicy.preset("Maximum Quality") + setOf("interpolation", "sharpening"), emptyMap(), true)
    assertEquals("bilinear", plan["scale"])
    assertEquals("no", plan["deband"])
    assertEquals("no", plan["sigmoid-upscaling"])
    assertEquals("0", plan["sharpen"])
    assertEquals("no", plan["interpolation"])
    assertEquals("audio", plan["video-sync"])
    assertNull(plan["tscale"])
    assertFalse(plan.keys.any { it in setOf("speed", "audio-delay", "vf", "glsl-shaders") })
  }

  @Test fun maximumQualityOnlySpendsMoreOnNativeReconstruction() {
    val hq = RexVisionPolicy.plan(RexVisionPolicy.preset("High Quality"), mapOf("preset" to "High Quality"), false)
    val max = RexVisionPolicy.plan(RexVisionPolicy.preset("Maximum Quality"), mapOf("preset" to "Maximum Quality"), false)
    assertEquals("1", hq["deband-iterations"])
    assertEquals("2", max["deband-iterations"])
  }

  @Test fun gpuGateDoesNotClaimToDisableCoreRenderer() {
    val plan = RexVisionPolicy.plan(RexVisionPolicy.defaults - "gpu_shaders", emptyMap(), false)
    assertEquals(setOf("hwdec"), plan.keys)
    assertEquals("mediacodec,auto", plan["hwdec"])
  }

  @Test fun budgetsHaveSustainedDropAndRecoveryHysteresis() {
    val budget = RexVisionBudget()
    assertFalse(budget.sample(3, false, false))
    assertFalse(budget.sample(3, false, false))
    assertTrue(budget.sample(3, false, false))
    repeat(29) { assertTrue(budget.sample(0, false, false)) }
    assertFalse(budget.sample(0, false, false))
  }

  @Test fun offSwitchesActuallyDisableTheManagedPasses() {
    val plan = RexVisionPolicy.plan(setOf("gpu_shaders"), emptyMap(), false)
    assertEquals("no", plan["hwdec"])
    assertEquals("bilinear", plan["scale"])
    assertEquals("no", plan["deband"])
    assertEquals("no", plan["sigmoid-upscaling"])
    assertEquals("no", plan["correct-downscaling"])
    assertEquals("no", plan["dither-depth"])
    assertEquals("clip", plan["tone-mapping"])
  }

  @Test fun correctDownscalingSelectsAConvolutionKernelRatherThanIneffectiveBilinear() {
    assertEquals("spline36", RexVisionPolicy.plan(RexVisionPolicy.defaults, emptyMap(), false)["dscale"])
    assertEquals("bilinear", RexVisionPolicy.plan(RexVisionPolicy.defaults, emptyMap(), true)["dscale"])
  }

  @Test fun thermalAndPauseSamplesDoNotOscillate() {
    val budget = RexVisionBudget()
    assertTrue(budget.sample(0, true, false))
    repeat(50) { assertTrue(budget.sample(0, false, true)) }
    repeat(29) { assertTrue(budget.sample(0, false, false)) }
    assertFalse(budget.sample(0, false, false))
  }
}

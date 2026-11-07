/*
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 */
package app.infinity.mpvz.domain.anime4k

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrameRestorationPolicyTest {
  @Test
  fun profilesUseDistinctRestorationNetworks() {
    assertEquals("Anime4K_Restore_CNN_L.glsl", FrameRestorationProfile.MAXIMUM_QUALITY.shaderFileName)
    assertEquals("Anime4K_Restore_CNN_Soft_M.glsl", FrameRestorationProfile.BALANCED.shaderFileName)
    assertEquals("Anime4K_Restore_CNN_Soft_S.glsl", FrameRestorationProfile.REAL_TIME.shaderFileName)
    assertEquals(3, FrameRestorationProfile.entries.map { it.shaderFileName }.distinct().size)
  }

  @Test
  fun strengthChangesOnlyTheFinalLearnedResidualBlend() {
    val source = "vec4 hook() {\n  return result + MAIN_tex(MAIN_pos);\n}"
    assertEquals(
      "vec4 hook() {\n  return MAIN_tex(MAIN_pos) + (0.35 * result);\n}",
      scaleFrameRestorationResidual(source, 35),
    )
  }

  @Test
  fun unknownOrAmbiguousShaderLayoutsAreRejected() {
    assertNull(scaleFrameRestorationResidual("void hook() {}", 50))
    val duplicate = "return result + MAIN_tex(MAIN_pos); return result + MAIN_tex(MAIN_pos);"
    assertNull(scaleFrameRestorationResidual(duplicate, 50))
  }

  @Test
  fun strengthIsClampedToSupportedRange() {
    assertEquals(0, normalizeFrameRestorationStrength(-20))
    assertEquals(100, normalizeFrameRestorationStrength(120))
    assertNotNull(scaleFrameRestorationResidual("return result + MAIN_tex(MAIN_pos);", 120))
  }
}

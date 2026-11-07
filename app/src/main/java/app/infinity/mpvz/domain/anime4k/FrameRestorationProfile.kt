/*
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 *
 * Copyright (c) 2026 ZHINFINITY. Mpv∞ Covered Code.
 * See LICENSE for terms.
 */

package app.infinity.mpvz.domain.anime4k

import java.util.Locale

/** Profiles select different bundled CNN shader variants; they are not temporal modes. */
enum class FrameRestorationProfile(
  val title: String,
  val summary: String,
  val shaderFileName: String,
) {
  MAXIMUM_QUALITY(
    title = "Maximum quality",
    summary = "Full-strength CNN restoration model; highest shader workload.",
    shaderFileName = "Anime4K_Restore_CNN_L.glsl",
  ),
  BALANCED(
    title = "Balanced",
    summary = "Softer medium CNN model for a compromise between detail and playback load.",
    shaderFileName = "Anime4K_Restore_CNN_Soft_M.glsl",
  ),
  REAL_TIME(
    title = "Real-time",
    summary = "Smaller soft CNN model; lowest shader workload of these profiles.",
    shaderFileName = "Anime4K_Restore_CNN_Soft_S.glsl",
  ),
}

internal fun normalizeFrameRestorationStrength(value: Int): Int = value.coerceIn(0, 100)

/**
 * The Anime4K CNN restore pass predicts a residual and adds it to the input frame.
 * Scale only that final residual so strength is a real model-output blend, not UI-only metadata.
 * Refuse unknown shader layouts rather than silently changing an arbitrary expression.
 */
internal fun scaleFrameRestorationResidual(
  source: String,
  strength: Int,
): String? {
  val normalized = normalizeFrameRestorationStrength(strength)
  val marker = "return result + MAIN_tex(MAIN_pos);"
  val firstMatch = source.indexOf(marker)
  if (firstMatch < 0 || firstMatch != source.lastIndexOf(marker)) return null
  val amount = String.format(Locale.US, "%.2f", normalized / 100.0f)
  return source.replace(marker, "return MAIN_tex(MAIN_pos) + ($amount * result);")
}

/*
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 *
 * Copyright (c) 2026 ZHINFINITY. Mpv∞ Covered Code.
 * See LICENSE for terms.
 */

package app.infinity.mpvz.domain.restoration

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Loads and owns the Remaster DRUNet Student ncnn model.
 *
 * The Media3 Flow sink calls this one-frame RGBA8 SDR staging API after its color-conversion pass.
 * The standard build may execute on ncnn Vulkan; CPU remains the fallback. The caller owns frame
 * readback/upload and must enforce its own resolution/performance limits. AHardwareBuffer capability is reported independently.
 */
class DrunetNcnnEngine(context: Context) : AutoCloseable {
  private val appContext = context.applicationContext
  private val native = DrunetNcnnNative()
  private val modelDirectory = File(appContext.filesDir, "ai/restoration/remaster_drunet")
  private var nativeHandle = 0L

  data class Capabilities(
    val cpu: Boolean,
    val vulkan: Boolean,
    val androidHardwareBufferImport: Boolean,
  )

  data class LoadedModel(
    val backend: String,
    val inputBlob: String,
    val outputBlob: String,
    val modelDirectory: File,
  )

  @Synchronized
  fun capabilities(): Capabilities {
    val flags = native.nativeCapabilities()
    return Capabilities(
      cpu = flags and CAPABILITY_CPU != 0,
      vulkan = flags and CAPABILITY_VULKAN != 0,
      androidHardwareBufferImport = flags and CAPABILITY_AHB_IMPORT != 0,
    )
  }

  /** Load the bundled ncnn graph. Vulkan is preferred, with optional CPU fallback. */
  @Synchronized
  fun load(
    preferVulkan: Boolean = true,
    allowCpuFallback: Boolean = true,
    gpuIndex: Int = 0,
  ): LoadedModel? {
    closeNativeHandle()
    val param = copyBundledAsset("drunet_student.param") ?: return null
    val model = copyBundledAsset("drunet_student.bin") ?: return null
    val checksums = copyBundledAsset("SHA256SUMS") ?: return null
    if (!verifyChecksum(param, checksums) || !verifyChecksum(model, checksums)) {
      Log.e(TAG, "DRUNet ncnn asset checksum mismatch; refusing to load model")
      return null
    }
    val metadata = readMetadata() ?: return null
    val inputBlob = metadata.optString("input_blob", "input").takeIf(String::isNotBlank) ?: return null
    val outputBlob = metadata.optString("output_blob", "output").takeIf(String::isNotBlank) ?: return null

    val handle = native.nativeCreate(
      param.absolutePath,
      model.absolutePath,
      inputBlob,
      outputBlob,
      preferVulkan,
      allowCpuFallback,
      gpuIndex,
    )
    if (handle == 0L) {
      Log.e(TAG, "ncnn could not load the DRUNet Student model")
      return null
    }
    nativeHandle = handle
    return LoadedModel(
      backend = native.nativeBackendName(handle),
      inputBlob = inputBlob,
      outputBlob = outputBlob,
      modelDirectory = modelDirectory,
    )
  }

  /**
   * Process an RGBA8 SDR frame. Buffers must be direct and have at least width*height*4 bytes.
   * Returns inference+staging time in microseconds, or null when the frame could not be processed.
   */
  @Synchronized
  fun processRgba(
    input: ByteBuffer,
    output: ByteBuffer,
    width: Int,
    height: Int,
    strength: Float = 1f,
  ): Long? {
    val handle = nativeHandle
    if (
      handle == 0L || !input.isDirect || !output.isDirect || input.position() != 0 ||
      output.position() != 0 || width <= 0 || height <= 0
    ) return null
    val required = width.toLong() * height.toLong() * 4L
    if (required > Int.MAX_VALUE || input.capacity().toLong() < required || output.capacity().toLong() < required) return null
    val latencyUs = native.nativeProcessRgba(handle, input, output, width, height, strength.coerceIn(0f, 1f))
    return latencyUs.takeIf { it >= 0L }
  }

  @Synchronized
  override fun close() {
    closeNativeHandle()
  }

  private fun closeNativeHandle() {
    val handle = nativeHandle
    nativeHandle = 0L
    if (handle != 0L) native.nativeDestroy(handle)
  }

  private fun readMetadata(): JSONObject? = runCatching {
    appContext.assets.open("$ASSET_DIRECTORY/model.json").bufferedReader().use { reader ->
      JSONObject(reader.readText())
    }
  }.onFailure { error ->
    Log.e(TAG, "DRUNet model metadata is not packaged", error)
  }.getOrNull()

  private fun verifyChecksum(file: File, checksumFile: File): Boolean = runCatching {
    val expected = checksumFile.readLines().firstOrNull { line ->
      line.trim().endsWith("  ${file.name}") || line.trim().endsWith(" *${file.name}")
    }?.trim()?.substringBefore(' ')?.lowercase() ?: return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) } == expected
  }.getOrDefault(false)

  private fun copyBundledAsset(fileName: String): File? = runCatching {
    modelDirectory.mkdirs()
    val target = File(modelDirectory, fileName)
    val temporary = File(modelDirectory, "$fileName.tmp")
    appContext.assets.open("$ASSET_DIRECTORY/$fileName").use { input ->
      temporary.outputStream().use { output -> input.copyTo(output) }
    }
    if (target.exists()) target.delete()
    check(temporary.renameTo(target)) { "Unable to install $fileName" }
    target
  }.onFailure { error ->
    Log.e(TAG, "DRUNet asset $fileName is not available", error)
  }.getOrNull()


  companion object {
    private const val TAG = "DrunetNcnnEngine"
    private const val ASSET_DIRECTORY = "ai/restoration/remaster_drunet"
    private const val CAPABILITY_CPU = 1 shl 0
    private const val CAPABILITY_VULKAN = 1 shl 1
    private const val CAPABILITY_AHB_IMPORT = 1 shl 2
  }
}

internal class DrunetNcnnNative {
  external fun nativeCapabilities(): Int
  external fun nativeGpuDeviceName(gpuIndex: Int): String
  external fun nativeCreate(
    paramPath: String,
    modelPath: String,
    inputBlob: String,
    outputBlob: String,
    preferVulkan: Boolean,
    allowCpuFallback: Boolean,
    gpuIndex: Int,
  ): Long
  external fun nativeDestroy(handle: Long)
  external fun nativeBackendName(handle: Long): String
  external fun nativeProcessRgba(
    handle: Long,
    input: ByteBuffer,
    output: ByteBuffer,
    width: Int,
    height: Int,
    strength: Float,
  ): Long

  companion object {
    init {
      System.loadLibrary("frame_restoration_ncnn")
    }
  }
}

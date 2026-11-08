# DRUNet Android inference and Media3 integration

The repository bundles the verified Remaster DRUNet Student ncnn model at `app/src/main/assets/ai/restoration/remaster_drunet/`. The Standard product flavor builds the pinned Tencent ncnn runtime with Vulkan; the noVulkan flavor builds ncnn for CPU only. The app verifies both model files against `SHA256SUMS` before loading them.

`DrunetNcnnEngine` owns the model and exposes a direct RGBA8 SDR frame API. The Media3 Flow sink now has an explicit, default-off DRUNet control. When enabled, accepted SDR source frames pass through this experimental path:

1. Media3 decodes to the sink's private `SurfaceTexture`.
2. The existing color-conversion pass stages the frame into the sink's RGBA8 frame target.
3. GLES reads that frame into a reusable direct CPU buffer; ncnn runs same-resolution restoration with Vulkan preferred (and CPU fallback enabled).
4. The restored RGBA8 result is uploaded to a temporary GLES texture and blitted back into the same frame target before Flow consumes/presents it.

The model is loaded off the GLES callback thread, and frames bypass restoration while loading or after a processing failure. The runtime strength is blended in the native inference routine. The setting can be changed during playback without recreating the player. To limit inference memory use, frames with a longest edge above 512 pixels are bypassed unchanged; no downscale is performed.

## Limitations

- This is **not zero-copy**: `glReadPixels`, synchronous ncnn inference and a texture upload occur on the source-frame callback path. It can reduce frame rate substantially, stall playback, increase power use and heat. Keep it disabled for normal playback until device-specific benchmarks prove otherwise.
- The current safe-size limit means only frames up to 512 px on their longest edge are processed. Larger frames continue through the normal renderer without DRUNet. This conservative limit avoids attempting large intermediate feature maps on mobile GPUs; it is not a statement that the model cannot accept larger input shapes.
- It is limited to SDR frames accepted by the custom Media3 Flow renderer. HDR/Dolby Vision, MPV playback, DRM and formats routed to Media3's stock renderer are not processed.
- Vulkan availability reported by ncnn is separate from the libplacebo/Vulkan frame-sharing context used by Media3 Flow. This initial integration uses CPU-visible RGBA staging; it does not import the Flow AHardwareBuffer directly into ncnn.
- It is a spatial per-frame restoration model, not temporal denoising or frame interpolation. No real-time 1080p/60 performance is claimed. Validate detail retention, halos, ringing, colour stability, inference latency, dropped frames, memory, battery and thermal behavior on target devices.
- The RGBA8 path is SDR only and does not preserve HDR or 10-bit source precision.

The existing Anime4K shader restoration preference remains independent and continues to target the MPV shader path. DRUNet is a separate Media3-only option.

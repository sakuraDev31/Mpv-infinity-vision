# Frame restoration implementation status

## Current prototype

The first implementation uses the repository's bundled Anime4K CNN GLSL restoration networks as a same-resolution GPU shader pass. A generated copy scales the final CNN-predicted residual against the original frame, providing a real 0–100 strength control. The Decoder screen exposes three selectable network profiles and the PlayerViewModel applies the selected pass when MPV's `gpu` or `gpu-next` renderer owns an active video.

This is spatial per-frame CNN shader inference. It is **not** a temporal video denoiser, a general-purpose live-action deblocking model, an ncnn runtime, or a CPU fallback. Anime4K weights were designed for animation; live-action HEVC footage must be evaluated for texture hallucination, ringing and detail loss before these defaults are promoted.

## Next neural backend stage

1. Select a pretrained restoration/deblocking model trained on live-action HEVC compression artefacts and publish its exact weights, licence, input range and colour-space contract.
2. Build ncnn for Android arm64 with ARM NEON CPU execution and optional Vulkan. Probe Vulkan device extensions and formats at runtime; do not assume Mali-G57 zero-copy AHardwareBuffer import is supported.
3. Add decoder-to-inference GPU frame interop and avoid CPU readback where supported. Keep a CPU upload/download fallback only when measurements justify it.
4. Benchmark 1080p H.265 at 24 fps for frame latency, dropped frames, GPU/CPU load, RAM, thermal throttling and battery. GPU is not automatically faster than ARM NEON on every mobile SoC.
5. Implement bounded queues for live, balanced and ahead-processing modes. Do not allocate a fixed 5–10 second queue of decoded 1080p frames without a memory budget; estimate frame bytes from the actual pixel format and bit depth, cap memory, and fall back to live processing when the cap is reached.
6. Preserve source precision/bit depth through decode and processing where the model and renderer allow it. Do not up-convert 8-bit samples and describe the result as recovered source bit depth.

## Public test material

- 1080p H.265 Big Buck Bunny: https://test-videos.co.uk/bigbuckbunny/mp4-h265
- libde265 H.265 sample set: https://github.com/strukturag/libde265/tree/master/teststreams
- Microsoft HEVC test content (includes 1080p Main 8-bit and 4K/10-bit clips): https://learn.microsoft.com/en-us/playready/advanced/testcontent/playready-3x-test-content

Use short clips with static fine textures, foliage, text, motion and gradients. Keep the original unprocessed file for A/B and objective comparisons.

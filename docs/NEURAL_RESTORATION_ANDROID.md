# DRUNet Android inference stage

The Standard product flavor builds the pinned Tencent ncnn runtime with its Vulkan backend; the noVulkan flavor builds the same ncnn runtime with CPU inference only. The app uses the source build at API 26+ so the Vulkan build retains ncnn's Android HardwareBuffer import API when the device exposes `VK_ANDROID_external_memory_android_hardware_buffer`.

`DrunetNcnnEngine` is the Kotlin model owner. It verifies the model asset checksums, loads the `.param` and `.bin`, prefers Vulkan with optional CPU fallback, and exposes capability data for CPU, Vulkan compute and AHardwareBuffer import.

The current JNI frame API accepts direct RGBA8 SDR buffers, converts them to the model's normalized RGB FP16 tensor, pads dimensions to multiples of eight, runs same-resolution inference, blends the predicted result with the source using a 0..1 restoration strength, and returns a microsecond timing. This interface is for correctness and device benchmarking. It is **not yet connected to Media3FlowVideoSink or MPV's video output**; its CPU-visible buffer path is intentionally not treated as a zero-copy real-time solution.

The existing Media3Flow sink already stages decoded SDR video into RGBA8 AHardwareBuffers. A future playback path must import those buffers into ncnn's own Vulkan device, verify AHB import support at runtime, synchronize access to shared buffers, and export the restored output back into the presentable frame queue. HDR/10-bit and exact source-bit-depth preservation are not supported by this RGBA8 prototype.

# Remaster DRUNet Student (ncnn conversion)

This directory receives a generated ncnn model from `.github/workflows/prepare-drunet-ncnn.yml`.
The workflow downloads the upstream MIT-licensed `drunet_student.pth`, verifies its published SHA-256,
loads the exact student architecture (`UNetRes`, nc=[16,32,64,128], nb=2) from the upstream KAIR submodule,
exports an FP16 TorchScript trace, uses PNNX with 128x128 and 512x512 input traces, converts it using the pinned Tencent ncnn converter,
and executes a CPU ncnn inference smoke test with the graph's FP16 input contract,
and only then commits the resulting `.param` and `.bin` files.

Upstream model: https://huggingface.co/seantempesta/remaster-drunet
Upstream project/source: https://github.com/seantempesta/remaster
Upstream card: https://huggingface.co/seantempesta/remaster-drunet/blob/main/README.md
Upstream source model: 2.14 MB ONNX; 1.06M-parameter DRUNet Student.

Contract from the upstream model card:
- Input/output values: RGB, NCHW, normalized 0..1; upstream ONNX I/O tensors are FP16
- Scale: 1x (same resolution)
- Dynamic spatial shapes; the traced/validated sample shapes are 128x128 and 512x512 (dimensions must be divisible by 8)
- Intended task: compression artefact reduction and detail recovery on video frames
- Upstream reports 1080p HEVC training; its published performance numbers are on an RTX 3060, not mobile hardware.

Do not use this model for HDR/BT.2020 without an explicit colour-managed implementation. The current
ncnn conversion is a CPU graph-validation step; GPU performance and video-frame integration remain
separate tasks.

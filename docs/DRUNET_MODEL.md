# Remaster DRUNet Student (ncnn conversion)

This directory receives a generated ncnn model from `.github/workflows/prepare-drunet-ncnn.yml`.
The workflow downloads the upstream MIT-licensed `drunet_student.onnx`, verifies its published SHA-256,
converts it using the pinned Tencent ncnn converter, executes a CPU ncnn inference smoke test,
and only then commits the resulting `.param` and `.bin` files.

Upstream model: https://huggingface.co/seantempesta/remaster-drunet
Upstream card: https://huggingface.co/seantempesta/remaster-drunet/blob/main/README.md
Upstream source model: 2.14 MB ONNX; 1.06M-parameter DRUNet Student.

Contract from the upstream model card:
- Input/output: RGB float32, NCHW, 0..1
- Scale: 1x (same resolution)
- Input width/height must be divisible by 8
- Intended task: compression artefact reduction and detail recovery on video frames
- Upstream reports 1080p HEVC training; its published performance numbers are on an RTX 3060, not mobile hardware.

Do not use this model for HDR/BT.2020 without an explicit colour-managed implementation. The current
ncnn conversion is a CPU graph-validation step; GPU performance and video-frame integration remain
separate tasks.

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>

#include "net.h"

int main(int argc, char** argv) {
    if (argc != 3) {
        std::fprintf(stderr, "usage: neural_model_smoke <model.param> <model.bin>\n");
        return 2;
    }

    ncnn::Net net;
    net.opt.use_vulkan_compute = false;
    net.opt.num_threads = 1;
    net.opt.use_packing_layout = false;
    net.opt.use_fp16_storage = false;
    net.opt.use_fp16_arithmetic = false;

    const int param_result = net.load_param(argv[1]);
    if (param_result != 0) {
        std::fprintf(stderr, "ncnn load_param failed: %d\n", param_result);
        return 10;
    }
    const int model_result = net.load_model(argv[2]);
    if (model_result != 0) {
        std::fprintf(stderr, "ncnn load_model failed: %d\n", model_result);
        return 11;
    }

    // The model card specifies RGB NCHW float32 in [0, 1] and dimensions divisible by eight.
    constexpr int width = 64;
    constexpr int height = 64;
    ncnn::Mat input(width, height, 3);
    for (int c = 0; c < input.c; ++c) {
        float* plane = input.channel(c);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                plane[y * width + x] = 0.15f + 0.1f * static_cast<float>((x + y + c) % 7) / 6.0f;
            }
        }
    }

    ncnn::Extractor extractor = net.create_extractor();
    extractor.set_light_mode(true);
    if (extractor.input("input", input) != 0) {
        std::fprintf(stderr, "ncnn input blob rejected\n");
        return 12;
    }
    ncnn::Mat output;
    if (extractor.extract("output", output) != 0) {
        std::fprintf(stderr, "ncnn output extraction failed\n");
        return 13;
    }
    if (output.empty() || output.w != width || output.h != height || output.c != 3 || output.elemsize != sizeof(float)) {
        std::fprintf(stderr, "unexpected output shape/type: %d x %d x %d, elemsize=%zu\n",
                     output.w, output.h, output.c, output.elemsize);
        return 14;
    }
    const float* values = static_cast<const float*>(output.data);
    const size_t count = output.total() * output.elempack;
    for (size_t i = 0; i < count; ++i) {
        if (!std::isfinite(values[i])) {
            std::fprintf(stderr, "non-finite model output at %zu\n", i);
            return 15;
        }
    }
    std::printf("DRUNet ncnn smoke test passed: %d x %d x %d float32 output, first=%0.6f\n",
                output.w, output.h, output.c, values[0]);
    return 0;
}

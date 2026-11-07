#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "net.h"

static bool resolveBlobNames(const char* paramPath, std::string& inputName, std::string& outputName) {
    std::ifstream file(paramPath);
    if (!file) return false;

    // ncnn param format: magic, layer/blob counts, then one layer per line.
    std::string line;
    if (!std::getline(file, line) || !std::getline(file, line)) return false;
    while (std::getline(file, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream row(line);
        std::string layerType;
        std::string layerName;
        int bottomCount = 0;
        int topCount = 0;
        if (!(row >> layerType >> layerName >> bottomCount >> topCount) ||
            bottomCount < 0 || topCount < 0 || bottomCount > 1024 || topCount > 1024) {
            continue;
        }
        std::vector<std::string> bottoms(static_cast<size_t>(bottomCount));
        std::vector<std::string> tops(static_cast<size_t>(topCount));
        bool complete = true;
        for (auto& blob : bottoms) complete = complete && static_cast<bool>(row >> blob);
        for (auto& blob : tops) complete = complete && static_cast<bool>(row >> blob);
        if (!complete) continue;
        if (layerType == "Input" && !tops.empty()) inputName = tops.front();
        if (!tops.empty()) outputName = tops.back();
    }
    return !inputName.empty() && !outputName.empty();
}

int main(int argc, char** argv) {
    if (argc != 3) {
        std::fprintf(stderr, "usage: neural_model_smoke <model.param> <model.bin>\n");
        return 2;
    }

    std::string inputBlob;
    std::string outputBlob;
    if (!resolveBlobNames(argv[1], inputBlob, outputBlob)) {
        std::fprintf(stderr, "could not resolve input/output blobs from %s\n", argv[1]);
        return 3;
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
    if (extractor.input(inputBlob.c_str(), input) != 0) {
        std::fprintf(stderr, "ncnn input blob rejected: %s\n", inputBlob.c_str());
        return 12;
    }
    ncnn::Mat output;
    if (extractor.extract(outputBlob.c_str(), output) != 0) {
        std::fprintf(stderr, "ncnn output extraction failed: %s\n", outputBlob.c_str());
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
    std::printf("DRUNet ncnn smoke test passed: input=%s output=%s shape=%d x %d x %d float32 first=%0.6f\n",
                inputBlob.c_str(), outputBlob.c_str(), output.w, output.h, output.c, values[0]);
    return 0;
}

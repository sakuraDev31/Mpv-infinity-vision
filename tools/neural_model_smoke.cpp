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


static bool runSmoke(ncnn::Net& net,
                     const std::string& inputBlob,
                     const std::string& outputBlob,
                     int width,
                     int height) {
    // The exported graph and Android JNI staging API both use normalized FP32 input.
    ncnn::Mat input(width, height, 3, sizeof(float));
    if (input.empty()) {
        std::fprintf(stderr, "could not allocate FP32 input tensor: %d x %d\n", width, height);
        return false;
    }

    for (int c = 0; c < input.c; ++c) {
        float* plane = static_cast<float*>(input.channel(c));
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                plane[y * width + x] = 0.15f + 0.1f * static_cast<float>((x + y + c) % 7) / 6.0f;
            }
        }
    }

    ncnn::Extractor extractor = net.create_extractor();
    extractor.set_light_mode(true);
    if (extractor.input(inputBlob.c_str(), input) != 0) {
        std::fprintf(stderr, "ncnn input blob rejected (%d x %d): %s\n",
                     width, height, inputBlob.c_str());
        return false;
    }

    ncnn::Mat output;
    if (extractor.extract(outputBlob.c_str(), output) != 0) {
        std::fprintf(stderr, "ncnn output extraction failed (%d x %d): %s\n",
                     width, height, outputBlob.c_str());
        return false;
    }
    if (output.empty() || output.w != width || output.h != height || output.c != 3 ||
        (output.elemsize != sizeof(float) && output.elemsize != sizeof(unsigned short))) {
        std::fprintf(stderr, "unexpected output for %d x %d: %d x %d x %d, elemsize=%zu\n",
                     width, height, output.w, output.h, output.c, output.elemsize);
        return false;
    }

    const size_t count = output.total() * output.elempack;
    for (size_t i = 0; i < count; ++i) {
        const float value = output.elemsize == sizeof(unsigned short)
            ? ncnn::float16_to_float32(static_cast<const unsigned short*>(output.data)[i])
            : static_cast<const float*>(output.data)[i];
        if (!std::isfinite(value)) {
            std::fprintf(stderr, "non-finite output at %zu for %d x %d\n", i, width, height);
            return false;
        }
    }

    std::printf("DRUNet ncnn smoke passed: input=%s output=%s shape=%d x %d x %d elem=%zu\n",
                inputBlob.c_str(), outputBlob.c_str(), output.w, output.h, output.c, output.elemsize);
    return true;
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

    const int paramResult = net.load_param(argv[1]);
    if (paramResult != 0) {
        std::fprintf(stderr, "ncnn load_param failed: %d\n", paramResult);
        return 10;
    }
    const int modelResult = net.load_model(argv[2]);
    if (modelResult != 0) {
        std::fprintf(stderr, "ncnn load_model failed: %d\n", modelResult);
        return 11;
    }

    // Validate both declared spatial sizes with the same FP32 contract used by Android.
    if (!runSmoke(net, inputBlob, outputBlob, 128, 128)) return 20;
    if (!runSmoke(net, inputBlob, outputBlob, 512, 512)) return 21;
    return 0;
}

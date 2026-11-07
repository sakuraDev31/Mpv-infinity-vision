#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cmath>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <thread>

#include "net.h"
#if NCNN_VULKAN
#include "gpu.h"
#endif

namespace {
constexpr const char* kTag = "DrunetNcnn";
constexpr jint kCapabilityCpu = 1 << 0;
constexpr jint kCapabilityVulkan = 1 << 1;
constexpr jint kCapabilityAhbImport = 1 << 2;

struct Runtime {
    ncnn::Net net;
    std::string input_blob;
    std::string output_blob;
    std::string backend = "CPU";
    bool ready = false;
};

#if NCNN_VULKAN
std::mutex g_gpu_mutex;
bool g_gpu_init_attempted = false;
int g_gpu_init_result = -1;

bool ensureGpuInstance() {
    std::lock_guard<std::mutex> lock(g_gpu_mutex);
    if (!g_gpu_init_attempted) {
        g_gpu_init_attempted = true;
        g_gpu_init_result = ncnn::create_gpu_instance();
        if (g_gpu_init_result != 0) {
            __android_log_print(ANDROID_LOG_WARN, kTag, "ncnn create_gpu_instance failed: %d", g_gpu_init_result);
        }
    }
    return g_gpu_init_result == 0 && ncnn::get_gpu_count() > 0;
}
#endif

int configureAndLoad(Runtime& runtime,
                     const char* param_path,
                     const char* model_path,
                     bool use_vulkan,
                     int gpu_index) {
    runtime.net.clear();
    runtime.net.opt.use_vulkan_compute = false;
    runtime.net.opt.use_fp16_storage = false;
    runtime.net.opt.use_fp16_packed = false;
    runtime.net.opt.use_fp16_arithmetic = false;
    runtime.net.opt.use_packing_layout = false;
    const unsigned int cpu_count = std::thread::hardware_concurrency();
    runtime.net.opt.num_threads = static_cast<int>(std::max(1u, std::min(cpu_count == 0 ? 4u : cpu_count, 4u)));

#if NCNN_VULKAN
    if (use_vulkan) {
        if (!ensureGpuInstance()) return -20;
        const int gpu_count = ncnn::get_gpu_count();
        if (gpu_index < 0 || gpu_index >= gpu_count) return -21;
        runtime.net.set_vulkan_device(gpu_index);
        runtime.net.opt.use_vulkan_compute = true;
        runtime.net.opt.use_fp16_storage = true;
        runtime.net.opt.use_fp16_packed = true;
        runtime.net.opt.use_fp16_arithmetic = true;
    }
#else
    if (use_vulkan) return -22;
#endif

    const int param_result = runtime.net.load_param(param_path);
    if (param_result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "load_param failed: %d", param_result);
        return -30;
    }
    const int model_result = runtime.net.load_model(model_path);
    if (model_result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "load_model failed: %d", model_result);
        runtime.net.clear();
        return -31;
    }
    runtime.backend = use_vulkan ? "Vulkan" : "CPU";
    runtime.ready = true;
    return 0;
}

float readOutputValue(const ncnn::Mat& output, int channel, int x, int y) {
    const size_t index = static_cast<size_t>(y) * output.w + x;
    const void* plane = output.channel(channel);
    if (output.elemsize == sizeof(unsigned short)) {
        return ncnn::float16_to_float32(static_cast<const unsigned short*>(plane)[index]);
    }
    return static_cast<const float*>(plane)[index];
}

jstring toJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_app_infinity_mpvz_domain_restoration_DrunetNcnnNative_nativeCapabilities(JNIEnv*, jobject) {
    jint caps = kCapabilityCpu;
#if NCNN_VULKAN
    if (ensureGpuInstance()) {
        const int count = ncnn::get_gpu_count();
        if (count > 0) {
            caps |= kCapabilityVulkan;
            ncnn::VulkanDevice* device = ncnn::get_gpu_device(ncnn::get_default_gpu_index());
#if __ANDROID_API__ >= 26
            if (device && device->info.support_VK_ANDROID_external_memory_android_hardware_buffer()) {
                caps |= kCapabilityAhbImport;
            }
#endif
        }
    }
#endif
    return caps;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_infinity_mpvz_domain_restoration_DrunetNcnnNative_nativeGpuDeviceName(JNIEnv* env, jobject, jint gpu_index) {
#if NCNN_VULKAN
    if (ensureGpuInstance() && gpu_index >= 0 && gpu_index < ncnn::get_gpu_count()) {
        return toJavaString(env, ncnn::get_gpu_info(gpu_index).device_name());
    }
#else
    (void)gpu_index;
#endif
    return toJavaString(env, "Unavailable");
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_infinity_mpvz_domain_restoration_DrunetNcnnNative_nativeCreate(
        JNIEnv* env, jobject, jstring param_path, jstring model_path,
        jstring input_blob, jstring output_blob, jboolean prefer_vulkan,
        jboolean allow_cpu_fallback, jint gpu_index) {
    if (!param_path || !model_path || !input_blob || !output_blob) return 0;
    const char* param_chars = env->GetStringUTFChars(param_path, nullptr);
    const char* model_chars = env->GetStringUTFChars(model_path, nullptr);
    const char* input_chars = env->GetStringUTFChars(input_blob, nullptr);
    const char* output_chars = env->GetStringUTFChars(output_blob, nullptr);
    if (!param_chars || !model_chars || !input_chars || !output_chars) {
        if (param_chars) env->ReleaseStringUTFChars(param_path, param_chars);
        if (model_chars) env->ReleaseStringUTFChars(model_path, model_chars);
        if (input_chars) env->ReleaseStringUTFChars(input_blob, input_chars);
        if (output_chars) env->ReleaseStringUTFChars(output_blob, output_chars);
        return 0;
    }

    Runtime* runtime = new (std::nothrow) Runtime();
    int load_result = runtime ? -1 : -2;
    if (runtime) {
        runtime->input_blob = input_chars;
        runtime->output_blob = output_chars;
        load_result = configureAndLoad(*runtime, param_chars, model_chars, prefer_vulkan == JNI_TRUE, gpu_index);
        if (load_result != 0 && prefer_vulkan == JNI_TRUE && allow_cpu_fallback == JNI_TRUE) {
            __android_log_print(ANDROID_LOG_WARN, kTag, "Vulkan model load failed (%d); retrying on CPU", load_result);
            load_result = configureAndLoad(*runtime, param_chars, model_chars, false, gpu_index);
        }
        if (load_result != 0) {
            delete runtime;
            runtime = nullptr;
        }
    }

    env->ReleaseStringUTFChars(param_path, param_chars);
    env->ReleaseStringUTFChars(model_path, model_chars);
    env->ReleaseStringUTFChars(input_blob, input_chars);
    env->ReleaseStringUTFChars(output_blob, output_chars);

    if (!runtime) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "ncnn model initialization failed: %d", load_result);
        return 0;
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "DRUNet initialized on %s", runtime->backend.c_str());
    return reinterpret_cast<jlong>(runtime);
}

extern "C" JNIEXPORT void JNICALL
Java_app_infinity_mpvz_domain_restoration_DrunetNcnnNative_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    Runtime* runtime = reinterpret_cast<Runtime*>(handle);
    delete runtime;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_infinity_mpvz_domain_restoration_DrunetNcnnNative_nativeBackendName(JNIEnv* env, jobject, jlong handle) {
    Runtime* runtime = reinterpret_cast<Runtime*>(handle);
    return toJavaString(env, runtime && runtime->ready ? runtime->backend : "Unavailable");
}

/**
 * One-frame staging API for correctness/latency validation.
 * Input/output are direct RGBA8 SDR buffers; the model consumes normalized RGB FP16.
 * This is intentionally not connected to a playback Surface yet: a GPU readback path must
 * first be benchmarked and replaced by AHardwareBuffer import/export where supported.
 * Returns elapsed microseconds, or a negative error code.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_app_infinity_mpvz_domain_restoration_DrunetNcnnNative_nativeProcessRgba(
        JNIEnv* env, jobject, jlong handle, jobject input_buffer, jobject output_buffer,
        jint width, jint height, jfloat strength) {
    Runtime* runtime = reinterpret_cast<Runtime*>(handle);
    if (!runtime || !runtime->ready || !input_buffer || !output_buffer) return -1;
    if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return -2;
    if ((strength < 0.f) || (strength > 1.f)) return -3;
    const size_t required = static_cast<size_t>(width) * static_cast<size_t>(height) * 4u;
    if (required / 4u / static_cast<size_t>(width) != static_cast<size_t>(height)) return -4;
    auto* input_rgba = static_cast<const uint8_t*>(env->GetDirectBufferAddress(input_buffer));
    auto* output_rgba = static_cast<uint8_t*>(env->GetDirectBufferAddress(output_buffer));
    const jlong input_capacity = env->GetDirectBufferCapacity(input_buffer);
    const jlong output_capacity = env->GetDirectBufferCapacity(output_buffer);
    if (!input_rgba || !output_rgba || input_capacity < static_cast<jlong>(required) ||
        output_capacity < static_cast<jlong>(required)) return -5;

    const auto started = std::chrono::steady_clock::now();
    const int padded_width = (width + 7) & ~7;
    const int padded_height = (height + 7) & ~7;
    ncnn::Mat input(padded_width, padded_height, 3, sizeof(unsigned short));
    if (input.empty()) return -6;

    for (int c = 0; c < 3; ++c) {
        auto* plane = static_cast<unsigned short*>(input.channel(c));
        for (int y = 0; y < padded_height; ++y) {
            const int source_y = std::min(y, height - 1);
            for (int x = 0; x < padded_width; ++x) {
                const int source_x = std::min(x, width - 1);
                const size_t offset = (static_cast<size_t>(source_y) * width + source_x) * 4u;
                const float normalized = static_cast<float>(input_rgba[offset + static_cast<size_t>(c)]) / 255.f;
                plane[static_cast<size_t>(y) * padded_width + x] = ncnn::float32_to_float16(normalized);
            }
        }
    }

    ncnn::Extractor extractor = runtime->net.create_extractor();
    extractor.set_light_mode(true);
    if (extractor.input(runtime->input_blob.c_str(), input) != 0) return -7;
    ncnn::Mat restored;
    if (extractor.extract(runtime->output_blob.c_str(), restored) != 0) return -8;
    if (restored.empty() || restored.w != padded_width || restored.h != padded_height || restored.c != 3 ||
        (restored.elemsize != sizeof(unsigned short) && restored.elemsize != sizeof(float))) return -9;

    const float amount = strength;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const size_t rgba_offset = (static_cast<size_t>(y) * width + x) * 4u;
            for (int c = 0; c < 3; ++c) {
                const float original = static_cast<float>(input_rgba[rgba_offset + static_cast<size_t>(c)]) / 255.f;
                const float prediction = readOutputValue(restored, c, x, y);
                const float blended = std::clamp(original + amount * (prediction - original), 0.f, 1.f);
                output_rgba[rgba_offset + static_cast<size_t>(c)] = static_cast<uint8_t>(std::lround(blended * 255.f));
            }
            output_rgba[rgba_offset + 3u] = input_rgba[rgba_offset + 3u];
        }
    }

    const auto finished = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::microseconds>(finished - started).count();
}

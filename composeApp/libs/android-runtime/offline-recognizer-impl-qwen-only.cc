// Minimal offline recognizer dispatcher used by VRCMC's Android ARM64 build.
// Only Qwen3-ASR is retained to avoid linking unrelated ASR runtimes.

#include "sherpa-onnx/csrc/offline-recognizer-impl.h"

#include <memory>
#include <string>

#if __ANDROID_API__ >= 9
#include "android/asset_manager.h"
#include "android/asset_manager_jni.h"
#endif

#include "sherpa-onnx/csrc/macros.h"
#include "sherpa-onnx/csrc/offline-recognizer-qwen3-asr-impl.h"
#include "sherpa-onnx/csrc/text-utils.h"

namespace sherpa_onnx {

OfflineRecognizerImpl::OfflineRecognizerImpl(
    const OfflineRecognizerConfig &config)
    : config_(config) {}

template <typename Manager>
OfflineRecognizerImpl::OfflineRecognizerImpl(
    Manager *, const OfflineRecognizerConfig &config)
    : config_(config) {}

std::unique_ptr<OfflineRecognizerImpl> OfflineRecognizerImpl::Create(
    const OfflineRecognizerConfig &config) {
  if (!config.model_config.qwen3_asr.conv_frontend.empty()) {
    return std::make_unique<OfflineRecognizerQwen3ASRImpl>(config);
  }
  SHERPA_ONNX_LOGE("This narrowed runtime supports only Qwen3-ASR");
  SHERPA_ONNX_EXIT(-1);
  return nullptr;
}

template <typename Manager>
std::unique_ptr<OfflineRecognizerImpl> OfflineRecognizerImpl::Create(
    Manager *mgr, const OfflineRecognizerConfig &config) {
  if (!config.model_config.qwen3_asr.conv_frontend.empty()) {
    return std::make_unique<OfflineRecognizerQwen3ASRImpl>(mgr, config);
  }
  SHERPA_ONNX_LOGE("This narrowed runtime supports only Qwen3-ASR");
  SHERPA_ONNX_EXIT(-1);
  return nullptr;
}

void OfflineRecognizerImpl::SetConfig(const OfflineRecognizerConfig &config) {
  config_ = config;
}

std::string OfflineRecognizerImpl::ApplyInverseTextNormalization(
    std::string text) const {
  return RemoveInvalidUtf8Sequences(text);
}

std::string OfflineRecognizerImpl::ApplyHomophoneReplacer(
    std::string text) const {
  return text;
}

#if __ANDROID_API__ >= 9
template OfflineRecognizerImpl::OfflineRecognizerImpl(
    AAssetManager *, const OfflineRecognizerConfig &);
template std::unique_ptr<OfflineRecognizerImpl> OfflineRecognizerImpl::Create(
    AAssetManager *, const OfflineRecognizerConfig &);
#endif

}  // namespace sherpa_onnx

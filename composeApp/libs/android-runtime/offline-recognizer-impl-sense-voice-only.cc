// Minimal offline recognizer dispatcher used by VRCMC's Android ARM64 build.
// Only SenseVoice is retained to avoid linking unrelated ASR runtimes.

#include "sherpa-onnx/csrc/offline-recognizer-impl.h"

#include <memory>
#include <string>

#if __ANDROID_API__ >= 9
#include "android/asset_manager.h"
#include "android/asset_manager_jni.h"
#endif

#include "sherpa-onnx/csrc/macros.h"
#include "sherpa-onnx/csrc/offline-recognizer-sense-voice-impl.h"
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
  if (!config.model_config.sense_voice.model.empty()) {
    return std::make_unique<OfflineRecognizerSenseVoiceImpl>(config);
  }
  SHERPA_ONNX_LOGE("This narrowed runtime supports only SenseVoice");
  SHERPA_ONNX_EXIT(-1);
  return nullptr;
}

template <typename Manager>
std::unique_ptr<OfflineRecognizerImpl> OfflineRecognizerImpl::Create(
    Manager *mgr, const OfflineRecognizerConfig &config) {
  if (!config.model_config.sense_voice.model.empty()) {
    return std::make_unique<OfflineRecognizerSenseVoiceImpl>(mgr, config);
  }
  SHERPA_ONNX_LOGE("This narrowed runtime supports only SenseVoice");
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

// JNI bridge between the Lume Translate add-on and bergamot-translator (MPL-2.0).
// Text crosses the boundary as UTF-8 byte arrays: JNI's own string functions use "modified
// UTF-8", which mangles characters outside the BMP (emoji in chapter text).
#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/translation_model.h"

using marian::bergamot::BlockingService;
using marian::bergamot::Response;
using marian::bergamot::ResponseOptions;
using marian::bergamot::TranslationModel;

namespace {

BlockingService &service() {
  static BlockingService instance{BlockingService::Config{}};
  return instance;
}

std::string bytes(JNIEnv *env, jbyteArray array) {
  jsize n = env->GetArrayLength(array);
  std::string out(static_cast<size_t>(n), '\0');
  env->GetByteArrayRegion(array, 0, n, reinterpret_cast<jbyte *>(out.data()));
  return out;
}

jbyteArray toBytes(JNIEnv *env, const std::string &s) {
  jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
  env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
  return out;
}

void throwJava(JNIEnv *env, const char *message) {
  jclass cls = env->FindClass("java/lang/IllegalStateException");
  env->ThrowNew(cls, message);
}

struct Handle {
  std::shared_ptr<TranslationModel> model;
};

}  // namespace

extern "C" JNIEXPORT jlong JNICALL Java_com_lume_translate_addon_BergamotNative_load(JNIEnv *env, jobject,
                                                                                    jbyteArray config) {
  try {
    auto options = marian::bergamot::parseOptionsFromString(bytes(env, config), /*validate=*/false);
    auto *handle = new Handle{std::make_shared<TranslationModel>(options)};
    return reinterpret_cast<jlong>(handle);
  } catch (const std::exception &e) {
    throwJava(env, e.what());
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL Java_com_lume_translate_addon_BergamotNative_free(JNIEnv *, jobject, jlong ptr) {
  delete reinterpret_cast<Handle *>(ptr);
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_com_lume_translate_addon_BergamotNative_translate(
    JNIEnv *env, jobject, jlong ptr, jobjectArray texts, jboolean html) {
  auto *handle = reinterpret_cast<Handle *>(ptr);
  jsize count = env->GetArrayLength(texts);
  std::vector<std::string> source;
  source.reserve(static_cast<size_t>(count));
  for (jsize i = 0; i < count; ++i) {
    auto item = static_cast<jbyteArray>(env->GetObjectArrayElement(texts, i));
    source.push_back(bytes(env, item));
    env->DeleteLocalRef(item);
  }
  ResponseOptions options;
  options.HTML = html == JNI_TRUE;
  options.alignment = options.HTML;  // Moving tags onto the translation needs word alignments.
  std::vector<ResponseOptions> perText(source.size(), options);
  try {
    std::vector<Response> responses = service().translateMultiple(handle->model, std::move(source), perText);
    jobjectArray out = env->NewObjectArray(count, env->FindClass("[B"), nullptr);
    for (jsize i = 0; i < count; ++i) {
      jbyteArray b = toBytes(env, responses[static_cast<size_t>(i)].getTranslatedText());
      env->SetObjectArrayElement(out, i, b);
      env->DeleteLocalRef(b);
    }
    return out;
  } catch (const std::exception &e) {
    throwJava(env, e.what());
    return nullptr;
  }
}

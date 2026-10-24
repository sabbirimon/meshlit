#include <napi/native_api.h>

// A real native boundary; never advertise a model engine merely because N-API loaded.
static napi_value Capabilities(napi_env env, napi_callback_info) {
    napi_value result, available, detail;
    if (napi_create_object(env, &result) != napi_ok ||
        napi_get_boolean(env, false, &available) != napi_ok ||
        napi_create_string_utf8(env, "Local LLM runtime not linked or qualified on HarmonyOS NEXT", NAPI_AUTO_LENGTH, &detail) != napi_ok ||
        napi_set_named_property(env, result, "localInference", available) != napi_ok ||
        napi_set_named_property(env, result, "detail", detail) != napi_ok) {
        napi_throw_error(env, "MESHLIT_CAPABILITY_FAILURE", "Cannot read native runtime capability");
        return nullptr;
    }
    return result;
}
static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor descriptor = {"capabilities", nullptr, Capabilities, nullptr, nullptr, nullptr, napi_default, nullptr};
    if (napi_define_properties(env, exports, 1, &descriptor) != napi_ok) {
        napi_throw_error(env, "MESHLIT_CAPABILITY_FAILURE", "Cannot register native runtime capability");
        return nullptr;
    }
    return exports;
}
static napi_module module = {1, 0, nullptr, Init, "meshlit_runtime", nullptr, {nullptr}};
extern "C" __attribute__((constructor)) void RegisterMeshlitNextRuntime() { napi_module_register(&module); }

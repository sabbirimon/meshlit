cmake_minimum_required(VERSION 3.22.1)
project(meshlit_sd LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Phase 4.x — Real on-device Stable Diffusion MVP.
# This CMakeLists builds a JNI shim that exposes the entry points the
# Kotlin SdCppEngine calls. The body is a stub for MVP1: it links,
# every entry point returns a typed "stub" failure, and the Kotlin
# wrapper translates that into a MeshlitResult.Failure so the UI can
# distinguish "library linked but real sd.cpp not bundled" from
# "library not linked at all".
#
# Phase 2 will add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/external/sd.cpp)
# + add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/external/ggml) here and
# replace the body of each native function with a real sd_ctx_t call.
add_library(meshlit_sd SHARED
    meshlit_sd.cpp
)

# Expose only the JNI entry points; everything else is hidden.
target_compile_options(meshlit_sd PRIVATE
    -fvisibility=hidden
    -fno-rtti
    -fno-exceptions
    -O2
    -DNDEBUG
    -Wall
    -Wno-unused-parameter
)

find_library(log-lib log)
target_link_libraries(meshlit_sd ${log-lib})

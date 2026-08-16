cmake_minimum_required(VERSION 3.22.1)
project(vt_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(vt_native SHARED
    vt_parser.cpp
    vt_dispatch.cpp
)

# Expose only the JNI entry points; everything else is hidden.
target_compile_options(vt_native PRIVATE
    -fvisibility=hidden
    -fno-rtti
    -fno-exceptions
    -O3
    -DNDEBUG
)

find_library(log-lib log)
target_link_libraries(vt_native ${log-lib})
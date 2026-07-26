# Native module for the on-device GGUF assistant (llama.cpp) — see local/LocalLlm.kt.
#
# llama.cpp is fetched at configure time and pinned to a fixed upstream release so builds are
# reproducible. The pin (b9888) is the exact llama.cpp version the attached ollama source tree
# validates against (ollama-main/LLAMA_CPP_VERSION), i.e. a combination known to be stable.
#
# The CPU backend is always built: it works on every device, needs no driver, and its worst case is
# "slow", never "crash". The Vulkan GPU backend is ALSO built by default (LUCENT_ENABLE_VULKAN=ON),
# so the in-app CPU/GPU switch can really offload to the GPU; a device whose driver can't handle it
# falls back to CPU in LocalLlm rather than crashing. Pass -DLUCENT_ENABLE_VULKAN=OFF (Gradle
# -PcpuOnly) for a smaller CPU-only APK. All heavy work runs on a background thread; the UI never
# blocks on this code.
cmake_minimum_required(VERSION 3.22.1)
project(lucent_llama C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# ---- llama.cpp, pinned ----
set(LLAMA_CPP_TAG "b9888")

# If CI (or a developer working offline) has already cloned llama.cpp at the pinned tag into
# llama.cpp-src/ beside this file, use that checkout instead of hitting the network at configure
# time. FetchContent honours FETCHCONTENT_SOURCE_DIR_<NAME>; setting it only when the directory
# actually exists means local builds without the pre-seed keep working exactly as before. This is
# what removes the one network fetch from the middle of the Gradle build — the workflow's
# "Prefetch llama.cpp" step does the clone up front, with retries.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp-src/CMakeLists.txt")
    set(FETCHCONTENT_SOURCE_DIR_LLAMA_CPP "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp-src" CACHE PATH "" FORCE)
    message(STATUS "lucent: using pre-seeded llama.cpp at ${FETCHCONTENT_SOURCE_DIR_LLAMA_CPP}")
endif()

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG        ${LLAMA_CPP_TAG}
    GIT_SHALLOW    TRUE
)

# Trim llama.cpp down to the library itself. No tools, no tests, no server, no libcurl —
# the app streams tokens through JNI, nothing else is wanted.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# CPU is always built (works on every device, no driver, worst case "slow" not "crash").
#
# GPU (Vulkan) is OPT-IN and OFF by default, deliberately: on Android, Vulkan offload is the single
# biggest source of device-specific crashes, and a non-technical maintainer can't debug a CI that
# the biggest source of device-specific crashes, so it is OPT-IN at runtime: the in-app CPU/GPU
# switch (SettingsScreen / LocalLlm) defaults to CPU and warns before turning GPU on. But the GPU
# BACKEND is compiled in by default here, so choosing GPU actually offloads to the GPU. If a device's
# Vulkan driver can't handle it, LocalLlm transparently falls back to CPU (it never crashes the app).
#
# To build a smaller, CPU-only APK instead (e.g. if the Vulkan build ever breaks CI), pass
# -DLUCENT_ENABLE_VULKAN=OFF (the Gradle flag is -PcpuOnly).
option(LUCENT_ENABLE_VULKAN "Compile the Vulkan GPU backend for the on-device model" ON)

set(GGML_NATIVE  OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL  OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP  OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)

# llama.cpp's llamafile sgemm kernels use ARMv8.2 FP16 NEON intrinsics (vld1q_f16, vld1_f16, ...)
# that 32-bit armeabi-v7a doesn't have — sgemm.cpp won't compile at -march=armv7-a. Turn the
# llamafile path OFF for v7a only; ggml falls back to its portable matmul there (slower, but 32-bit
# devices are the slow case regardless). arm64-v8a keeps llamafile. (The default GPU build is
# arm64-only anyway; this keeps the -PcpuOnly build, which re-adds v7a, compiling.)
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
endif()
if (LUCENT_ENABLE_VULKAN)
    message(STATUS "lucent: Vulkan GPU backend ENABLED")
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)

    # ggml's Vulkan backend compiles its GLSL shaders to SPIR-V at build time with glslc, run on the
    # BUILD machine (a host tool). Cross-compiling to Android, CMake's FindVulkan often can't locate a
    # host glslc — so point it straight at the one bundled with the NDK we're already using. That
    # needs no download and is always the right version. If it isn't where we expect, we leave
    # Vulkan_GLSLC_EXECUTABLE unset and let CMake find glslc on PATH (the CI provides one as a
    # fallback). ANDROID_NDK and ANDROID_HOST_TAG are set by the NDK's CMake toolchain.
    if (NOT Vulkan_GLSLC_EXECUTABLE AND DEFINED ANDROID_NDK)
        set(_lucent_host_tag "${ANDROID_HOST_TAG}")
        if (NOT _lucent_host_tag)
            set(_lucent_host_tag "linux-x86_64")
        endif()
        set(_lucent_glslc "${ANDROID_NDK}/shader-tools/${_lucent_host_tag}/glslc")
        if (EXISTS "${_lucent_glslc}")
            message(STATUS "lucent: using NDK glslc at ${_lucent_glslc}")
            set(Vulkan_GLSLC_EXECUTABLE "${_lucent_glslc}" CACHE FILEPATH "glslc for shader compilation" FORCE)
        endif()
    endif()
else()
    message(STATUS "lucent: Vulkan GPU backend DISABLED (CPU-only build)")
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

# armv8 devices without dotprod exist; let ggml's runtime CPU detection handle features rather
# than baking in an -march that faults on older cores (the "must not crash on old devices" rule).
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
endif()

FetchContent_MakeAvailable(llama_cpp)

add_library(lucent_llama SHARED lucent_llama.cpp)

find_library(log-lib log)

target_link_libraries(lucent_llama
    llama
    ${log-lib})

# ---- PHASE 4: multimodal (llama.cpp's official mtmd) ----
# The mtmd library lives in the llama.cpp tree at tools/mtmd but is only built as a target when
# LLAMA_BUILD_TOOLS is ON — which would drag every CLI tool into this build. So its sources are
# compiled straight into lucent_llama instead: they depend only on ggml + llama (already linked)
# and the vendored stb/miniaudio headers. Guarded by an existence check so a future llama.cpp pin
# that moves these files degrades to a text-only engine WITH A WARNING instead of a broken build —
# multimodal must never be the reason the engine fails to exist.
set(LUCENT_MTMD_DIR ${llama_cpp_SOURCE_DIR}/tools/mtmd)
if (EXISTS ${LUCENT_MTMD_DIR}/mtmd.cpp)
    # Second CI pass (Android job 2026-07-26): the hand-picked four-file list above COMPILED but
    # the LINKER answered with two dozen missing vtables (clip_graph_*, mtmd_image_preprocessor_*):
    # b9888 splits the per-model clip graphs and image preprocessors across many more translation
    # units than older revisions. So: stop hand-listing sources at all. Take every .cpp in the mtmd
    # tree except executables/tests — a future upstream re-split then lands here as "more files in
    # the glob", not as another red run.
    #
    # Filter notes: "-cli.cpp" (not a bare "cli" substring — clip.cpp CONTAINS "cli" and must
    # survive), standalone main.cpp, and any test/example/bench trees.
    file(GLOB_RECURSE LUCENT_MTMD_SOURCES CONFIGURE_DEPENDS ${LUCENT_MTMD_DIR}/*.cpp)
    list(FILTER LUCENT_MTMD_SOURCES EXCLUDE REGEX "-cli\.cpp$")
    list(FILTER LUCENT_MTMD_SOURCES EXCLUDE REGEX "/main\.cpp$")
    list(FILTER LUCENT_MTMD_SOURCES EXCLUDE REGEX "/(tests?|examples?|bench|deprecated|debug)/")
    # Third CI pass (Android job 2026-07-26): the glob above worked — every models/*.cpp came in
    # and all the missing vtables resolved — and then the linker met `duplicate symbol: main`,
    # courtesy of debug/mtmd-debug.cpp and deprecation-warning.cpp: two EXECUTABLE stubs whose
    # names none of the filters anticipated. Name-based filtering keeps losing to upstream's
    # imagination, so the last word now goes to the sources themselves: anything that defines
    # main() is a program, not library code, and is dropped no matter what it is called or where
    # it lives. That ends the whole class, not just these two files.
    set(LUCENT_MTMD_LIB_SOURCES "")
    foreach(LUCENT_MTMD_SRC ${LUCENT_MTMD_SOURCES})
        file(READ ${LUCENT_MTMD_SRC} LUCENT_MTMD_SRC_TEXT)
        if (LUCENT_MTMD_SRC_TEXT MATCHES "int[ \t\r\n]+main[ \t]*\(")
            message(STATUS "lucent: mtmd source excluded (defines main): ${LUCENT_MTMD_SRC}")
        else()
            list(APPEND LUCENT_MTMD_LIB_SOURCES ${LUCENT_MTMD_SRC})
        endif()
    endforeach()
    set(LUCENT_MTMD_SOURCES ${LUCENT_MTMD_LIB_SOURCES})
    unset(LUCENT_MTMD_SRC_TEXT)
    list(LENGTH LUCENT_MTMD_SOURCES LUCENT_MTMD_COUNT)
    message(STATUS "lucent: multimodal (mtmd) compiled in - ${LUCENT_MTMD_COUNT} source(s) under ${LUCENT_MTMD_DIR}")
    target_sources(lucent_llama PRIVATE ${LUCENT_MTMD_SOURCES})
    target_include_directories(lucent_llama PRIVATE
        ${LUCENT_MTMD_DIR}
        ${llama_cpp_SOURCE_DIR}/vendor
        ${llama_cpp_SOURCE_DIR}/common
    )
else()
    target_compile_definitions(lucent_llama PRIVATE LUCENT_NO_MTMD)
    message(WARNING "lucent: tools/mtmd not found in the llama.cpp checkout - building a TEXT-ONLY engine (local multimodal will be unavailable)")
endif()

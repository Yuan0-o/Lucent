# Desktop (Windows) build of the on-device GGUF assistant native module — see local/LocalLlm.kt.
#
# This compiles the SAME C++ source as the Android build (app/src/main/cpp/lucent_llama.cpp) into a
# Windows DLL, lucent_llama.dll. Two things differ from the Android CMakeLists:
#
#   1. There is no NDK, so <jni.h> comes from the JDK via find_package(JNI), and the one Android-only
#      header the source pulls in (<android/log.h>) is satisfied by the tiny shim in shim/, which maps
#      __android_log_print onto vfprintf(stderr).
#   2. Two flavours from one CMakeLists. By default this is the guaranteed, portable CPU-only
#      build that runs on any x86-64 Windows. Passing -DLUCENT_ENABLE_VULKAN=ON additionally
#      compiles llama.cpp's Vulkan GPU backend into the DLL (settings task A4 — Windows GPU
#      support, mirroring the Android build): the workflow builds the CPU DLL first, then makes a
#      best-effort second build with Vulkan on and ships it as lucent_llama_vk.dll. NativeLoader
#      loads the _vk variant only on machines that actually have the Vulkan runtime
#      (vulkan-1.dll), so a machine without it keeps the CPU engine and nothing breaks. The
#      in-app switch stays OFF (CPU) by default with a risk warning, exactly like Android, and a
#      failed GPU load falls back to CPU inside LocalLlm.
#
# The JNI symbols the source exports (Java_com_lucent_app_local_LocalLlm_native*) are unchanged,
# because the desktop copy of LocalLlm keeps the Android package name com.lucent.app.local — so no
# renaming is needed and the same .cpp links here verbatim.
#
# llama.cpp is pinned to the same tag (b9888) and, like the Android build, is used from a pre-seeded
# checkout (llama.cpp-src/ beside this file, cloned up front by the workflow with retries) when one
# is present, so there is no network fetch in the middle of the compile.
cmake_minimum_required(VERSION 3.22)
project(lucent_llama C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Static-link the MSVC runtime so the shipped DLL doesn't depend on a VC++ redistributable being
# installed on the end user's machine.
if (MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

include(FetchContent)

# ---- llama.cpp, pinned (same tag as Android) ----
set(LLAMA_CPP_TAG "b9888")

# Prefer a checkout the workflow already made (llama.cpp-src/ beside this file) over a network fetch,
# exactly like the Android CMakeLists. FETCHCONTENT_SOURCE_DIR_<NAME> makes FetchContent use it.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp-src/CMakeLists.txt")
    set(FETCHCONTENT_SOURCE_DIR_LLAMA_CPP "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp-src" CACHE PATH "" FORCE)
    message(STATUS "lucent(desktop): using pre-seeded llama.cpp at ${FETCHCONTENT_SOURCE_DIR_LLAMA_CPP}")
endif()

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG        ${LLAMA_CPP_TAG}
    GIT_SHALLOW    TRUE
)

# Trim llama.cpp to just the library — no tools, tests, server, examples, or libcurl. The app streams
# tokens through JNI and wants nothing else. Static libs, folded into our one shared DLL.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# Backend selection.
#   GGML_VULKAN      — OFF by default (the portable CPU baseline). The LUCENT_ENABLE_VULKAN option
#                      below turns it on for the second, GPU-enabled engine build; it degrades
#                      itself back to CPU-only with a warning when the Vulkan SDK isn't installed,
#                      so a misconfigured environment can never fail the build outright.
#   GGML_NATIVE OFF  — don't bake in -march=native. NOTE (backlog W-1): this does NOT mean
#                      "lowest common denominator" — ggml then defaults GGML_AVX2/FMA/F16C/BMI2 ON,
#                      producing a fixed x86-64-v3-class binary that requires AVX2. On an older CPU
#                      the DLL loads fine and the FIRST INFERENCE dies with an illegal-instruction
#                      fault. The shipped guard: NativeLoader refuses to load the engine on
#                      non-AVX2 CPUs (probed via lucent_native, which IS plain x86-64) and the app
#                      shows a plain message instead. The proper long-term fix is
#                      -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON (ship several CPU variants,
#                      pick at runtime — what upstream's own Windows releases do), at the cost of
#                      build time and a few MB.
#   GGML_OPENMP OFF  — avoid an external OpenMP runtime dependency in the shipped DLL.
option(LUCENT_ENABLE_VULKAN "Compile the Vulkan GPU backend into the desktop engine" OFF)
if (LUCENT_ENABLE_VULKAN)
    if (DEFINED ENV{VULKAN_SDK})
        set(GGML_VULKAN ON CACHE BOOL "" FORCE)
        message(STATUS "lucent(desktop): Vulkan GPU backend ENABLED (VULKAN_SDK=$ENV{VULKAN_SDK})")
    else()
        set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
        message(WARNING "lucent(desktop): LUCENT_ENABLE_VULKAN=ON but VULKAN_SDK is not set — building CPU-only instead.")
    endif()
else()
    set(GGML_VULKAN  OFF CACHE BOOL "" FORCE)
endif()
set(GGML_OPENCL  OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE  OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP  OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

# ---- JNI headers from the JDK (no NDK on desktop) ----
# find_package(JNI) locates <jni.h> and the platform <jni_md.h> from JAVA_HOME (set by setup-java in
# CI). We only need the headers; nothing is linked against the JVM.
find_package(JNI REQUIRED)

# The shared module the JVM loads. SHARED → lucent_llama.dll on Windows. JNIEXPORT expands to
# __declspec(dllexport) there, and the functions are already extern "C", so the JNI entry points are
# exported with their exact unmangled names.
add_library(lucent_llama SHARED ../../app/src/main/cpp/lucent_llama.cpp)

target_include_directories(lucent_llama PRIVATE
    ${JNI_INCLUDE_DIRS}
    # Satisfies the source's #include <android/log.h> with the desktop shim (stderr logging).
    "${CMAKE_CURRENT_SOURCE_DIR}/shim"
)

target_link_libraries(lucent_llama llama)

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
        # Fourth CI pass (both jobs 2026-07-26): CMake's own regex engine rejected the escaped paren
        # ("Unmatched parentheses" — the \( collapsed to a bare "(" across the string-escape layer).
        # A character class needs no escaping anywhere, so it cannot collapse: [(] is the paren.
        if (LUCENT_MTMD_SRC_TEXT MATCHES "int[ \t\r\n]+main[ \t]*[(]")
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

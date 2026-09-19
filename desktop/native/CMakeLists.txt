cmake_minimum_required(VERSION 3.22)
project(lucent_llama C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

include(FetchContent)

set(LLAMA_CPP_TAG "b9888")

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

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

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

find_package(JNI REQUIRED)

add_library(lucent_llama SHARED ../../app/src/main/cpp/lucent_llama.cpp)

target_include_directories(lucent_llama PRIVATE
    ${JNI_INCLUDE_DIRS}
    "${CMAKE_CURRENT_SOURCE_DIR}/shim"
)

target_link_libraries(lucent_llama llama)

set(LUCENT_MTMD_DIR ${llama_cpp_SOURCE_DIR}/tools/mtmd)
if (EXISTS ${LUCENT_MTMD_DIR}/mtmd.cpp)
    file(GLOB_RECURSE LUCENT_MTMD_SOURCES CONFIGURE_DEPENDS ${LUCENT_MTMD_DIR}/*.cpp)
    list(FILTER LUCENT_MTMD_SOURCES EXCLUDE REGEX "-cli\.cpp$")
    list(FILTER LUCENT_MTMD_SOURCES EXCLUDE REGEX "/main\.cpp$")
    list(FILTER LUCENT_MTMD_SOURCES EXCLUDE REGEX "/(tests?|examples?|bench|deprecated|debug)/")
    set(LUCENT_MTMD_LIB_SOURCES "")
    foreach(LUCENT_MTMD_SRC ${LUCENT_MTMD_SOURCES})
        file(READ ${LUCENT_MTMD_SRC} LUCENT_MTMD_SRC_TEXT)
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

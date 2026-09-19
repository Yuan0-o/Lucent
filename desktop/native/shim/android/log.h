#ifndef LUCENT_DESKTOP_SHIM_ANDROID_LOG_H
#define LUCENT_DESKTOP_SHIM_ANDROID_LOG_H

#include <cstdarg>
#include <cstdio>

typedef enum android_LogPriority {
    ANDROID_LOG_UNKNOWN = 0,
    ANDROID_LOG_DEFAULT,
    ANDROID_LOG_VERBOSE,
    ANDROID_LOG_DEBUG,
    ANDROID_LOG_INFO,
    ANDROID_LOG_WARN,
    ANDROID_LOG_ERROR,
    ANDROID_LOG_FATAL,
    ANDROID_LOG_SILENT,
} android_LogPriority;

static inline int __android_log_print(int , const char* tag, const char* fmt, ...) {
    if (tag != nullptr) {
        std::fprintf(stderr, "[%s] ", tag);
    }
    std::va_list args;
    va_start(args, fmt);
    int written = std::vfprintf(stderr, fmt, args);
    va_end(args);
    std::fputc('\n', stderr);
    return written;
}

#endif

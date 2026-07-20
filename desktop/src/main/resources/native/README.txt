Windows native libraries (lucent_llama.dll, lucent_native.dll) are compiled and copied here by
.github/workflows/build-windows.yml before packaging. They are optional: the app falls back to its
Kotlin/JCE implementations when a DLL is absent (see nativebridge/NativeLoader), so this directory
may be empty in a plain checkout.

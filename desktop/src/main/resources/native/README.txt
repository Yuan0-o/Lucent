Windows native libraries are compiled and copied here by .github/workflows/build-windows.yml
before packaging:

  lucent_llama.dll     - on-device GGUF assistant engine (llama.cpp), CPU baseline. Always built.
  lucent_llama_vk.dll  - the same engine with the Vulkan GPU backend compiled in (task A4).
                         Best-effort: only present when the Vulkan SDK build succeeded. At runtime
                         NativeLoader loads this variant only on machines that actually have the
                         Vulkan runtime (vulkan-1.dll); everywhere else the CPU DLL is used.
  lucent_native.dll    - Rust crypto/animation accelerator.

All of them are optional: the app falls back to its Kotlin/JCE implementations (and the local
model simply stays unavailable / on the CPU) when a DLL is absent — see nativebridge/NativeLoader.
This directory may be empty in a plain checkout.

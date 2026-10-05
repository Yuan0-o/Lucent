package com.lucent.app.local;

import com.lucent.app.local.ILocalLlmCallback;

interface ILocalLlmEngine {

    boolean ensureLoaded(boolean gpuEnabled);

    boolean supportsVision();

    oneway void generate(in String[] roles, in String[] texts, in String[] imagePaths,
                         in ILocalLlmCallback callback);

    oneway void stop();

    oneway void shutdown();
}

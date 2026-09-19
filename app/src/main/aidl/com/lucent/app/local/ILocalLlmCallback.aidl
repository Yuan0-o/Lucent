package com.lucent.app.local;

oneway interface ILocalLlmCallback {

    void onPiece(String piece);

    void onDone(int rc);
}

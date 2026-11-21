package com.lume.translate;

oneway interface ITranslateCallback {
    void onResult(in String[] html);

    void onError(String message);
}

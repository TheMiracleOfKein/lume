package com.lume.translate;

oneway interface IDownloadCallback {
    /** Bytes downloaded so far of the pair's compressed total. */
    void onProgress(long done, long total);

    void onDone();

    void onError(String message);
}

package com.lume.translate;

import com.lume.translate.IDownloadCallback;
import com.lume.translate.ITranslateCallback;

/**
 * The add-on's translation service. Pairs are written "en-ru". Every call returns at once; long
 * work reports through the callback. Keep each translate batch well under the binder limit
 * (Lume sends at most TranslateContract.MAX_BATCH_CHARS characters).
 */
interface ILumeTranslator {
    /** TranslateContract.API_VERSION of the add-on. */
    int apiVersion();

    /** Pairs whose model files are downloaded and checked. */
    String[] installedPairs();

    /** Downloads, unpacks and checks a pair's model files. A second call while running is ignored. */
    void download(String pair, IDownloadCallback callback);

    void cancelDownload(String pair);

    /** Removes a pair's files; true when something was removed. */
    boolean deletePair(String pair);

    /** Translates HTML fragments (paragraphs) in order; the result has the same length. */
    void translate(String pair, in String[] html, ITranslateCallback callback);
}

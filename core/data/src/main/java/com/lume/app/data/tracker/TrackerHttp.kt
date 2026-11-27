package com.lume.app.data.tracker

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

/** The answer text, or null when the status was not successful. The body is read either way. */
internal fun Response.textOrNull(): String? = body.string().takeIf { isSuccessful }

/** Runs [request] and returns the answer text, or null when it was not successful. */
internal fun OkHttpClient.fetchText(request: Request): String? =
    newCall(request).execute().use { it.textOrNull() }

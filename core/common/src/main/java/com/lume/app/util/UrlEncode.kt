package com.lume.app.util

import java.net.URLEncoder

/** Centralized query encoding (String charset — Charset overloads need API 33 / desugaring). */
fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8")

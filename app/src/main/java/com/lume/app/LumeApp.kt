package com.lume.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

class LumeApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        // Per-app language: empty list = follow system (see AppLanguage.SYSTEM).
        runCatching {
            com.lume.app.data.locale.AppLocaleController.applyStoredTag(container.prefs.appLanguage.value)
        }
        container.start()
    }

    override fun newImageLoader(): ImageLoader {
        val http = container.http
        val imageReferers = container.imageReferers
        val client: OkHttpClient = http.okHttp.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                // Same UA as catalogue requests and the challenge WebView (cf_clearance is UA-bound).
                val builder = original.newBuilder()
                    .header("User-Agent", http.userAgent)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                if (original.header("Referer").isNullOrBlank()) {
                    imageReferers.refererFor(original.url.host)?.let { builder.header("Referer", it) }
                }
                chain.proceed(builder.build())
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        /** Only for [com.lume.app.data.locale.appString]; everything else uses [container]. */
        internal lateinit var instance: LumeApp
            private set
    }
}

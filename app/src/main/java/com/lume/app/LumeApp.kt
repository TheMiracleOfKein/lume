package com.lume.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lume.app.data.local.pdf.PdfPageFetcher
import com.lume.app.data.local.pdf.PdfPageKeyer
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class LumeApp : Application(), SingletonImageLoader.Factory {
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

    override fun newImageLoader(context: PlatformContext): ImageLoader {
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
        val app: Context = this
        return ImageLoader.Builder(app)
            .components {
                add(PdfPageKeyer())
                add(PdfPageFetcher.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }
            .crossfade(true)
            .memoryCache { MemoryCache.Builder().maxSizePercent(app, 0.25).build() }
            .diskCache {
                // Coil 3 writes its entries differently: start a new folder and drop Coil 2's.
                cacheDir.resolve(LEGACY_IMAGE_CACHE).takeIf { it.exists() }?.deleteRecursively()
                DiskCache.Builder()
                    .directory(cacheDir.resolve(IMAGE_CACHE).toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        private const val IMAGE_CACHE = "image_cache_v3"
        private const val LEGACY_IMAGE_CACHE = "image_cache"

        /** Only for [com.lume.app.data.locale.appString]; everything else uses [container]. */
        internal lateinit var instance: LumeApp
            private set
    }
}

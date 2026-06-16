package com.jahirtrap.cconnect.data.remote

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.HttpRequestPipeline

private val imageClient = HttpClient(Js).apply {
    requestPipeline.intercept(HttpRequestPipeline.State) {
        if (context.url.buildString().startsWith(Backend.baseUrl)) {
            Backend.authHeaders.forEach { (name, value) -> context.headers.append(name, value) }
        }
    }
}

actual object AppImageLoader {

    private var instance: ImageLoader? = null

    actual fun get(context: PlatformContext): ImageLoader =
        instance ?: build(context).also { instance = it }

    private fun build(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = imageClient))
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}

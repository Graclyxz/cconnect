package com.jahirtrap.cconnect.data.remote

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder

actual object AppImageLoader {

    private var instance: ImageLoader? = null

    actual fun get(context: PlatformContext): ImageLoader =
        instance ?: build(context).also { instance = it }

    private fun build(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}

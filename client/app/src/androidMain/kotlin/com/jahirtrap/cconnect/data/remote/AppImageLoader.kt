package com.jahirtrap.cconnect.data.remote

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

actual object AppImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    actual fun get(context: PlatformContext): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .memoryCache { null }
            .diskCache { null }
            .build()
    }

    private object AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!request.url.toString().startsWith(Backend.baseUrl)) return chain.proceed(request)
            val authed = request.newBuilder().apply {
                Backend.authHeaders.forEach { (name, value) -> header(name, value) }
            }.build()
            return chain.proceed(authed)
        }
    }
}

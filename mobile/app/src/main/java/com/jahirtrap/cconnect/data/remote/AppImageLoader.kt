package com.jahirtrap.cconnect.data.remote

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

object AppImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
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

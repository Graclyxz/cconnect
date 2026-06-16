package com.jahirtrap.cconnect.data.remote

import coil3.ImageLoader
import coil3.PlatformContext

expect object AppImageLoader {
    fun get(context: PlatformContext): ImageLoader
}

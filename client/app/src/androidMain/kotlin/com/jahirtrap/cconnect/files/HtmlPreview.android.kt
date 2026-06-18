package com.jahirtrap.cconnect.files

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.connection_error
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.EmptyState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun HtmlPreview(
    url: String,
    filename: String,
    onOpenExternally: () -> Unit,
    modifier: Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = AuthWebViewClient(
                        onLoaded = { loading = false },
                        onFailed = { failed = true; loading = false },
                    )
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            failed -> EmptyState(stringResource(Res.string.connection_error), Modifier.fillMaxSize())
            loading -> CenteredProgress(Modifier.fillMaxSize())
        }
    }
}

private class AuthWebViewClient(
    private val onLoaded: () -> Unit,
    private val onFailed: () -> Unit,
) : WebViewClient() {
    private val client = OkHttpClient()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val target = request.url.toString()
        if (!target.startsWith(Backend.baseUrl)) return null
        return runCatching {
            val authed = Request.Builder().url(target).apply {
                Backend.authHeaders.forEach { (name, value) -> header(name, value) }
            }.build()
            val response = client.newCall(authed).execute()
            if (!response.isSuccessful) {
                response.close()
                return@runCatching null
            }
            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            WebResourceResponse(
                contentType.substringBefore(';').trim(),
                contentType.substringAfter("charset=", "UTF-8").trim(),
                response.body?.byteStream(),
            )
        }.getOrNull()
    }

    override fun onPageFinished(view: WebView?, url: String?) = onLoaded()

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) onFailed()
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        if (request?.isForMainFrame == true) onFailed()
    }
}

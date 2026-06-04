package com.jahirtrap.cconnect.files

import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jahirtrap.cconnect.data.remote.AppImageLoader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Share2
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.remote.SharedApi
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.MarkdownText
import com.jahirtrap.cconnect.ui.TooltipIconButton
import kotlinx.coroutines.launch

private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")

private val TEXT_APPLICATION_MIMES = setOf(
    "application/json", "application/xml", "application/javascript", "application/typescript",
    "application/x-sh", "application/x-yaml", "application/yaml", "application/toml",
    "application/sql", "application/x-bat",
)
private val TEXT_FALLBACK_EXTENSIONS = setOf(
    "kt", "kts", "gradle", "toml", "ini", "cfg", "conf", "properties", "env", "yml", "yaml",
    "ts", "tsx", "jsx", "rs", "go", "ps1", "diff", "patch", "log", "lock",
)

enum class PreviewKind { Image, Markdown, Text, None }

fun previewKindOf(filename: String): PreviewKind {
    val extension = filename.substringAfterLast('.', "").lowercase()
    if (extension in MARKDOWN_EXTENSIONS) return PreviewKind.Markdown
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return when {
        mime?.startsWith("image/") == true && mime != "image/svg+xml" -> PreviewKind.Image
        mime?.startsWith("text/") == true -> PreviewKind.Text
        mime in TEXT_APPLICATION_MIMES -> PreviewKind.Text
        extension in TEXT_FALLBACK_EXTENSIONS -> PreviewKind.Text
        else -> PreviewKind.None
    }
}

fun isPreviewable(filename: String): Boolean = previewKindOf(filename) != PreviewKind.None

@Composable
fun FilePreviewScreen(url: String, filename: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(url) { mutableStateOf<String?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val pending = pendingSaveUrl
        if (uri != null && pending != null) scope.launch { FileTransfer.saveTo(context, pending, uri) }
        pendingSaveUrl = null
    }

    val kind = previewKindOf(filename)
    LaunchedEffect(url) {
        if (kind == PreviewKind.Image) return@LaunchedEffect  // Coil streams the image itself
        val result = SharedApi.fetchText(url)
        if (result != null) text = result else failed = true
    }
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.view),
                subtitle = filename,
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        TooltipIconButton(label = stringResource(R.string.files), onClick = { menu = true }) {
                            Icon(Lucide.EllipsisVertical, contentDescription = null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            CompactDropdownItem(
                                text = stringResource(R.string.save),
                                leadingIcon = { Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; FileTransfer.enqueueToDownloads(context, url, filename) },
                            )
                            CompactDropdownItem(
                                text = stringResource(R.string.save_as),
                                leadingIcon = { Icon(Lucide.Save, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; pendingSaveUrl = url; saveLauncher.launch(filename) },
                            )
                            CompactDropdownItem(
                                text = stringResource(R.string.share),
                                leadingIcon = { Icon(Lucide.Share2, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        FileTransfer.shareIntent(context, url, filename)?.let {
                                            context.startActivity(android.content.Intent.createChooser(it, null))
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            kind == PreviewKind.Image -> ImagePreview(url, Modifier.fillMaxSize().padding(padding))
            failed -> EmptyState(stringResource(R.string.connection_error), Modifier.fillMaxSize().padding(padding))
            text == null -> CenteredProgress(Modifier.fillMaxSize().padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (kind == PreviewKind.Markdown) {
                    MarkdownText(text.orEmpty(), modifier = Modifier.fillMaxWidth())
                } else {
                    SelectionContainer {
                        Text(
                            text.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            imageLoader = AppImageLoader.get(context),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { imageState = it },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        when (imageState) {
            is AsyncImagePainter.State.Error -> EmptyState(stringResource(R.string.connection_error), Modifier.fillMaxSize())
            is AsyncImagePainter.State.Success -> Unit
            else -> CenteredProgress(Modifier.fillMaxSize())
        }
    }
}

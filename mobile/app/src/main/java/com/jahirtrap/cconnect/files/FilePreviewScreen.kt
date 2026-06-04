package com.jahirtrap.cconnect.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
private val TEXT_EXTENSIONS = MARKDOWN_EXTENSIONS + setOf(
    "txt", "log", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env",
    "csv", "html", "css", "js", "ts", "jsx", "tsx", "py", "kt", "kts", "java", "c", "cpp", "h",
    "sh", "bat", "ps1", "sql", "rs", "go", "rb", "php", "gradle", "diff", "patch",
)

fun isPreviewable(filename: String): Boolean =
    filename.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

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

    LaunchedEffect(url) {
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
            failed -> EmptyState(stringResource(R.string.connection_error), Modifier.fillMaxSize().padding(padding))
            text == null -> CenteredProgress(Modifier.fillMaxSize().padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                val markdown = filename.substringAfterLast('.', "").lowercase() in MARKDOWN_EXTENSIONS
                if (markdown) {
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

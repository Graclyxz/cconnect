package com.jahirtrap.cconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.jahirtrap.cconnect.ui.MarkdownText
import com.jahirtrap.cconnect.ui.theme.CConnectTheme
import com.jahirtrap.cconnect.ui.theme.ThemeMode
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        CConnectTheme(themeMode = ThemeMode.SYSTEM, dynamicColor = false) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text("CConnect Web — vista previa", style = MaterialTheme.typography.titleLarge)
                    MarkdownText(PREVIEW_MARKDOWN)
                }
            }
        }
    }
}

private const val PREVIEW_MARKDOWN = """
# Migración web en curso

Esto se renderiza en **wasmJs** con el mismo tema y componentes que desktop.

- Tema y colores compartidos
- Markdown propio de web (*primer corte*)
- `código en línea` y bloques:

```kotlin
fun hola() = "desde el navegador"
```

> Las pantallas reales se montan en la siguiente fase.

Un [enlace de ejemplo](https://example.com).
"""

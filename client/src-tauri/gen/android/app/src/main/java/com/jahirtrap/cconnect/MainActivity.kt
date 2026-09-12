package com.jahirtrap.cconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.view.ActionMode
import android.view.MotionEvent
import android.view.View
import androidx.core.content.IntentCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.json.JSONObject

private const val SHARE_ATTEMPTS = 20
private const val SHARE_RETRY_MS = 400L

class MainActivity : TauriActivity() {
  override val handleBackNavigation = false

  private lateinit var downloads: Downloads
  private val installer by lazy { Installer(this) }
  private val dictation by lazy { Dictation(this) { content } }
  private var pendingSave: Triple<String, String, String>? = null
  private var content: WebView? = null
  private var pendingShare: String? = null
  private var shareAttempts = 0
  private var selecting = false
  @Volatile private var selectableTarget = false

  private val backCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      val view = content
      if (view == null) {
        leave()
        return
      }
      view.evaluateJavascript("window.__cconnectBack ? window.__cconnectBack() : false") { handled ->
        if (handled != "true") leave()
      }
    }
  }

  private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
    val pending = pendingSave ?: return@registerForActivityResult
    pendingSave = null
    if (uri != null) downloads.writeUri(pending.first, uri, pending.third)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    downloads = Downloads(this) { url, filename, headers ->
      pendingSave = Triple(url, filename, headers)
      createDocument.launch(filename)
    }
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, backCallback)
    consumeImeInset()
    takeShare(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    takeShare(intent)
    deliverShare()
  }

  private fun takeShare(intent: Intent?) {
    val uris = when (intent?.action) {
      Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
      Intent.ACTION_SEND_MULTIPLE ->
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
      else -> return
    }
    if (uris.isEmpty()) return
    val json = PastedContent.encode(this, uris)
    if (json == "[]") return
    pendingShare = json
    shareAttempts = 0
  }

  /** The web view only takes it once the chat registered its hook, which lags a cold start. */
  private fun deliverShare() {
    val json = pendingShare ?: return
    val view = content ?: return
    if (shareAttempts++ > SHARE_ATTEMPTS) return
    view.postDelayed({
      view.evaluateJavascript(
        "window.__cconnectPaste ? (window.__cconnectPaste(${JSONObject.quote(json)}), true) : false",
      ) { accepted ->
        if (accepted == "true") pendingShare = null else deliverShare()
      }
    }, SHARE_RETRY_MS)
  }

  private fun consumeImeInset() {
    val root = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
      view.updatePadding(bottom = (ime - navigation).coerceAtLeast(0))
      WindowInsetsCompat.Builder(insets)
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
        .build()
    }
  }

  override fun onResume() {
    super.onResume()
    content?.evaluateJavascript("window.__cconnectResume && window.__cconnectResume()", null)
  }

  /** The GPU path paints composited layers black while text is selected, and the selection
   *  starts on the long press, before any action mode exists. Only presses the page reports
   *  as selectable arm it, so long-pressing a list row does not repaint the whole view. */
  private fun renderSoftwareWhileSelecting(webView: WebView) {
    webView.setOnLongClickListener { view ->
      if (selectableTarget) view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
      false
    }
    webView.setOnTouchListener { view, event ->
      if (event.actionMasked == MotionEvent.ACTION_DOWN && !selecting &&
        view.layerType != View.LAYER_TYPE_NONE
      ) {
        view.setLayerType(View.LAYER_TYPE_NONE, null)
      }
      false
    }
  }

  inner class Selection {
    @JavascriptInterface
    fun setSelectable(value: Boolean) {
      selectableTarget = value
    }
  }

  override fun onActionModeStarted(mode: ActionMode) {
    super.onActionModeStarted(mode)
    selecting = true
    content?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
  }

  override fun onActionModeFinished(mode: ActionMode) {
    super.onActionModeFinished(mode)
    selecting = false
  }

  private fun leave() {
    backCallback.isEnabled = false
    onBackPressedDispatcher.onBackPressed()
    backCallback.isEnabled = true
  }

  override fun onWebViewCreate(webView: WebView) {
    content = webView
    renderSoftwareWhileSelecting(webView)
    webView.addJavascriptInterface(SystemBars(), "AndroidSystemBars")
    webView.addJavascriptInterface(downloads, "AndroidDownloads")
    webView.addJavascriptInterface(Background(), "AndroidBackground")
    webView.addJavascriptInterface(CodeScanner(), "AndroidQrScan")
    webView.addJavascriptInterface(installer, "AndroidInstaller")
    webView.addJavascriptInterface(Voice(), "AndroidVoice")
    webView.addJavascriptInterface(Selection(), "AndroidSelection")
    PastedContent(webView).install()
    deliverShare()
  }

  inner class CodeScanner {
    private val scanner by lazy { QrScan(this@MainActivity) { content } }

    @JavascriptInterface
    fun isAvailable(): Boolean = scanner.available()

    @JavascriptInterface
    fun scan() = scanner.scan()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode != Dictation.PERMISSION_REQUEST) return
    dictation.onPermissionResult(grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
  }

  inner class Voice {
    @JavascriptInterface
    fun isAvailable(): Boolean = dictation.available()

    @JavascriptInterface
    fun start(language: String) = dictation.start(language)

    @JavascriptInterface
    fun stop() = dictation.stop()
  }

  inner class Background {
    @JavascriptInterface
    fun batteryOptimizationIgnored(): Boolean {
      val power = getSystemService(Context.POWER_SERVICE) as PowerManager
      return power.isIgnoringBatteryOptimizations(packageName)
    }

    @JavascriptInterface
    fun requestIgnoreBatteryOptimization() {
      val target =
        if (batteryOptimizationIgnored()) Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        else Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
      runCatching {
        startActivity(Intent(target, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    }
  }

  inner class SystemBars {
    @JavascriptInterface
    fun setAppearance(dark: Boolean) {
      runOnUiThread {
        WindowCompat.getInsetsController(window, window.decorView).apply {
          isAppearanceLightStatusBars = !dark
          isAppearanceLightNavigationBars = !dark
        }
      }
    }
  }
}

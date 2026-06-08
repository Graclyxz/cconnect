package com.jahirtrap.cconnect.terminal

import android.content.Context
import android.net.wifi.WifiManager
import com.jahirtrap.cconnect.data.SshProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.OutputStream

class SshConnection(
    private val profile: SshProfile,
    private val scope: CoroutineScope,
    context: Context,
    private val onOsDetected: ((String) -> Unit)? = null,
) {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var stdin: OutputStream? = null
    private var readJob: Job? = null
    private var resizeJob: Job? = null
    @Volatile private var lastCols = 0
    @Volatile private var lastRows = 0

    private val wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
        ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "cconnect:ssh")
        ?.apply { setReferenceCounted(false) }

    private val _output = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val output: SharedFlow<ByteArray> = _output

    private val _state = MutableSharedFlow<State>(replay = 1, extraBufferCapacity = 4)
    val state: SharedFlow<State> = _state

    suspend fun connect(cols: Int = 80, rows: Int = 24) = withContext(Dispatchers.IO) {
        _state.emit(State.Connecting)
        try {
            runCatching { wifiLock?.acquire() }
            val config = DefaultConfig().apply { keepAliveProvider = KeepAliveProvider.KEEP_ALIVE }
            val ssh = SSHClient(config).apply {
                // MVP: no known_hosts verification. Mark in UI as such.
                addHostKeyVerifier(PromiscuousVerifier())
                connect(profile.host, profile.port)
                connection.keepAlive.keepAliveInterval = 15
                authPassword(profile.user, profile.password)
            }
            runCatching { probeOs(ssh) }.getOrNull()?.let { onOsDetected?.invoke(it) }
            val s = ssh.startSession()
            s.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
            val sh = s.startShell()
            client = ssh
            session = s
            shell = sh
            stdin = sh.outputStream
            _state.emit(State.Connected)
            readJob = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(8192)
                val input = sh.inputStream
                try {
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        _output.emit(buf.copyOf(n))
                    }
                } catch (_: Exception) {
                } finally {
                    _state.emit(State.Closed)
                }
            }
        } catch (e: Exception) {
            _state.emit(State.Failed(e.message ?: e::class.simpleName.orEmpty()))
            close()
        }
    }

    fun send(bytes: ByteArray) {
        val out = stdin ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                out.write(bytes)
                out.flush()
            }
        }
    }

    fun send(text: String) = send(text.toByteArray(Charsets.UTF_8))

    // Debounced: termlib fires onResize many times during keyboard animations and each
    // SIGWINCH makes the remote TUI redraw a partial frame into scrollback.
    fun resize(cols: Int, rows: Int) {
        lastCols = cols
        lastRows = rows
        val sh = shell ?: return
        resizeJob?.cancel()
        resizeJob = scope.launch(Dispatchers.IO) {
            delay(50)
            runCatching { sh.changeWindowDimensions(lastCols, lastRows, 0, 0) }
        }
    }

    private fun probeOs(ssh: SSHClient): String? {
        val probeCmd =
            "sh -c 'uname -s 2>/dev/null; cat /etc/os-release /etc/lsb-release /etc/system-release 2>/dev/null'"
        val raw = runCatching {
            ssh.startSession().use { s ->
                val cmd = s.exec(probeCmd)
                val text = cmd.inputStream.bufferedReader().readText()
                cmd.join(3, java.util.concurrent.TimeUnit.SECONDS)
                text
            }
        }.getOrNull().orEmpty()
        android.util.Log.d("CConnect.SSH", "OS probe raw: ${raw.take(400)}")
        if (raw.isBlank()) return "windows"
        val upper = raw.uppercase()
        when {
            "DARWIN" in upper -> return "macos"
            "MINGW" in upper || "MSYS" in upper || "CYGWIN" in upper -> return "windows"
        }
        Regex("(?m)^(?:ID|DISTRIB_ID)=\"?([a-zA-Z0-9_.-]+)\"?")
            .find(raw)?.groupValues?.get(1)?.lowercase()
            ?.let { return it }
        return if ("LINUX" in upper) "linux" else null
    }

    fun close() {
        readJob?.cancel()
        readJob = null
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { client?.disconnect() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }
        shell = null
        session = null
        client = null
        stdin = null
    }

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Connected : State
        data object Closed : State
        data class Failed(val message: String) : State
    }
}

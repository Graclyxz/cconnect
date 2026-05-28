package com.jahirtrap.cconnect.terminal

import com.jahirtrap.cconnect.data.SshProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.OutputStream

class SshConnection(
    private val profile: SshProfile,
    private val scope: CoroutineScope,
) {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var stdin: OutputStream? = null
    private var readJob: Job? = null
    private var resizeJob: Job? = null
    @Volatile private var lastCols = 0
    @Volatile private var lastRows = 0

    private val _output = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val output: SharedFlow<ByteArray> = _output

    private val _state = MutableSharedFlow<State>(replay = 1, extraBufferCapacity = 4)
    val state: SharedFlow<State> = _state

    suspend fun connect(cols: Int = 80, rows: Int = 24) = withContext(Dispatchers.IO) {
        _state.emit(State.Connecting)
        try {
            val ssh = SSHClient().apply {
                // MVP: no known_hosts verification. Mark in UI as such.
                addHostKeyVerifier(PromiscuousVerifier())
                connect(profile.host, profile.port)
                authPassword(profile.user, profile.password)
            }
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

    fun close() {
        readJob?.cancel()
        readJob = null
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { client?.disconnect() }
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

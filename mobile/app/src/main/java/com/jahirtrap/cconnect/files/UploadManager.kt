package com.jahirtrap.cconnect.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jahirtrap.cconnect.data.remote.SharedApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object UploadManager {

    enum class Status { Uploading, Done, Failed }

    data class Upload(
        val id: Long,
        val name: String,
        val dir: String,
        val progress: Float = 0f,
        val status: Status = Status.Uploading,
    )

    private val _uploads = MutableStateFlow<List<Upload>>(emptyList())
    val uploads: StateFlow<List<Upload>> = _uploads

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong()
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun enqueue(context: Context, uri: Uri, dir: String) {
        val resolver = context.applicationContext.contentResolver
        val (name, length) = resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
            val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
            (name ?: uri.lastPathSegment ?: "file") to size
        } ?: ((uri.lastPathSegment ?: "file") to -1L)

        val id = nextId.incrementAndGet()
        _uploads.value += Upload(id, name, dir)
        jobs[id] = scope.launch {
            val path = if (dir.isEmpty()) name else "$dir/$name"
            val ok = SharedApi.upload(path, length, { resolver.openInputStream(uri) }) { p ->
                patch(id) { it.copy(progress = p) }
            }
            patch(id) { it.copy(progress = 1f, status = if (ok) Status.Done else Status.Failed) }
        }.also { job -> job.invokeOnCompletion { jobs.remove(id) } }
    }

    fun cancel(id: Long) {
        jobs.remove(id)?.cancel()
        _uploads.value = _uploads.value.filterNot { it.id == id && it.status == Status.Uploading }
    }

    fun clearFinished() {
        _uploads.value = _uploads.value.filter { it.status == Status.Uploading }
    }

    private fun patch(id: Long, transform: (Upload) -> Upload) {
        _uploads.value = _uploads.value.map { if (it.id == id) transform(it) else it }
    }
}

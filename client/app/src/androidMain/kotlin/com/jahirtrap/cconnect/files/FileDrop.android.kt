package com.jahirtrap.cconnect.files

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent

actual fun filesFromDrop(event: DragAndDropEvent): List<AttachmentFile> {
    val clip = event.toAndroidDragEvent().clipData ?: return emptyList()
    val result = ArrayList<AttachmentFile>()
    for (i in 0 until clip.itemCount) {
        clip.getItemAt(i)?.uri?.let { result.add(AttachmentFile(it)) }
    }
    return result
}

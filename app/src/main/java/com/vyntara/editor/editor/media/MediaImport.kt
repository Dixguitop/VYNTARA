package com.vyntara.editor.editor.media

/**
 * Media import contract description.
 * Actual picker is launched from Compose via ActivityResultContracts.GetContent / OpenDocument.
 * This module holds metadata extracted after URI grant — no fake duration.
 */
data class ImportedMedia(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean
)

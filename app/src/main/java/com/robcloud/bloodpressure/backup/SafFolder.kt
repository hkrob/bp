package com.robcloud.bloodpressure.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/**
 * Thin wrapper over [DocumentsContract] for one folder inside a SAF tree. Used instead of
 * DocumentFile because DocumentFile swallows provider errors — a failed or still-loading folder
 * listing looks exactly like an empty folder, which made sync create duplicate backup files.
 * Every method here throws on failure instead. All calls are provider IPC: use off the main thread.
 */
internal class SafFolder(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val documentId: String = DocumentsContract.getTreeDocumentId(treeUri)
) {
    data class Child(val uri: Uri, val documentId: String, val name: String, val isDirectory: Boolean)

    private val documentUri: Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    /** Children named [name]. Throws if the listing fails, or is incomplete and has no match. */
    fun find(name: String, directory: Boolean = false): List<Child> {
        val (children, loading) = list()
        val matches = children.filter { it.name == name && it.isDirectory == directory }
        if (matches.isEmpty() && loading) {
            throw IOException("The backup folder is still loading from the storage provider — try again shortly")
        }
        return matches.sortedBy { it.documentId }
    }

    fun children(): List<Child> = list().first

    fun readText(child: Child): String =
        resolver.openInputStream(child.uri)?.use { it.reader(Charsets.UTF_8).readText() }
            ?: throw IOException("Could not read ${child.name}")

    /** Replaces the file's contents. */
    fun overwrite(child: Child, text: String) {
        resolver.openOutputStream(child.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Could not open ${child.name} for writing")
    }

    /** Creates a new file and writes [text] into it. */
    fun create(name: String, text: String, mimeType: String = "text/csv"): Child {
        val uri = DocumentsContract.createDocument(resolver, documentUri, mimeType, name)
            ?: throw IOException("Could not create $name in the backup folder")
        val child = Child(uri, DocumentsContract.getDocumentId(uri), name, isDirectory = false)
        overwrite(child, text)
        return child
    }

    fun findOrCreateDirectory(name: String): SafFolder {
        val existing = find(name, directory = true).firstOrNull()
        val id = existing?.documentId ?: run {
            val uri = DocumentsContract.createDocument(resolver, documentUri, Document.MIME_TYPE_DIR, name)
                ?: throw IOException("Could not create the $name folder")
            DocumentsContract.getDocumentId(uri)
        }
        return SafFolder(resolver, treeUri, id)
    }

    fun delete(child: Child) {
        DocumentsContract.deleteDocument(resolver, child.uri)
    }

    /** All children, plus whether the provider flagged the listing as still loading. */
    private fun list(): Pair<List<Child>, Boolean> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("Could not list the backup folder")
        return cursor.use { c ->
            val children = mutableListOf<Child>()
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: throw IOException("The storage provider returned a file without a name")
                children += Child(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    documentId = id,
                    name = name,
                    isDirectory = c.getString(2) == Document.MIME_TYPE_DIR
                )
            }
            children to (c.extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true)
        }
    }
}

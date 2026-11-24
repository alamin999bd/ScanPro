package com.scanpro.pdfdocscanner.data

import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.data.db.AppDatabase
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.data.db.FolderEntity
import com.scanpro.pdfdocscanner.data.db.FolderWithCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Single source of truth for documents and folders (database rows + files on disk). */
class DocumentRepository(
    private val db: AppDatabase,
    private val files: FileStore,
) {
    private val docs get() = db.documentDao()
    private val folders get() = db.folderDao()

    fun observeDocuments(): Flow<List<DocumentEntity>> = docs.observeAll()
    fun observeRecent(limit: Int = 5): Flow<List<DocumentEntity>> = docs.observeRecent(limit)
    fun observeDocumentsInFolder(folderId: Long): Flow<List<DocumentEntity>> = docs.observeInFolder(folderId)
    fun observeDocumentCount(): Flow<Int> = docs.observeCount()

    fun observeFolders(): Flow<List<FolderWithCount>> = folders.observeAllWithCount()
    fun observeFolderList(): Flow<List<FolderEntity>> = folders.observeAll()

    fun fileOf(doc: DocumentEntity): File = files.docFile(doc.fileName)

    suspend fun getDocument(id: Long): DocumentEntity? = withContext(Dispatchers.IO) { docs.getById(id) }

    /** Registers a file already written to the documents directory. */
    suspend fun addDocument(
        name: String,
        file: File,
        type: String,
        pageCount: Int,
        folderId: Long? = null,
    ): Long = withContext(Dispatchers.IO) {
        docs.insert(
            DocumentEntity(
                name = FileStore.sanitizeName(name),
                fileName = file.name,
                type = type,
                pageCount = pageCount,
                sizeBytes = file.length(),
                folderId = folderId,
            )
        )
    }

    suspend fun renameDocument(doc: DocumentEntity, newName: String) = withContext(Dispatchers.IO) {
        docs.update(doc.copy(name = FileStore.sanitizeName(newName)))
    }

    suspend fun moveDocument(doc: DocumentEntity, folderId: Long?) = withContext(Dispatchers.IO) {
        docs.update(doc.copy(folderId = folderId))
    }

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        files.docFile(doc.fileName).delete()
        docs.delete(doc)
    }

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        folders.insert(FolderEntity(name = name.trim()))
    }

    suspend fun renameFolder(id: Long, newName: String) = withContext(Dispatchers.IO) {
        folders.getById(id)?.let { folders.update(it.copy(name = newName.trim())) }
    }

    /** Deleting a folder keeps its documents; they move back to "My Documents". */
    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        folders.getById(id)?.let { folders.delete(it) }
    }

    suspend fun getFolder(id: Long): FolderEntity? = withContext(Dispatchers.IO) { folders.getById(id) }
}

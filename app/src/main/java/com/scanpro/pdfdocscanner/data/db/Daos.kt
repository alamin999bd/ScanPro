package com.scanpro.pdfdocscanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun observeInFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Delete
    suspend fun delete(doc: DocumentEntity)
}

@Dao
interface TextDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(text: DocumentText)

    @Query("SELECT text FROM document_text WHERE docId = :docId")
    suspend fun textOf(docId: Long): String?

    @Query("SELECT docId FROM document_text WHERE instr(lower(text), lower(:needle)) > 0")
    fun observeMatches(needle: String): Flow<List<Long>>
}

@Dao
interface FolderDao {
    @Query(
        """
        SELECT f.id AS id, f.name AS name, f.createdAt AS createdAt,
               (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id) AS documentCount
        FROM folders f ORDER BY f.name COLLATE NOCASE ASC
        """
    )
    fun observeAllWithCount(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)
}

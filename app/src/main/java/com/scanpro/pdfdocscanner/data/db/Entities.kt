package com.scanpro.pdfdocscanner.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("folderId")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** File name inside the app's private documents directory. */
    val fileName: String,
    /** "pdf" or "image" */
    val type: String,
    val pageCount: Int = 1,
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val folderId: Long? = null,
)

data class FolderWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val documentCount: Int,
)

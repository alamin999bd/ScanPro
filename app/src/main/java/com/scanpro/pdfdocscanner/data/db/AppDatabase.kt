package com.scanpro.pdfdocscanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, FolderEntity::class, DocumentText::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao
    abstract fun textDao(): TextDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scanpro.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds the table that holds recognized (OCR) text. Existing documents are untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_text` (`docId` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "PRIMARY KEY(`docId`), FOREIGN KEY(`docId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }
    }
}

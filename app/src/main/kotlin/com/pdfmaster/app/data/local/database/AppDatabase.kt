package com.pdfmaster.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pdfmaster.app.data.local.entity.*

@Database(
    entities = [
        PdfEntity::class,
        RecentFileEntity::class,
        SignatureEntity::class,
        AnnotationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
}

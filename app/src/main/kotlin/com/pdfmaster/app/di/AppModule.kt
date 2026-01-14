package com.pdfmaster.app.di

import android.content.Context
import androidx.room.Room
import com.pdfmaster.app.data.local.database.PdfDao
import com.pdfmaster.app.data.local.database.PdfDatabase
import com.pdfmaster.app.data.local.preferences.UserPreferences
import com.pdfmaster.app.data.repository.FileRepositoryImpl
import com.pdfmaster.app.data.repository.PdfRepositoryImpl
import com.pdfmaster.app.data.repository.SettingsRepositoryImpl
import com.pdfmaster.app.domain.repository.FileRepository
import com.pdfmaster.app.domain.repository.PdfRepository
import com.pdfmaster.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePdfDatabase(
        @ApplicationContext context: Context
    ): PdfDatabase {
        return Room.databaseBuilder(
            context,
            PdfDatabase::class.java,
            PdfDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun providePdfDao(database: PdfDatabase): PdfDao {
        return database.pdfDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        return UserPreferences(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPdfRepository(
        pdfRepositoryImpl: PdfRepositoryImpl
    ): PdfRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(
        fileRepositoryImpl: FileRepositoryImpl
    ): FileRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}

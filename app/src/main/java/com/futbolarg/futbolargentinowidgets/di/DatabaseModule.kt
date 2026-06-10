package com.futbolarg.futbolargentinowidgets.di

import android.content.Context
import androidx.room.Room
import com.futbolarg.futbolargentinowidgets.data.local.dao.MatchDao
import com.futbolarg.futbolargentinowidgets.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ============================================================
// DatabaseModule.kt
// ============================================================
// Le enseña a Hilt cómo construir la base de datos Room
// y cómo obtener los DAOs.
//
// @ApplicationContext → Hilt inyecta el Context de la app
// automáticamente (Room lo necesita para crear el archivo DB)
// ============================================================

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Crear la base de datos Room
    // "futbol_widgets_db" es el nombre del archivo .db en el teléfono
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "futbol_widgets_db"
        )
            // Si cambiás la versión de la DB y no definís una migración,
            // esto borra todo y recrea las tablas. Solo para desarrollo.
            // En producción hay que hacer migraciones reales.
            .fallbackToDestructiveMigration()
            .build()
    }

    // Proveer el MatchDao desde la base de datos
    @Provides
    @Singleton
    fun provideMatchDao(database: AppDatabase): MatchDao {
        return database.matchDao()
    }
}
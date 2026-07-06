package com.futbolarg.futbolargentinowidgets.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.futbolarg.futbolargentinowidgets.data.local.dao.MatchDao
import com.futbolarg.futbolargentinowidgets.data.local.entity.MatchEntity

// ============================================================
// AppDatabase.kt
// ============================================================
// Clase principal de Room.
//
// version = 3: se agregó leagueName al volverse la app
// multicompetición (liga + copas). Seguimos en desarrollo, así
// que usamos fallbackToDestructiveMigration (ver DatabaseModule)
// en lugar de escribir una migración real.
// ============================================================

@Database(
    entities = [MatchEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
}

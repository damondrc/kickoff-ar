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
// version = 4: se agregó phase (fase del torneo) al implementar
// el historial de resultados. Seguimos usando
// fallbackToDestructiveMigration (ver DatabaseModule): al
// actualizar, la DB se recrea vacía y el primer sync la vuelve a
// llenar con la temporada completa desde el servidor — así que
// no se pierde historial real.
// ============================================================

@Database(
    entities = [MatchEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
}

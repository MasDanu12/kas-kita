package com.example.kaskita

import android.app.Application
import com.example.kaskita.data.db.AppDatabase
import com.example.kaskita.data.repository.KasKitaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class KasKitaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { KasKitaRepository(database.kasKitaDao()) }
}

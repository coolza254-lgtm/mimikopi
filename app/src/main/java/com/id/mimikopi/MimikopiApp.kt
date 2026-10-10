package com.id.mimikopi

import android.app.Application
import com.id.mimikopi.data.db.AppDatabase

/** Hand-written dependency container (no DI framework needed at this size). */
class MimikopiApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
}

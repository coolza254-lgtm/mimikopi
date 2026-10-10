package com.id.mimikopi

import android.app.Application
import com.id.mimikopi.backup.BackupService
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.update.UpdateService

/** Hand-written dependency container (no DI framework needed at this size). */
class MimikopiApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val backupService: BackupService by lazy { BackupService(this, database) }
    val updateService: UpdateService by lazy { UpdateService(this, database) }
}

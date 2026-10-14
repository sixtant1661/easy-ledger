package com.easyledger.app

import android.app.Application
import com.easyledger.app.data.local.LedgerDatabase
import com.easyledger.app.data.repository.LedgerRepository

class LedgerApplication : Application() {
    val database by lazy { LedgerDatabase.open(this) }
    val repository by lazy { LedgerRepository(database) }
}

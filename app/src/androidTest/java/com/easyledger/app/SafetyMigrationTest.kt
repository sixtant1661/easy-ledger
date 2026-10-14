package com.easyledger.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.data.backup.BackupCodec
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SafetyMigrationTest {
    @Test fun importActualLegacyRunAsSnapshotIntoSeparateApp(): Unit = runBlocking {
        val app=ApplicationProvider.getApplicationContext<LedgerApplication>()
        assertEquals("com.easyledger.app.recovery",app.packageName)
        val file=File(app.filesDir,"legacy-migration.json")
        assertTrue("Run scripts/verify-legacy-rescue.py, not the regular test suite",file.exists())
        val backup=BackupCodec.decode(file.readText())
        app.repository.restore(backup)
        val saved=app.repository.dao.record("r")!!
        assertEquals("3.335",saved.unitPrice);assertEquals("6.67",saved.amount)
        assertEquals(backup.projects.single(),app.repository.dao.project("p"))
        assertEquals(backup.records.single(),saved)
        app.repository.restore(backup)
        assertEquals(saved,app.repository.dao.record("r"))
        app.repository.deleteRecord("r");app.repository.deleteProject("p")
        file.delete()
    }
}

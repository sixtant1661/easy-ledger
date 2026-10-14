package com.easyledger.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.data.local.*
import com.easyledger.app.data.repository.LedgerRepository
import com.easyledger.app.domain.model.BillingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LedgerDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LedgerDatabase
    private lateinit var repository: LedgerRepository
    private val name = "test-ledger-${UUID.randomUUID()}.db"
    @Before fun open() { db = LedgerDatabase.open(context, name); repository = LedgerRepository(db) }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun project(): String {
        repository.saveProject(null, "桥梁施工", "运输 100%", BillingMode.DAY, "天", "3.33")
        return repository.projects("").first().first().id
    }
    @Test fun createEditSearchAndSoftDeletePreserveHistory() = runBlocking {
        val id = project()
        repository.saveRecord(null, id, "2026-10-14", BillingMode.DAY, "天", "1", "3.33", "加班 100%")
        val record = repository.records("").first().single().record
        assertEquals("3.33", record.amount)
        repository.saveProject(id, "新桥工程", "备注更新", BillingMode.HOUR, "小时", "99")
        assertEquals("3.33", repository.records("").first().single().record.unitPrice)
        assertEquals(1, repository.projects("备注更新").first().size)
        assertEquals(1, repository.records("新桥").first().size)
        assertEquals(1, repository.records("%").first().size)
        assertTrue(repository.records("不存在").first().isEmpty())
        repository.saveRecord(record.id, id, "2026-10-15", BillingMode.DAY, "天", "2", "3.33", "修改后")
        assertEquals("6.66", repository.records("").first().single().record.amount)
        repository.deleteProject(id)
        assertTrue(repository.projects("").first().isEmpty())
        assertEquals(1, repository.records("").first().size)
        repository.deleteRecord(record.id)
        assertTrue(repository.records("").first().isEmpty())
    }
    @Test fun reopeningFileDatabaseKeepsRecordsAndOnboarding() = runBlocking {
        val id = project()
        repository.saveRecord(null, id, "2026-10-14", BillingMode.HOUR, "小时", "7.5", "20", "持久化检查")
        repository.finishOnboarding()
        db.close()
        db = LedgerDatabase.open(context, name)
        repository = LedgerRepository(db)
        assertEquals("150.00", repository.records("").first().single().record.amount)
        assertEquals("true", repository.onboarding.first())
    }
    @Test fun failedMultiTableTransactionRollsBack() = runBlocking {
        var failed = false
        try {
            db.withTransaction {
                db.dao().insertProject(ProjectEntity("rollback-project", "合成数据", "DAY", "天", "1", 1, 1))
                db.dao().insertRecord(WorkRecordEntity("bad", "missing-project", "2026-10-14", "DAY", "天", "1", "1", "1.00", "", 1, 1))
            }
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertNull(db.dao().project("rollback-project"))
        assertTrue(repository.records("").first().isEmpty())
    }
    @Test fun retryingSameDraftUpdatesOneRecordInsteadOfDuplicating() = runBlocking {
        val id = project()
        repository.saveRecord(null, id, "2026-10-14", BillingMode.HOUR, "小时", "1", "20", "首次", draftId = "synthetic-draft")
        repository.saveRecord(null, id, "2026-10-14", BillingMode.HOUR, "小时", "2", "20", "重试", draftId = "synthetic-draft")
        assertEquals(1, repository.records("").first().size)
        assertEquals("40.00", repository.records("").first().single().record.amount)
    }
    @Test fun invalidRecordEditDoesNotChangeStoredRecord() = runBlocking {
        val id = project()
        repository.saveRecord(null, id, "2026-10-14", BillingMode.DAY, "天", "1", "5", "原记录")
        val saved = repository.records("").first().single().record
        try { repository.saveRecord(saved.id, id, "2026-02-30", BillingMode.DAY, "天", "2", "5", "不应保存"); fail("invalid date accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(saved, repository.records("").first().single().record)
    }
    @Test fun versionOneMigrationKeepsRecordAndAddsProjectNote() = runBlocking {
        db.close()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, defaultMode TEXT NOT NULL, defaultUnit TEXT NOT NULL, defaultPrice TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)")
            old.execSQL("CREATE TABLE work_records (id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, date TEXT NOT NULL, mode TEXT NOT NULL, unit TEXT NOT NULL, quantity TEXT NOT NULL, unitPrice TEXT NOT NULL, amount TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, actualMinutes INTEGER, quantitySource TEXT NOT NULL, calculationPolicyVersion INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE RESTRICT)")
            old.execSQL("CREATE INDEX index_work_records_projectId_date_deletedAt ON work_records(projectId, date, deletedAt)")
            old.execSQL("CREATE TABLE preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            old.execSQL("CREATE TABLE work_segments (id TEXT NOT NULL PRIMARY KEY, recordId TEXT NOT NULL, position INTEGER NOT NULL, startDateTime TEXT NOT NULL, endDateTime TEXT NOT NULL, FOREIGN KEY(recordId) REFERENCES work_records(id) ON DELETE RESTRICT)")
            old.execSQL("CREATE UNIQUE INDEX index_work_segments_recordId_position ON work_segments(recordId, position)")
            old.execSQL("INSERT INTO projects VALUES ('p', '升级前项目', 'DAY', '天', '25', 1, 1, NULL)")
            old.execSQL("INSERT INTO work_records VALUES ('r', 'p', '2026-10-14', 'DAY', '天', '2', '25', '50.00', '升级前记录', 1, 1, NULL, NULL, 'MANUAL', 1)")
            old.version = 1
        }
        db = LedgerDatabase.open(context, name)
        repository = LedgerRepository(db)
        assertEquals("50.00", repository.records("").first().single().record.amount)
        assertEquals("", repository.projects("").first().single().note)
        repository.saveProject("p", "升级后项目", "可搜索的新备注", BillingMode.DAY, "天", "25")
        assertEquals(1, repository.projects("新备注").first().size)
    }
}

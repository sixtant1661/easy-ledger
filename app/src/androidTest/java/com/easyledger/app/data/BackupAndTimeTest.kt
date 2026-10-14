package com.easyledger.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.data.local.*
import com.easyledger.app.data.repository.LedgerRepository
import com.easyledger.app.data.backup.*
import com.easyledger.app.domain.model.BillingMode
import com.easyledger.app.domain.time.TimeSpan
import com.easyledger.app.domain.settlement.Settlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupAndTimeTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="backup-test-${UUID.randomUUID()}.db"
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    @Before fun open() { db=LedgerDatabase.open(context,name);repo=LedgerRepository(db) }
    @After fun close() { db.close();context.deleteDatabase(name) }
    private suspend fun project(): String {
        repo.saveProject(null,"合成工程","仅测试",BillingMode.HOUR,"小时","25",draftId="p")
        return "p"
    }
    private val spans=listOf(TimeSpan("2026-10-14T07:00","2026-10-14T11:00"),TimeSpan("2026-10-14T22:00","2026-10-15T01:00"))
    @Test fun crossMidnightAndManualOverrideRetainOriginalTimes()=runBlocking {
        val p=project()
        repo.saveRecord(null,p,"2026-10-14",BillingMode.HOUR,"小时","1","25","",draftId="r",spans=spans,useTime=true)
        val actual=repo.dao.record("r")!!
        assertEquals(420L,actual.actualMinutes);assertEquals("175.00",actual.amount);assertEquals("TIME",actual.quantitySource)
        val before=repo.dao.segments("r")
        repo.saveRecord("r",p,"2026-10-14",BillingMode.HOUR,"小时","8.5","25","手动覆盖")
        assertEquals("212.50",repo.dao.record("r")!!.amount)
        assertEquals(420L,repo.dao.record("r")!!.actualMinutes);assertEquals(before,repo.dao.segments("r"))
        assertEquals("MANUAL",repo.dao.record("r")!!.quantitySource)
    }
    @Test fun overlapAcrossDatesIsRejectedAndNoPartialSave()=runBlocking {
        val p=project();repo.saveRecord(null,p,"2026-10-14",BillingMode.HOUR,"小时","1","25","",draftId="r",spans=spans)
        try { repo.saveRecord(null,p,"2026-10-15",BillingMode.HOUR,"小时","1","25","",draftId="bad",spans=listOf(TimeSpan("2026-10-15T00:30","2026-10-15T02:00")));fail("overlap accepted") }catch(_:IllegalArgumentException){}
        assertNull(repo.dao.record("bad"));assertEquals(1,repo.records("").first().size)
    }
    @Test fun batchAtomicRetryAndFilteredSettlement()=runBlocking {
        val p=project()
        repo.saveBatch(p,listOf("2026-10-14","2026-10-15"),BillingMode.HOUR,"小时","1","25","批量",spans,"2026-10-14",true,"batch")
        repo.saveBatch(p,listOf("2026-10-14","2026-10-15"),BillingMode.HOUR,"小时","1","25","批量",spans,"2026-10-14",true,"batch")
        assertEquals(2,repo.records("").first().size)
        assertEquals("350.00",repo.backup().total)
        val doc=Settlements.create(repo.backup(),p,"2026-10-15","2026-10-15")
        assertEquals("175.00",doc.total);assertEquals(1,doc.rows.size);assertTrue(doc.rows.single().cells[2].contains("\n"))
        try {repo.saveBatch(p,listOf("2026-10-16","not-a-date"),BillingMode.DAY,"天","2","25","",emptyList(),"2026-10-16",false,"badbatch");fail("invalid date accepted")}catch(_:Exception){}
        assertEquals(2,repo.records("").first().size)
    }
    @Test fun backupRoundTripIntoFreshDatabasePreservesAllEntities()=runBlocking {
        val p=project();repo.saveRecord(null,p,"2026-10-14",BillingMode.HOUR,"小时","8","25","备注",draftId="r",spans=spans)
        repo.finishOnboarding();repo.deleteProject(p)
        val original=repo.backup();val text=BackupCodec.encode(original)
        val decoded=BackupCodec.decode(text);assertEquals(original,decoded)
        val targetName="restore-${UUID.randomUUID()}.db";val target=LedgerDatabase.open(context,targetName)
        try {
            val restored=LedgerRepository(target);restored.restore(decoded);restored.restore(decoded)
            assertEquals(original,restored.backup());assertEquals("200.00",restored.backup().total)
        }finally{target.close();context.deleteDatabase(targetName)}
    }
    @Test fun historicalThreeDecimalPriceNeverRoundedByBackup()=runBlocking {
        project()
        repo.dao.insertRecord(WorkRecordEntity("legacy","p","2026-10-14","DAY","天","2","3.335","6.67","历史合成数据",1,1))
        val original=repo.backup();val restored=BackupCodec.decode(BackupCodec.encode(original))
        assertEquals("3.335",restored.records.single().unitPrice);assertEquals("6.67",restored.total)
        repo.saveRecord("legacy","p","2026-10-14",BillingMode.DAY,"天","2","3.335","只改备注")
        assertEquals("3.335",repo.dao.record("legacy")!!.unitPrice);assertEquals("6.67",repo.dao.record("legacy")!!.amount)
    }
    @Test fun corruptedBackupAndConflictingRestoreLeaveLocalDataUntouched()=runBlocking {
        val p=project();repo.saveRecord(null,p,"2026-10-14",BillingMode.DAY,"天","2","25","原始",draftId="r")
        val before=repo.backup()
        val corrupt=BackupCodec.encode(before).replace("合成工程","损坏工程")
        try {BackupCodec.decode(corrupt);fail("corruption accepted")}catch(_:IllegalArgumentException){}
        val conflict=before.copy(records=before.records.map { it.copy(note="冲突") })
        try {repo.restore(conflict);fail("conflict accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,repo.backup())
    }
}

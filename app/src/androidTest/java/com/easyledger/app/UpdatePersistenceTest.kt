package com.easyledger.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.data.local.PreferenceEntity
import com.easyledger.app.domain.model.BillingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UpdatePersistenceTest {
    @Test fun recordSurvivesForceStopAndSignedPackageReplacement() = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<LedgerApplication>().repository
        when (InstrumentationRegistry.getArguments().getString("phase")) {
            "seed" -> {
                val name = "安装更新检查-合成数据-${UUID.randomUUID()}"
                repo.saveProject(null, name, "合成测试", BillingMode.DAY, "天", "25")
                val project = repo.projects(name).first().single()
                repo.saveRecord(null, project.id, "2026-10-14", BillingMode.DAY, "天", "2", "25", name)
                val record = repo.records(name).first().single().record
                repo.dao.preference(PreferenceEntity("update_test_record_id", record.id))
                repo.finishOnboarding()
                assertEquals("50.00", record.amount)
            }
            "verify" -> {
                val id = repo.dao.preference("update_test_record_id").first()
                assertNotNull("Seed marker disappeared after signed APK replacement", id)
                val record = repo.records("").first().firstOrNull { it.record.id == id }
                assertNotNull("Saved record disappeared after package replacement", record)
                assertEquals("50.00", record!!.record.amount)
                assertEquals("true", repo.onboarding.first())
                repo.deleteRecord(record.record.id)
                repo.deleteProject(record.record.projectId)
            }
            else -> fail("Run this test using scripts/verify-update-persistence.py, with explicit seed and verify phases")
        }
    }
}

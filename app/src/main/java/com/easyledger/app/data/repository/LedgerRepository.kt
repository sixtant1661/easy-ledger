package com.easyledger.app.data.repository

import androidx.room.withTransaction
import com.easyledger.app.data.local.*
import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.model.BillingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class LedgerRepository(private val db: LedgerDatabase) {
    val dao = db.dao()
    fun projects(keyword: String) = dao.projects(keyword.trim())
    fun records(keyword: String) = dao.records(keyword.trim())
    val allProjects = dao.allProjects()
    val onboarding = dao.preference("onboarding_complete")
    suspend fun finishOnboarding() = dao.preference(PreferenceEntity("onboarding_complete", "true"))
    suspend fun saveProject(id: String?, name: String, note: String, mode: BillingMode, unit: String, price: String) = db.withTransaction {
        require(name.trim().isNotEmpty()) { "请填写项目名称" }
        require(unit.trim().isNotEmpty()) { "请填写计费单位" }
        val decimal = Money.decimal(price, "单价").toPlainString()
        val now = System.currentTimeMillis()
        val existing = id?.let { requireNotNull(dao.project(it)) { "项目已不存在" } }
        require(existing?.deletedAt == null) { "此项目已删除" }
        val project = ProjectEntity(id ?: UUID.randomUUID().toString(), name.trim(), mode.name, unit.trim(), decimal,
            existing?.createdAt ?: now, now, note = note.trim())
        if (existing == null) dao.insertProject(project) else check(dao.updateProject(project) == 1)
    }
    suspend fun deleteProject(id: String) = db.withTransaction { check(dao.deleteProject(id, System.currentTimeMillis()) == 1) { "项目已删除或不存在" } }
    suspend fun saveRecord(id: String?, projectId: String, date: String, mode: BillingMode, unit: String, quantity: String, price: String, note: String) = db.withTransaction {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val parsed = runCatching { parser.parse(date) }.getOrNull()
        require(parsed != null && parser.format(parsed) == date) { "请通过日历选择有效日期" }
        require(unit.trim().isNotEmpty()) { "请填写计费单位" }
        val existing = id?.let { requireNotNull(dao.record(it)) { "记录已不存在" } }
        require(existing?.deletedAt == null) { "此记录已删除" }
        val project = requireNotNull(dao.project(projectId)) { "请选择项目" }
        require(project.deletedAt == null || existing?.projectId == projectId) { "此项目已删除，请选择其他项目" }
        val amount = Money.amount(quantity, price, mode).toPlainString()
        val now = System.currentTimeMillis()
        val record = WorkRecordEntity(id ?: UUID.randomUUID().toString(), projectId, date, mode.name, unit.trim(),
            Money.quantity(quantity, mode).toPlainString(), Money.decimal(price, "单价").toPlainString(), amount, note.trim(),
            existing?.createdAt ?: now, now, actualMinutes = existing?.actualMinutes,
            quantitySource = "MANUAL", calculationPolicyVersion = existing?.calculationPolicyVersion ?: 1)
        // Keep future time segments and original duration when changing billed quantity.
        if (existing == null) dao.insertRecord(record) else check(dao.updateRecord(record) == 1)
    }
    suspend fun deleteRecord(id: String) = db.withTransaction { check(dao.deleteRecord(id, System.currentTimeMillis()) == 1) { "记录已删除或不存在" } }
}

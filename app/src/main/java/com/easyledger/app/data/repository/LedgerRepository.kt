package com.easyledger.app.data.repository

import androidx.room.withTransaction
import com.easyledger.app.data.local.*
import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.model.BillingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import com.easyledger.app.domain.time.*
import com.easyledger.app.data.backup.LedgerBackup

class LedgerRepository(private val db: LedgerDatabase) {
    val dao = db.dao()
    fun projects(keyword: String) = dao.projects(keyword.trim())
    fun records(keyword: String) = dao.records(keyword.trim())
    val allProjects = dao.allProjects()
    val onboarding = dao.preference("onboarding_complete")
    suspend fun finishOnboarding() = dao.preference(PreferenceEntity("onboarding_complete", "true"))
    suspend fun saveProject(id: String?, name: String, note: String, mode: BillingMode, unit: String, price: String, draftId: String? = null) = db.withTransaction {
        require(name.trim().isNotEmpty()) { "请填写项目名称" }
        require(unit.trim().isNotEmpty()) { "请填写计费单位" }
        val now = System.currentTimeMillis()
        val existing = if (id != null) requireNotNull(dao.project(id)) { "项目已不存在" } else draftId?.let { dao.project(it) }
        val decimal = (if (price.trim() == existing?.defaultPrice) Money.decimal(price, "历史单价") else Money.price(price)).toPlainString()
        require(existing?.deletedAt == null) { "此项目已删除" }
        val project = ProjectEntity(existing?.id ?: draftId ?: UUID.randomUUID().toString(), name.trim(), mode.name, unit.trim(), decimal,
            existing?.createdAt ?: now, now, note = note.trim())
        if (existing == null) dao.insertProject(project) else check(dao.updateProject(project) == 1)
    }
    suspend fun deleteProject(id: String) = db.withTransaction { check(dao.deleteProject(id, System.currentTimeMillis()) == 1) { "项目已删除或不存在" } }
    suspend fun saveRecord(id: String?, projectId: String, date: String, mode: BillingMode, unit: String, quantity: String, price: String, note: String, draftId: String? = null, spans: List<TimeSpan>? = null, useTime: Boolean = false) = db.withTransaction {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val parsed = runCatching { parser.parse(date) }.getOrNull()
        require(parsed != null && parser.format(parsed) == date) { "请通过日历选择有效日期" }
        require(unit.trim().isNotEmpty()) { "请填写计费单位" }
        val existing = if (id != null) requireNotNull(dao.record(id)) { "记录已不存在" } else draftId?.let { dao.record(it) }
        require(existing?.deletedAt == null) { "此记录已删除" }
        val project = requireNotNull(dao.project(projectId)) { "请选择项目" }
        require(project.deletedAt == null || existing?.projectId == projectId) { "此项目已删除，请选择其他项目" }
        val effectiveSpans = spans ?: existing?.let { dao.segments(it.id).map { s -> TimeSpan(s.startDateTime, s.endDateTime) } }.orEmpty()
        val minutes = if (effectiveSpans.isEmpty()) null else WorkTime.minutes(date, effectiveSpans)
        if (effectiveSpans.isNotEmpty()) {
            val others = dao.exportRecords().filter { it.projectId == projectId && it.deletedAt == null && it.id != existing?.id }.map { it.id }.toSet()
            val combined = effectiveSpans + dao.exportSegments().filter { it.recordId in others }.map { TimeSpan(it.startDateTime, it.endDateTime) }
            val sorted = combined.sortedBy { java.time.LocalDateTime.parse(it.start) }
            sorted.zipWithNext().forEach { (a, b) -> require(java.time.LocalDateTime.parse(a.end) <= java.time.LocalDateTime.parse(b.start)) { "与此项目已有记录的时间段重叠，未保存" } }
        }
        require(!useTime || mode == BillingMode.HOUR && minutes != null) { "只有按小时并填写时间段时才能自动计费" }
        val billed = if (useTime) WorkTime.hours(minutes!!) else quantity
        val amount = Money.amount(billed, price, mode, existing?.unitPrice).toPlainString()
        val now = System.currentTimeMillis()
        val record = WorkRecordEntity(existing?.id ?: draftId ?: UUID.randomUUID().toString(), projectId, date, mode.name, unit.trim(),
            Money.quantity(billed, mode).toPlainString(), (if(price.trim() == existing?.unitPrice) Money.decimal(price,"历史单价") else Money.price(price)).toPlainString(), amount, note.trim(),
            existing?.createdAt ?: now, now, actualMinutes = minutes,
            quantitySource = if (useTime) "TIME" else "MANUAL", calculationPolicyVersion = existing?.calculationPolicyVersion ?: 1)
        // Keep future time segments and original duration when changing billed quantity.
        if (existing == null) dao.insertRecord(record) else check(dao.updateRecord(record) == 1)
        val previousSpans = existing?.let { dao.segments(it.id).map { s -> TimeSpan(s.startDateTime, s.endDateTime) } }.orEmpty()
        if (spans != null && spans != previousSpans) {
            dao.clearSegments(record.id)
            dao.insertSegments(spans.mapIndexed { index, span -> WorkSegmentEntity(UUID.randomUUID().toString(), record.id, index, span.start, span.end) })
        }
    }
    suspend fun deleteRecord(id: String) = db.withTransaction { check(dao.deleteRecord(id, System.currentTimeMillis()) == 1) { "记录已删除或不存在" } }
    suspend fun backup(): LedgerBackup = db.withTransaction {
        LedgerBackup(dao.exportProjects(), dao.exportRecords(), dao.exportSegments(), dao.exportPreferences()).also { it.validate() }
    }
    suspend fun restore(data: LedgerBackup) = db.withTransaction {
        data.validate()
        val current = backup()
        // Append new identities only. Never replace history with an older backup or clear tables.
        fun <T> checkConflicts(incoming: List<T>, local: List<T>, key: (T) -> String) {
            val indexed = local.associateBy(key)
            incoming.forEach { require(indexed[key(it)] == null || indexed[key(it)] == it) { "备份与现有记录冲突，未恢复、未清除数据。请在空记账本中恢复。" } }
        }
        checkConflicts(data.projects, current.projects) { it.id }
        checkConflicts(data.records, current.records) { it.id }
        checkConflicts(data.segments, current.segments) { it.id }
        val p = current.projects.map { it.id }.toSet(); val r = current.records.map { it.id }.toSet(); val s = current.segments.map { it.id }.toSet()
        data.projects.filter { it.id !in p }.forEach { dao.insertProject(it) }
        data.records.filter { it.id !in r }.forEach { dao.insertRecord(it) }
        dao.insertSegments(data.segments.filter { it.id !in s })
        val keys = current.preferences.map { it.key }.toSet()
        data.preferences.filter { it.key !in keys }.forEach { dao.preference(it) }
        val after = backup()
        val incomingIds = data.records.map { it.id }.toSet()
        require(after.records.filter { it.id in incomingIds }.toSet() == data.records.toSet()) { "恢复校验失败，操作已回滚" }
    }
    suspend fun saveBatch(projectId: String, dates: List<String>, mode: BillingMode, unit: String, quantity: String, price: String, note: String, spans: List<TimeSpan>, templateDate: String, useTime: Boolean, batchId: String) = db.withTransaction {
        require(dates.isNotEmpty() && dates.size <= 366 && dates.distinct().size == dates.size) { "请选择 1–366 个日期" }
        val incoming = dates.associateWith { WorkTime.shift(spans, templateDate, it) }
        val existing = dao.exportRecords().filter { it.deletedAt == null }
        val allSpans = dao.exportSegments().groupBy { it.recordId }
        if (spans.isNotEmpty()) {
            val combined = incoming.flatMap { (date, times) -> WorkTime.minutes(date, times); times } +
                existing.filter { it.projectId == projectId && !it.id.startsWith("$batchId:") }.flatMap { allSpans[it.id].orEmpty().map { s -> TimeSpan(s.startDateTime, s.endDateTime) } }
            val sorted = combined.sortedBy { it.start }
            sorted.zipWithNext().forEach { (a, b) -> require(a.end <= b.start) { "批量时间段与另一日期或已有记录重叠，未保存" } }
        }
        dates.forEach { date -> saveRecord(null, projectId, date, mode, unit, quantity, price, note, "$batchId:$date", incoming.getValue(date), useTime) }
    }
}

package com.easyledger.app.data.backup

import com.easyledger.app.data.local.*
import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.time.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.security.MessageDigest

data class LedgerBackup(val projects: List<ProjectEntity>, val records: List<WorkRecordEntity>, val segments: List<WorkSegmentEntity>, val preferences: List<PreferenceEntity>) {
    val activeRecords get() = records.filter { it.deletedAt == null }
    val total get() = Money.total(activeRecords.map { it.amount })
    fun validate() {
        require(projects.size <= 100000 && records.size <= 100000 && segments.size <= 500000) { "备份内容过大" }
        require(projects.map { it.id }.distinct().size == projects.size && records.map { it.id }.distinct().size == records.size && segments.map { it.id }.distinct().size == segments.size) { "备份存在重复标识" }
        require(preferences.map { it.key }.distinct().size == preferences.size) { "备份设置重复" }
        val projectIds = projects.map { it.id }.toSet(); val recordIds = records.map { it.id }.toSet()
        projects.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && it.defaultUnit.isNotBlank()) { "备份项目不完整" }; Money.decimal(it.defaultPrice, "历史单价") }
        records.forEach {
            require(it.id.isNotBlank() && it.projectId in projectIds && it.unit.isNotBlank()) { "备份记录缺少项目或单位" }
            LocalDate.parse(it.date)
            val quantity = Money.decimal(it.quantity, "历史数量")
            require(quantity.signum() > 0 && it.calculationPolicyVersion == 1) { "备份计费规则不支持" }
            val calculated = quantity.multiply(Money.decimal(it.unitPrice, "历史单价")).setScale(2, RoundingMode.HALF_UP)
            require(calculated.compareTo(Money.decimal(it.amount, "金额")) == 0) { "备份金额与数量、单价不一致，已拒绝恢复" }
            require(it.quantitySource in listOf("MANUAL", "TIME")) { "备份数量来源不支持" }
        }
        require(segments.all { it.recordId in recordIds } && segments.map { it.recordId to it.position }.distinct().size == segments.size) { "备份时间段关系错误" }
        val byRecord = segments.groupBy { it.recordId }
        byRecord.values.forEach { rows -> require(rows.map { it.position }.sorted() == rows.indices.toList()) { "备份时间段顺序错误" } }
        records.forEach { record ->
            val spans = byRecord[record.id].orEmpty().sortedBy { it.position }.map { TimeSpan(it.startDateTime, it.endDateTime) }
            if (spans.isNotEmpty()) require(WorkTime.minutes(record.date, spans) == record.actualMinutes) { "备份工时不一致" }
            else require(record.actualMinutes == null || record.actualMinutes == 0L) { "备份缺少原始时间段" }
        }
    }
}

object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun array(values: List<JSONObject>) = JSONArray().apply { values.forEach { put(it) } }
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
    fun encode(data: LedgerBackup): String {
        data.validate()
        val payload = obj(
            "schemaVersion" to 2,
            "projects" to array(data.projects.map { obj("id" to it.id, "name" to it.name, "defaultMode" to it.defaultMode, "defaultUnit" to it.defaultUnit, "defaultPrice" to it.defaultPrice, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt, "deletedAt" to it.deletedAt, "note" to it.note) }),
            "records" to array(data.records.map { obj("id" to it.id, "projectId" to it.projectId, "date" to it.date, "mode" to it.mode, "unit" to it.unit, "quantity" to it.quantity, "unitPrice" to it.unitPrice, "amount" to it.amount, "note" to it.note, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt, "deletedAt" to it.deletedAt, "actualMinutes" to it.actualMinutes, "quantitySource" to it.quantitySource, "calculationPolicyVersion" to it.calculationPolicyVersion) }),
            "segments" to array(data.segments.map { obj("id" to it.id, "recordId" to it.recordId, "position" to it.position, "startDateTime" to it.startDateTime, "endDateTime" to it.endDateTime) }),
            "preferences" to array(data.preferences.map { obj("key" to it.key, "value" to it.value) }),
            "activeTotal" to data.total,
        ).toString()
        val result = obj("format" to "easy-ledger-backup", "version" to 1, "payload" to payload, "sha256" to sha(payload)).toString()
        require(result.toByteArray().size <= MAX_BYTES) { "备份超过 32MB，请联系开发者；未删除数据" }
        return result
    }
    fun decode(text: String): LedgerBackup {
        require(text.toByteArray().size <= MAX_BYTES) { "备份超过 32MB" }
        val envelope = JSONObject(text)
        require(envelope.getString("format") == "easy-ledger-backup" && envelope.getInt("version") == 1) { "不是支持的记账备份" }
        val payload = envelope.getString("payload")
        require(sha(payload) == envelope.getString("sha256")) { "备份校验失败，文件可能损坏" }
        val root = JSONObject(payload)
        require(root.getInt("schemaVersion") == 2) { "备份数据库版本不支持" }
        fun rows(key: String): List<JSONObject> { val a = root.getJSONArray(key); return (0 until a.length()).map { a.getJSONObject(it) } }
        fun JSONObject.nullable(key: String): Long? = if (isNull(key)) null else getLong(key)
        val data = LedgerBackup(
            rows("projects").map { ProjectEntity(it.getString("id"), it.getString("name"), it.getString("defaultMode"), it.getString("defaultUnit"), it.getString("defaultPrice"), it.getLong("createdAt"), it.getLong("updatedAt"), it.nullable("deletedAt"), it.optString("note", "")) },
            rows("records").map { WorkRecordEntity(it.getString("id"), it.getString("projectId"), it.getString("date"), it.getString("mode"), it.getString("unit"), it.getString("quantity"), it.getString("unitPrice"), it.getString("amount"), it.getString("note"), it.getLong("createdAt"), it.getLong("updatedAt"), it.nullable("deletedAt"), it.nullable("actualMinutes"), it.getString("quantitySource"), it.getInt("calculationPolicyVersion")) },
            rows("segments").map { WorkSegmentEntity(it.getString("id"), it.getString("recordId"), it.getInt("position"), it.getString("startDateTime"), it.getString("endDateTime")) },
            rows("preferences").map { PreferenceEntity(it.getString("key"), it.getString("value")) },
        )
        data.validate()
        require(data.total == root.getString("activeTotal")) { "备份合计不一致" }
        return data
    }
}

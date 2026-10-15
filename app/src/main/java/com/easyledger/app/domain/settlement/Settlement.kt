package com.easyledger.app.domain.settlement

import com.easyledger.app.data.backup.LedgerBackup
import com.easyledger.app.domain.calculation.Money
import java.time.LocalDateTime

val defaultColumnNames = listOf("日期", "机型", "工作时间", "时长或数量", "单位", "单价", "金额", "备注")
data class SettlementRow(val cells: List<String>)
data class Settlement(val title: String, val period: String, val columns: List<String>, val rows: List<SettlementRow>, val total: String)

object Settlements {
    fun create(data: LedgerBackup, projectId: String?, start: String?, end: String?, names: List<String> = defaultColumnNames): Settlement {
        require(names.size == 8 && names.all { it.isNotBlank() && it.length <= 24 }) { "列名须填写完整，每列最多24字" }
        val projects = data.projects.associateBy { it.id }
        val spans = data.segments.groupBy { it.recordId }
        val records = data.activeRecords.filter { (projectId == null || it.projectId == projectId) && (start == null || it.date >= start) && (end == null || it.date <= end) }
            .sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))
        val rows = records.map { record ->
            val work = spans[record.id].orEmpty().sortedBy { it.position }.joinToString("\n") { segment ->
                val a = LocalDateTime.parse(segment.startDateTime); val b = LocalDateTime.parse(segment.endDateTime)
                "${a.toLocalTime()}–${if (b.toLocalDate() != a.toLocalDate()) "次日 " else ""}${b.toLocalTime()}"
            }
            SettlementRow(listOf(record.date, projects.getValue(record.projectId).name, work, record.quantity, record.unit, record.unitPrice, record.amount, record.note))
        }
        return Settlement(projectId?.let { projects[it]?.name } ?: "全部项目", "${start ?: "最早"} 至 ${end ?: "最新"}", names.toList(), rows, Money.total(records.map { it.amount }))
    }
}

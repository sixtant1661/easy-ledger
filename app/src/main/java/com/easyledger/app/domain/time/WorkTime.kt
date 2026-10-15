package com.easyledger.app.domain.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.math.BigDecimal
import java.math.RoundingMode

data class TimeSpan(val start: String, val end: String)

object WorkTime {
    fun minutes(date: String, spans: List<TimeSpan>): Long {
        val day = LocalDate.parse(date)
        val times = spans.map { span ->
            val start = LocalDateTime.parse(span.start)
            val end = LocalDateTime.parse(span.end)
            require(start.toLocalDate() == day) { "时间段须归属开始日期" }
            require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0) { "工时按整分钟记录" }
            require(end > start && end <= start.plusDays(1)) { "结束时间须晚于开始时间，每段不超过 24 小时" }
            start to end
        }.sortedBy { it.first }
        times.zipWithNext().forEach { (left, right) -> require(left.second <= right.first) { "工作时间段重叠，请修改；首尾相接允许" } }
        return times.sumOf { Duration.between(it.first, it.second).toMinutes() }
    }
    fun hours(minutes: Long): String = BigDecimal.valueOf(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP).toPlainString()
    fun dates(start: String, end: String): List<String> {
        val a = LocalDate.parse(start); val b = LocalDate.parse(end)
        require(b >= a) { "结束日期不能早于开始日期" }
        require(Duration.between(a.atStartOfDay(), b.atStartOfDay()).toDays() < 366) { "一次最多补录 366 天，请分批处理" }
        return generateSequence(a) { it.plusDays(1) }.takeWhile { it <= b }.map { it.toString() }.toList()
    }
    fun shift(spans: List<TimeSpan>, from: String, to: String): List<TimeSpan> {
        val offset = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to))
        return spans.map { TimeSpan(LocalDateTime.parse(it.start).plusDays(offset).toString(), LocalDateTime.parse(it.end).plusDays(offset).toString()) }
    }
}

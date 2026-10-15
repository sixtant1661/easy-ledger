package com.easyledger.app.domain
import com.easyledger.app.domain.time.*
import org.junit.Assert.*
import org.junit.Test

class WorkTimeTest {
    private val date = "2026-10-14"
    private fun span(a: String,b: String) = TimeSpan("${date}T$a", "${date}T$b")
    @Test fun multipleSegmentsAndTouchingEndpoints() {
        assertEquals(600L,WorkTime.minutes(date,listOf(span("07:00","11:00"),span("13:00","17:00"),span("18:00","20:00"))))
        assertEquals(120L,WorkTime.minutes(date,listOf(span("07:00","08:00"),span("08:00","09:00"))))
        assertEquals("10.00",WorkTime.hours(600))
        assertEquals("0.02",WorkTime.hours(1))
    }
    @Test fun crossMidnightStaysOnStartDate() {
        assertEquals(180L,WorkTime.minutes(date,listOf(TimeSpan("${date}T22:00","2026-10-15T01:00"))))
        val moved=WorkTime.shift(listOf(TimeSpan("${date}T22:00","2026-10-15T01:00")),date,"2026-10-31")
        assertEquals("2026-11-01T01:00",moved.single().end)
    }
    @Test fun nestedOverlappingOrInvalidTimesRejected() {
        listOf(listOf(span("07:00","11:00"),span("08:00","09:00")),listOf(span("07:00","07:00")),listOf(span("11:00","07:00"))).forEach {
            assertThrows(IllegalArgumentException::class.java) { WorkTime.minutes(date,it) }
        }
        assertThrows(IllegalArgumentException::class.java) { WorkTime.minutes(date,listOf(TimeSpan("2026-10-13T23:00","${date}T01:00"))) }
    }
    @Test fun batchDateRangeLimitsAndLeapDay() {
        assertEquals(listOf("2028-02-28","2028-02-29","2028-03-01"),WorkTime.dates("2028-02-28","2028-03-01"))
        assertThrows(IllegalArgumentException::class.java) { WorkTime.dates("2026-10-14","2026-10-13") }
        assertThrows(IllegalArgumentException::class.java) { WorkTime.dates("2026-01-01","2027-01-02") }
    }
}

package com.easyledger.app.domain

import com.easyledger.app.domain.calculation.Money
import com.easyledger.app.domain.model.BillingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test fun decimalCalculationAndHalfUp() {
        assertEquals("0.02", Money.amount("0.1", "0.2", BillingMode.DAY).toPlainString())
        assertEquals("1.01", Money.amount("1", "1.005", BillingMode.DAY).toPlainString())
        assertEquals("0.00", Money.amount("1", "0", BillingMode.HOUR).toPlainString())
    }
    @Test fun totalAddsRoundedRows() {
        val row = Money.amount("1", "0.005", BillingMode.DAY).toPlainString()
        assertEquals("0.02", Money.total(listOf(row, row)))
        assertEquals("0.00", Money.total(emptyList()))
    }
    @Test fun invalidInputsCannotProduceAmounts() {
        listOf("0", "-1", "1e3", "abc", "", "1,5").forEach { q ->
            assertThrows(IllegalArgumentException::class.java) { Money.amount(q, "1", BillingMode.HOUR) }
        }
        assertThrows(IllegalArgumentException::class.java) { Money.amount("1", "-0.1", BillingMode.DAY) }
        assertThrows(IllegalArgumentException::class.java) { Money.amount("1.001", "2", BillingMode.HOUR) }
        assertEquals("2.00", Money.amount("1.00", "2", BillingMode.HOUR).toPlainString())
        assertThrows(IllegalArgumentException::class.java) { Money.quantity("1.2300", BillingMode.HOUR) }
    }
}

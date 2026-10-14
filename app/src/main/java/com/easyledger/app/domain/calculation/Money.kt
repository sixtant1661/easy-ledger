package com.easyledger.app.domain.calculation

import java.math.BigDecimal
import java.math.RoundingMode
import com.easyledger.app.domain.model.BillingMode

object Money {
    fun decimal(text: String, label: String): BigDecimal {
        val value = text.trim()
        require(value.length <= 64 && value.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) { "$label 请填写有效的非负数字" }
        return BigDecimal(value)
    }
    fun quantity(text: String, mode: BillingMode): BigDecimal {
        val value = decimal(text, "数量")
        require(value.signum() > 0) { "数量必须大于 0" }
        require(mode != BillingMode.HOUR || value.scale() <= 2) { "计费小时最多保留两位小数" }
        return if (mode == BillingMode.HOUR) value.stripTrailingZeros() else value
    }
    fun amount(quantity: String, price: String, mode: BillingMode): BigDecimal =
        quantity(quantity, mode).multiply(decimal(price, "单价")).setScale(2, RoundingMode.HALF_UP)
    fun total(amounts: List<String>): String = amounts.fold(BigDecimal.ZERO) { sum, text -> sum + BigDecimal(text) }
        .setScale(2, RoundingMode.HALF_UP).toPlainString()
}

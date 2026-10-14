package com.easyledger.app.domain.model

enum class BillingMode(val label: String, val unit: String) {
    HOUR("按小时", "小时"), DAY("按天", "天"), TRIP("按次", "次"), PIECE("按件", "件"), CUSTOM("自定义单位", "单位");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: CUSTOM }
}

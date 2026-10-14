package com.easyledger.app.ui.navigation

enum class Destination(val route: String, val label: String) {
    HOME("home", "首页"),
    LEDGER("settlement", "账本"),
    PROJECTS("projects", "项目"),
    SETTINGS("settings", "我的"),
}

package com.easyledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.easyledger.app.ui.navigation.EasyLedgerApp
import com.easyledger.app.ui.theme.EasyLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EasyLedgerTheme { EasyLedgerApp() } }
    }
}

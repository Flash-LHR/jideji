package com.hackerli.jizhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hackerli.jizhang.ui.LedgerApp
import com.hackerli.jizhang.ui.theme.JiDeJiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JiDeJiTheme {
                LedgerApp()
            }
        }
    }
}

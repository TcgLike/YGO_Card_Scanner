package com.ygocardscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ygocardscanner.ui.InventoryApp
import com.ygocardscanner.ui.theme.YgoCardScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as YgoCardScannerApplication
        setContent {
            YgoCardScannerTheme {
                InventoryApp(app.appContainer)
            }
        }
    }
}

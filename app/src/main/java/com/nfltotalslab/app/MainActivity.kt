package com.nfltotalslab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nfltotalslab.app.ui.NflTotalsApp
import com.nfltotalslab.app.ui.NflTotalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NflTotalsTheme {
                NflTotalsApp(applicationContext)
            }
        }
    }
}

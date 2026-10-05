package com.jax.automation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jax.automation.ui.JaxApp
import com.jax.automation.ui.theme.JaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JaxTheme {
                JaxApp((application as JaxApplication).container)
            }
        }
    }
}

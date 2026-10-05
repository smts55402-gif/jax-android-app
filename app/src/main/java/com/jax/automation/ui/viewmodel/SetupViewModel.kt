package com.jax.automation.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.accessibility.JaxAccessibilityService
import com.jax.automation.di.AppContainer
import kotlinx.coroutines.launch

class SetupViewModel(private val container: AppContainer) : ViewModel() {

    var accessibilityOn by mutableStateOf(false)
    var chromeOk by mutableStateOf(false)
    var automationProbe by mutableStateOf<String?>(null)
    var chromeProbe by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    fun checkAll() {
        viewModelScope.launch {
            accessibilityOn = JaxAccessibilityService.isEnabled(container.app)
            chromeOk = container.chrome.isChromeInstalled()
        }
    }

    fun testAutomation() {
        viewModelScope.launch {
            busy = true
            automationProbe = try {
                val snap = container.automationAdapter.readScreen()
                "Screen readable: ${snap.texts.size} texts"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            busy = false
        }
    }

    fun testChrome() {
        viewModelScope.launch {
            busy = true
            chromeProbe = try {
                val r = container.chrome.openChrome()
                if (r.ok) "Chrome opened OK" else "Failed: ${r.message}"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            busy = false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

package io.github.lunasolcal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.screens.MainScreen
import io.github.lunasolcal.ui.theme.LunaYSolTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            LunaYSolTheme {
                MainScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.followNow) vm.resetToNow()
    }
}

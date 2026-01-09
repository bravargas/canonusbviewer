package com.brainer.canonusbviewer

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.brainer.canonusbviewer.ui.AppRoot
import com.brainer.canonusbviewer.viewmodel.ViewerViewModel
import com.brainer.canonusbviewer.viewmodel.ViewerViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: ViewerViewModel by viewModels { ViewerViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.post { applyImmersiveMode(true) }

        setContent {
            MaterialTheme {
                AppRoot(
                    viewModel = viewModel,
                    setImmersive = { enabled -> applyImmersiveMode(enabled) }
                )
            }
        }
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController
            if (controller == null) {
                window.decorView.post { applyImmersiveMode(enabled) }
                return
            }

            if (enabled) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (enabled) {
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                } else {
                    View.SYSTEM_UI_FLAG_VISIBLE
                }
        }
    }
}

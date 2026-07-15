package com.neversoft.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.neversoft.editor.ui.EditorViewModel
import com.neversoft.editor.ui.editor.EditorScreen
import com.neversoft.editor.ui.home.HomeScreen
import com.neversoft.editor.ui.theme.Bg
import com.neversoft.editor.ui.theme.NeverSoftTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NeverSoftTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    val vm: EditorViewModel = viewModel()
                    // vm.project is Compose state, so reading it here re-routes
                    // automatically the moment the first clip is imported.
                    if (vm.project.isEmpty) {
                        HomeScreen(vm)
                    } else {
                        EditorScreen(vm)
                    }
                }
            }
        }
    }
}

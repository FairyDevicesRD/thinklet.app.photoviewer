package com.example.fd.thinkletvision

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.fd.thinkletvision.ui.theme.ThinkletVisionTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThinkletVisionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Screen(modifier = Modifier.padding(innerPadding)) { viewModel.message(it) }
                }
            }
        }
        viewModel.setup(this.lifecycle)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.teardown(this.lifecycle)
    }
}

@Composable
fun Screen(modifier: Modifier = Modifier, getText: (ctx: Context) -> String) {
    val context = LocalContext.current
    Text(getText(context), modifier)
}

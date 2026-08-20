package com.example.stellare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.stellare.ui.StellareApp
import com.example.stellare.ui.StellareViewModel
import com.example.stellare.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StellareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    StellareApp(viewModel = viewModel)
                }
            }
        }
    }
}

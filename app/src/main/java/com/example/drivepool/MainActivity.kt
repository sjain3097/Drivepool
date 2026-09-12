package com.example.drivepool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivepool.data.repository.DrivePoolRepository
import com.example.drivepool.theme.DrivePoolTheme
import com.example.drivepool.ui.main.DrivePoolApp
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      DrivePoolTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val repository = remember { DrivePoolRepository(applicationContext) }
          val viewModel: DrivePoolViewModel = viewModel { DrivePoolViewModel(repository) }
          DrivePoolApp(viewModel = viewModel)
        }
      }
    }
  }
}

package com.example.drivepool.ui.main

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.example.drivepool.ui.screens.GoogleAuthSetupDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivepool.ui.screens.ClusterDashboardScreen
import com.example.drivepool.ui.screens.FileOrganizerScreen
import com.example.drivepool.ui.screens.LocalPhoneFilesScreen
import com.example.drivepool.ui.screens.NodesScreen
import com.example.drivepool.ui.screens.UnifiedFilesScreen
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DRIVE("Cloud", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    ORGANIZER("Organizer", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial),
    PHONE("Phone", Icons.Filled.PhoneAndroid, Icons.Outlined.PhoneAndroid),
    CLUSTER("Cluster", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    ACCOUNTS("Accounts", Icons.Filled.ManageAccounts, Icons.Outlined.ManageAccounts)
}

@Composable
fun DrivePoolApp(
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onConsentCompleted(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.authConsentIntent) {
        uiState.authConsentIntent?.let { intent ->
            try {
                consentLauncher.launch(intent)
            } catch (e: Exception) {
                // Handle launcher failure
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> UnifiedFilesScreen(
                    state = uiState,
                    viewModel = viewModel,
                    onSwitchToPhone = { currentTab = 2 }
                )
                1 -> FileOrganizerScreen(
                    state = uiState,
                    viewModel = viewModel
                )
                2 -> LocalPhoneFilesScreen(
                    state = uiState,
                    viewModel = viewModel
                )
                3 -> ClusterDashboardScreen(
                    state = uiState,
                    viewModel = viewModel
                )
                4 -> NodesScreen(
                    state = uiState,
                    viewModel = viewModel
                )
            }

            if (uiState.showAuthSetupDialog) {
                GoogleAuthSetupDialog(
                    errorMessage = uiState.authErrorMessage,
                    onDismiss = { viewModel.dismissAuthSetupDialog() }
                )
            }
        }
    }
}

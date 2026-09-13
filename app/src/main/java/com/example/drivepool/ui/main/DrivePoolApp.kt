package com.example.drivepool.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import com.example.drivepool.ui.components.ExitConfirmationDialog
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.drivepool.ui.screens.ClusterDashboardScreen
import com.example.drivepool.ui.screens.FileOrganizerScreen
import com.example.drivepool.ui.screens.LocalPhoneFilesScreen
import com.example.drivepool.ui.screens.NodesScreen
import com.example.drivepool.ui.screens.UnifiedFilesScreen
import com.example.drivepool.ui.screens.viewer.FileViewerDialog
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DRIVE("Cloud", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    PHONE("Phone", Icons.Filled.PhoneAndroid, Icons.Outlined.PhoneAndroid),
    ACCOUNTS("Accounts", Icons.Filled.ManageAccounts, Icons.Outlined.ManageAccounts)
}

@Composable
fun DrivePoolApp(
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Root Back button handling: prompt for confirmation before exiting
    val hasInnerBackHandling = uiState.viewerSession != null ||
            uiState.viewingTarget != null ||
            uiState.showAuthSetupDialog ||
            uiState.selectedPhoneFileIds.isNotEmpty() ||
            uiState.selectedFileIds.isNotEmpty() ||
            uiState.selectedPhoneFile != null ||
            uiState.selectedFileForDetails != null ||
            (currentTab == 0 && uiState.cloudSubTab == com.example.drivepool.ui.viewmodel.CloudSubTab.ORGANIZER && uiState.selectedCategoryFolder != null) ||
            (currentTab == 1 && uiState.phoneSubTab == com.example.drivepool.ui.viewmodel.PhoneSubTab.ORGANIZER && uiState.selectedPhoneCategoryFolder != null) ||
            (currentTab == 1 && uiState.isFolderViewMode && uiState.currentFolderResult?.parentPath != null)

    BackHandler(enabled = !hasInnerBackHandling) {
        showExitDialog = true
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    onSwitchToPhone = { currentTab = 1 }
                )
                1 -> LocalPhoneFilesScreen(
                    state = uiState,
                    viewModel = viewModel
                )
                2 -> NodesScreen(
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

            uiState.viewerSession?.let { session ->
                FileViewerDialog(
                    session = session,
                    onDismiss = { viewModel.dismissFileViewer() },
                    onPrepareFile = { item -> viewModel.prepareFileForViewer(item) },
                    onDelete = { item -> viewModel.deleteViewerItem(item) }
                )
            } ?: uiState.viewingTarget?.let { target ->
                FileViewerDialog(
                    target = target,
                    onDismiss = { viewModel.dismissFileViewer() },
                    onDelete = { item -> viewModel.deleteViewerItem(item) }
                )
            }

            if (uiState.isViewerLoading) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = uiState.statusMessage ?: "Loading file...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (showExitDialog) {
                ExitConfirmationDialog(
                    onDismiss = { showExitDialog = false },
                    onConfirmExit = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    }
                )
            }
        }
    }
}

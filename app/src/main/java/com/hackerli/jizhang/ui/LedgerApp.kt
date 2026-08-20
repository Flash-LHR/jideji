package com.hackerli.jizhang.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerli.jizhang.BuildConfig
import com.hackerli.jizhang.data.ExportProgress
import com.hackerli.jizhang.data.FullExporter
import com.hackerli.jizhang.data.LedgerEvent
import com.hackerli.jizhang.data.LedgerViewModel
import com.hackerli.jizhang.data.LocationSnapshot
import com.hackerli.jizhang.data.PhotoStorage
import com.hackerli.jizhang.data.TagImageStorage
import com.hackerli.jizhang.data.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private enum class MainTab { RECORD, HISTORY, SETTINGS }
private enum class AppRoute { MAIN, ENTRY, DETAIL, EDIT, REFUND, TAGS }

@Composable
fun LedgerApp(viewModel: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val operationInFlight by viewModel.operationInFlight.collectAsStateWithLifecycle()
    val latestExpenses by rememberUpdatedState(expenses)
    val latestTags by rememberUpdatedState(allTags)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAttempted by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MainTab.RECORD) }
    var route by remember { mutableStateOf(AppRoute.MAIN) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var selectedExpenseId by remember { mutableStateOf<Long?>(null) }
    var selectedLocation by remember { mutableStateOf<LocationSnapshot?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<ExportProgress?>(null) }
    var initialResumeHandled by remember { mutableStateOf(false) }
    var skipNextResumeUpdate by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionAttempted = true
        permissionGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (permissionGranted) viewModel.refreshLocation() else viewModel.requireLocationPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!initialResumeHandled) {
                    initialResumeHandled = true
                    skipNextResumeUpdate = false
                } else if (skipNextResumeUpdate) {
                    skipNextResumeUpdate = false
                } else {
                    viewModel.checkForUpdate()
                }
                permissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (permissionGranted) viewModel.refreshLocation() else viewModel.requireLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted, permissionAttempted) {
        if (!permissionGranted && !permissionAttempted) {
            skipNextResumeUpdate = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    LaunchedEffect(tab, route, permissionGranted) {
        if (permissionGranted && tab == MainTab.RECORD && route == AppRoute.MAIN) viewModel.refreshLocation()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LedgerEvent.ExpenseSaved -> {
                    route = AppRoute.MAIN
                    tab = MainTab.RECORD
                    selectedTagId = null
                    selectedLocation = null
                    viewModel.refreshLocation()
                }
                LedgerEvent.ExpenseUpdated -> route = AppRoute.DETAIL
                LedgerEvent.RefundSaved -> route = AppRoute.DETAIL
                LedgerEvent.RefundDeleted -> Unit
                LedgerEvent.ExpenseDeleted -> {
                    selectedExpenseId = null
                    route = AppRoute.MAIN
                    tab = MainTab.HISTORY
                }
                is LedgerEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            exporting = true
            exportProgress = ExportProgress(
                0,
                latestExpenses.sumOf { it.photos.size } + latestTags.count { it.imagePath != null },
            )
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            FullExporter.write(output, latestExpenses, latestTags, ZoneId.systemDefault()) { progress ->
                                scope.launch { exportProgress = progress }
                            }
                        } ?: error("无法创建导出文件")
                    }
                }
                if (result.isFailure) {
                    withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                }
                exporting = false
                exportProgress = null
                if (result.isFailure) snackbar.showSnackbar("导出失败，请重试")
            }
        }
    }

    DisposableEffect(exporting) {
        if (exporting) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (!permissionGranted) {
        FineLocationGate(
            onGrant = {
                val permanentlyDenied = permissionAttempted && !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
                if (permanentlyDenied) {
                    skipNextResumeUpdate = true
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                } else {
                    skipNextResumeUpdate = true
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                    )
                }
            },
        )
        return
    }

    val updateReady = updateState as? UpdateState.Ready
    val updateCanInterrupt = route !in setOf(AppRoute.ENTRY, AppRoute.EDIT, AppRoute.REFUND) && !operationInFlight && !exporting
    if (updateReady != null && updateCanInterrupt) {
        UpdateRequiredScreen(updateReady.versionName, updateReady.changelog) {
            viewModel.updateInstallIntent()?.let {
                skipNextResumeUpdate = true
                context.startActivity(it)
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route == AppRoute.MAIN) {
                AppNavigationBar(tab) {
                    tab = it
                    if (tab == MainTab.RECORD) viewModel.refreshLocation()
                }
            }
        },
    ) { innerPadding ->
        when (route) {
            AppRoute.MAIN -> when (tab) {
                MainTab.RECORD -> TagSelectionScreen(
                    tags = tags,
                    expenses = expenses,
                    locationState = locationState,
                    modifier = Modifier.padding(innerPadding),
                    onRetryLocation = viewModel::refreshLocation,
                    onAddTag = viewModel::addTag,
                    onLaunchExternalActivity = { skipNextResumeUpdate = true },
                    onSelect = { tag, location ->
                        selectedTagId = tag.id
                        selectedLocation = location
                        route = AppRoute.ENTRY
                    },
                )
                MainTab.HISTORY -> HistoryScreen(
                    expenses = expenses,
                    modifier = Modifier.padding(innerPadding),
                    onOpenExpense = { selectedExpenseId = it; route = AppRoute.DETAIL },
                )
                MainTab.SETTINGS -> SettingsScreen(
                    photoBytes = PhotoStorage.totalBytes(expenses.flatMap { it.photos }.map { it.path }) +
                        TagImageStorage.totalBytes(context),
                    versionName = BuildConfig.VERSION_NAME,
                    updateText = updateState.displayText(),
                    modifier = Modifier.padding(innerPadding),
                    onOpenTags = { route = AppRoute.TAGS },
                    onExport = {
                        skipNextResumeUpdate = true
                        exportLauncher.launch("记得记完整导出-${LocalDate.now()}.zip")
                    },
                    onCheckUpdate = viewModel::checkForUpdate,
                )
            }
            AppRoute.ENTRY -> {
                val tag = tags.firstOrNull { it.id == selectedTagId }
                val location = selectedLocation
                if (tag != null && location != null) {
                    EntryScreen(
                        tag = tag,
                        location = location,
                        expenses = expenses,
                        operationInFlight = operationInFlight,
                        modifier = Modifier.padding(innerPadding),
                        onLaunchExternalActivity = { skipNextResumeUpdate = true },
                        onBack = { route = AppRoute.MAIN; selectedTagId = null; selectedLocation = null },
                        onRecord = { amount, note, photos ->
                            viewModel.record(amount, tag, note, photos, location)
                        },
                    )
                }
            }
            AppRoute.DETAIL -> expenses.firstOrNull { it.id == selectedExpenseId }?.let { expense ->
                ExpenseDetailScreen(
                    expense = expense,
                    operationInFlight = operationInFlight,
                    modifier = Modifier.padding(innerPadding),
                    onBack = { route = AppRoute.MAIN; tab = MainTab.HISTORY },
                    onEdit = { route = AppRoute.EDIT },
                    onRefundRemaining = { viewModel.refundRemaining(expense.id) },
                    onRecordRefund = { route = AppRoute.REFUND },
                    onDeleteRefund = viewModel::deleteRefund,
                    onDeleteExpense = { viewModel.deleteExpense(expense.id) },
                )
            }
            AppRoute.EDIT -> expenses.firstOrNull { it.id == selectedExpenseId }?.let { expense ->
                EditExpenseScreen(
                    expense = expense,
                    tags = buildList {
                        addAll(tags)
                        allTags.firstOrNull { it.id == expense.tagId && it.isArchived }?.let(::add)
                    },
                    operationInFlight = operationInFlight,
                    modifier = Modifier.padding(innerPadding),
                    onLaunchExternalActivity = { skipNextResumeUpdate = true },
                    onCancel = { route = AppRoute.DETAIL },
                    onSave = { amount, tag, note, photos ->
                        viewModel.updateExpense(expense, amount, tag, note, photos)
                    },
                )
            }
            AppRoute.REFUND -> expenses.firstOrNull { it.id == selectedExpenseId }?.let { expense ->
                RefundScreen(
                    expense = expense,
                    operationInFlight = operationInFlight,
                    modifier = Modifier.padding(innerPadding),
                    onBack = { route = AppRoute.DETAIL },
                    onConfirm = { viewModel.addRefund(expense.id, it) },
                )
            }
            AppRoute.TAGS -> TagManagementScreen(
                allTags = allTags,
                modifier = Modifier.padding(innerPadding),
                onBack = { route = AppRoute.MAIN; tab = MainTab.SETTINGS },
                onAdd = viewModel::addTag,
                onUpdate = viewModel::updateTag,
                onArchive = viewModel::setTagArchived,
                onMove = viewModel::moveTag,
                onLaunchExternalActivity = { skipNextResumeUpdate = true },
            )
        }
    }

    if (exporting) ExportOverlay(exportProgress)
}

@Composable
private fun AppNavigationBar(current: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(current == MainTab.RECORD, { onSelect(MainTab.RECORD) }, { Text("记账") })
        NavigationBarItem(current == MainTab.HISTORY, { onSelect(MainTab.HISTORY) }, { Text("账单") })
        NavigationBarItem(current == MainTab.SETTINGS, { onSelect(MainTab.SETTINGS) }, { Text("设置") })
    }
}

@Composable
private fun FineLocationGate(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("记得记需要精确位置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("授予精确位置") }
        }
    }
}

@Composable
private fun UpdateRequiredScreen(version: String, changelog: List<String>, onInstall: () -> Unit) {
    androidx.activity.compose.BackHandler { }
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "发现新版本 v${version.removePrefix("v")}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (changelog.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("本次更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    changelog.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = item,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("安装更新") }
        }
    }
}

@Composable
private fun ExportOverlay(progress: ExportProgress?) {
    androidx.activity.compose.BackHandler { }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f)) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (progress == null || progress.totalImages == 0) "正在导出账单"
                        else "正在导出 ${progress.completedImages} / ${progress.totalImages} 张图片",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun UpdateState.displayText(): String = when (this) {
    UpdateState.Disabled -> "未配置更新仓库"
    UpdateState.Idle -> "已是最新版本"
    UpdateState.Checking -> "正在检查"
    is UpdateState.Downloading -> percent?.let { "正在下载 $it%" } ?: "正在下载"
    is UpdateState.Ready -> "新版本 $versionName 已下载"
    is UpdateState.Error -> message
}

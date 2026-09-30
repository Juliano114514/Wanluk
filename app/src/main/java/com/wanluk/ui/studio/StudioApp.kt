package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton

import com.wanluk.libcomposeui.AppDialog

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.libroom.entity.SurveySessionEntity
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libcomposeui.ActionSymbol
import com.wanluk.libcomposeui.ActionToast
import com.wanluk.libcomposeui.ActionToastHost
import com.wanluk.libcomposeui.rememberActionToastHostState
import com.wanluk.libcomposeui.AppSnackbar
import com.wanluk.libcomposeui.EmptyState
import com.wanluk.libsettings.RecordingMode
import com.wanluk.libsettingsui.RecorderSettingsScreen
import com.wanluk.foundation.survey.RecordingTaskSnapshot
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioApp(viewModel: StudioViewModel, lifecycle: Lifecycle, transferViewModel: SurveyTransferViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val packages by viewModel.packages.collectAsStateWithLifecycle()
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val detail by viewModel.detail.collectAsStateWithLifecycle()
  val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
  val recordingTask by viewModel.recordingTask.collectAsStateWithLifecycle()
  val skippedItem by viewModel.skippedItem.collectAsStateWithLifecycle()
  val noteDraft by viewModel.noteDraft.collectAsStateWithLifecycle()
  val capture by viewModel.capture.collectAsStateWithLifecycle()
  val trial by viewModel.trial.collectAsStateWithLifecycle()
  val playing by viewModel.playing.collectAsStateWithLifecycle()
  val exported by viewModel.lastExport.collectAsStateWithLifecycle()
  val recorderSettings by viewModel.recorderSettings.collectAsStateWithLifecycle()
  val transfer by transferViewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val view = LocalView.current
  val snackbar = remember { SnackbarHostState() }
  val toast = rememberActionToastHostState()
  val scope = rememberCoroutineScope()
  var startTask by remember { mutableStateOf<SurveyPackage?>(null) }
  var startDefault by rememberSaveable { mutableStateOf(false) }
  var skipReasonItem by remember { mutableStateOf<SkippedItem?>(null) }
  var discardDraft by remember { mutableStateOf(false) }
  var permissionDenied by remember { mutableStateOf(false) }
  var pendingTrial by rememberSaveable { mutableStateOf(false) }
  var pendingHold by rememberSaveable { mutableStateOf(false) }
  var finishedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
  var homeSection by rememberSaveable { mutableStateOf("landing") }
  var adminTab by rememberSaveable { mutableIntStateOf(0) }
  var recordings by rememberSaveable { mutableStateOf(false) }
  var managedSession by remember { mutableStateOf<SurveySessionEntity?>(null) }

  fun startRecordingTask(task: SurveyPackage) {
    if (recorderSettings.shouldAsk) startTask = task
    else viewModel.startSession(task, recorderSettings)
  }
  fun startDefaultRecording() {
    if (recorderSettings.shouldAsk) startDefault = true
    else viewModel.startDefaultSession(recorderSettings)
  }

  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::importTask)
  }
  val taskExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::exportTask)
  fun showExportFailure(message: String) {
    scope.launch { toast.show(ActionToast.Builder(message).duration(3_000L).setY(12.dp).build()) }
  }
  val resultExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    viewModel.exportResult(uri, ::showExportFailure)
  }
  fun exportRecording(id: String) {
    viewModel.prepareResultExport(id, onReady = { resultExportLauncher.launch(it) }, onFailure = ::showExportFailure)
  }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (!granted) permissionDenied = true
    else if (pendingHold) viewModel.notify("已允许麦克风，请重新长按录音")
    else viewModel.startRecording(pendingTrial)
    pendingHold = false
  }
  fun requestRecording(isTrial: Boolean, held: Boolean = false) {
    pendingTrial = isTrial
    pendingHold = held
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
      viewModel.startRecording(isTrial)
    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
  }
  fun back() {
    when {
      capture != null -> viewModel.notify("请先停止录音")
      state.busy != null -> Unit
      state.screen == StudioScreen.EDITOR -> if (state.draftChanged) discardDraft = true else viewModel.home()
      state.screen == StudioScreen.LIBRARY -> viewModel.leaveLibrary()
      state.screen == StudioScreen.HOME -> homeSection = "landing"
      else -> viewModel.home()
    }
  }
  BackHandler(state.screen != StudioScreen.HOME || homeSection != "landing" || capture != null) { back() }
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.background() }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  DisposableEffect(capture != null, view) {
    val previous = view.keepScreenOn
    if (capture != null) view.keepScreenOn = true
    onDispose { view.keepScreenOn = previous }
  }
  LaunchedEffect(state.message) {
    state.message?.let { snackbar.showSnackbar(it, withDismissAction = true); viewModel.clearMessage() }
  }
  LaunchedEffect(skippedItem, state.screen) {
    val skipped = skippedItem ?: return@LaunchedEffect
    if (state.screen == StudioScreen.SESSION && skipped.sessionId == selectedSessionId) {
      if (toast.show(ActionToast.Builder("已跳过：${skipped.text}").action("填写原因").setY(12.dp).build())) {
        skipReasonItem = skipped
      }
    }
    viewModel.clearSkippedItem(skipped)
  }

  Box(Modifier.fillMaxSize()) {
  Scaffold(
    topBar = {
      if (state.screen != StudioScreen.HOME || homeSection != "landing") {
      TopAppBar(title = { Text(when (state.screen) {
        StudioScreen.HOME -> if (homeSection == "admin") "管理员" else "开始录制"
        StudioScreen.EDITOR -> "编辑调查包"
        StudioScreen.LIBRARY -> if (state.pickingWords) "选择字目" else "字库"
        StudioScreen.SESSION -> "韵录"
        StudioScreen.SETTINGS -> "设置"
      }) }, navigationIcon = {
        IconButton(onClick = ::back) { ActionIcon(ActionSymbol.BACK, contentDescription = "返回") }
      }, actions = {
        if (state.screen == StudioScreen.HOME) {
          IconButton(onClick = viewModel::settings, enabled = state.ready && state.busy == null) {
            ActionIcon(ActionSymbol.SETTINGS, Modifier.size(24.dp), contentDescription = "设置")
          }
        }
      }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
      }
    }, snackbarHost = { SnackbarHost(snackbar) { AppSnackbar(it) } },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      val enabled = state.ready && state.busy == null && capture == null
      if (!state.ready) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("正在初始化…")
          if (state.busy == null) Button(onClick = viewModel::initialize) { Text("重试") }
        }
      } else when (state.screen) {
        StudioScreen.HOME -> StudioHome(packages, sessions, enabled, homeSection, { homeSection = it },
          onSettings = viewModel::settings,
          adminTab = adminTab, onAdminTab = { adminTab = it }, onDefault = ::startDefaultRecording,
          recordings = recordings, onRecordings = { recordings = it },
          onStart = ::startRecordingTask, onResume = viewModel::resume, onManageSession = { managedSession = it },
          onEdit = { viewModel.edit(it) }, onCopy = { viewModel.edit(it, copy = true) },
          onNew = viewModel::newPackage, onImport = { importLauncher.launch(arrayOf("*/*")) },
          onImportQr = transferViewModel::openImport, onExportQr = transferViewModel::generate,
          libraryContent = { active -> WordLibrary(false, enabled, viewModel::addWords,
            onCreate = viewModel::createPackage, active = active) },
          onExport = { taskExportLauncher.launch(viewModel.prepareTaskExport(it)) })
        StudioScreen.SETTINGS -> RecorderSettingsScreen(recorderSettings, enabled,
          viewModel::saveRecorderSettings, viewModel::saveRecordingMode, viewModel::saveThemeMode)
        StudioScreen.EDITOR -> state.draft?.let { draft ->
          SurveyEditor(draft, enabled, viewModel::updateDraft, { viewModel.library(picking = true) }, viewModel::saveDraft)
        }
        StudioScreen.LIBRARY -> WordLibrary(state.pickingWords, enabled, viewModel::addWords,
          creating = state.creatingPackage,
          maxSelection = minOf(SurveyPackage.MAX_ITEMS - (state.draft?.items?.size ?: 0),
            SurveyPackage.MAX_STEPS - (state.draft?.totalSteps ?: 0)).coerceAtLeast(0),
          onCreate = viewModel::createPackage)
        StudioScreen.SESSION -> detail?.takeIf { it.session.id == selectedSessionId && recordingTask?.sessionId == it.session.id }?.let { current ->
          key(current.session.id) {
            RecordingScreen(current, requireNotNull(recordingTask).task, enabled, capture, viewModel.meter, trial, playing,
              onRecord = { held -> requestRecording(false, held) }, onTrial = { requestRecording(true) },
              holdToRecord = recorderSettings.recordingMode == RecordingMode.HOLD,
              onStop = viewModel::stopRecording, onPlay = { viewModel.play(it) },
              onPlayTrial = { viewModel.play(isTrial = true) }, onStopPlayback = viewModel::stopPlayback,
              onPosition = viewModel::position, onSkip = viewModel::skip, onNote = viewModel::note,
              onSkipReason = viewModel::saveSkipReason,
              noteDraft = noteDraft, onNoteChange = viewModel::changeNote,
              onAdopt = viewModel::adopt, busy = state.busy, toastHost = { ActionToastHost(toast) },
              onFinish = { viewModel.finishSession {
                homeSection = "record"
                recordings = true
                finishedSessionId = current.session.id
              } },
              onExport = { exportRecording(current.session.id) })
          }
        } ?: Box(Modifier.padding(24.dp)) { Text("正在载入录制任务…") }
      }
      // Overlay progress to avoid moving fields/cards while a short save is in flight.
      if (state.busy != null && state.screen != StudioScreen.SESSION) {
        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
      }
    }
  }
  if (state.screen != StudioScreen.SESSION) ActionToastHost(toast, Modifier.fillMaxSize().systemBarsPadding())
  }

  LaunchedEffect(state.screen) {
    if (state.screen == StudioScreen.SESSION) { startTask = null; startDefault = false }
    else { skipReasonItem = null; toast.dismiss() }
  }
  finishedSessionId?.let { id ->
    AppDialog(onDismissRequest = { if (state.busy == null) finishedSessionId = null },
      symbol = ActionSymbol.FINISH, title = { Text("是否导出？") }, text = { Text("进度已保存，可导出录音 ZIP。") },
      confirmButton = { Button(onClick = { finishedSessionId = null; exportRecording(id) },
        enabled = state.busy == null) { Text("立即导出") } },
      dismissButton = { TextButton(onClick = { finishedSessionId = null }, enabled = state.busy == null) { Text("稍后") } })
  }
  managedSession?.let { session -> RecordingSessionDialog(session, state.busy == null,
    onDismiss = { managedSession = null },
    onSave = { title, alias, dialect ->
      viewModel.updateSessionInfo(session.id, title, alias, dialect) { managedSession = null }
    },
    onDelete = { viewModel.deleteSession(session.id) { managedSession = null } },
    onExport = { managedSession = null; exportRecording(session.id) }) }
  skipReasonItem?.let { skipped -> SkipReasonDialog(skipped, state.busy == null && capture == null,
    onDismiss = { skipReasonItem = null }) { reason ->
    viewModel.saveSkipReason(skipped, reason)
    skipReasonItem = null
  } }
  if (startDefault) SessionSetupDialog(
    remember { SurveyPackage(packageId = RecordingTaskSnapshot.LIBRARY_PACKAGE_ID, title = "默认方案 · 完整字库") },
    recorderSettings, state.busy == null, onDismiss = { startDefault = false }, onStart = viewModel::startDefaultSession)
  startTask?.let { task -> SessionSetupDialog(task, recorderSettings, state.busy == null,
    onDismiss = { startTask = null }) { profile ->
    viewModel.startSession(task, profile)
  } }
  SurveyTransferDialogs(transferViewModel, transfer,
    onExportJson = { taskExportLauncher.launch(viewModel.prepareTaskExport(it)) })
  if (discardDraft) AppDialog(onDismissRequest = { discardDraft = false },
    symbol = ActionSymbol.EDIT, title = { Text("离开编辑器？") }, text = { Text("未保存的修改将丢失。") },
    confirmButton = { Button(onClick = { discardDraft = false; viewModel.home() }) { Text("放弃修改") } },
    dismissButton = { TextButton(onClick = { discardDraft = false }) { Text("继续编辑") } })
  if (permissionDenied) AppDialog(onDismissRequest = { permissionDenied = false },
    symbol = ActionSymbol.MIC, title = { Text("需要麦克风权限") }, text = { Text("请在系统设置中允许使用麦克风。") },
    confirmButton = { Button(onClick = {
      permissionDenied = false
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }) { Text("打开设置") } }, dismissButton = { TextButton(onClick = { permissionDenied = false }) { Text("稍后") } })
  fun clearExport() { viewModel.clearExport(); transferViewModel.clearExport() }
  (transfer.exported ?: exported)?.let { document -> AppDialog(onDismissRequest = ::clearExport,
    symbol = ActionSymbol.FINISH, title = { Text("文件已导出") }, text = { Text("已保存到所选位置。") },
    confirmButton = { Button(onClick = {
      try {
        val intent = Intent(Intent.ACTION_SEND).apply {
          type = document.mime
          putExtra(Intent.EXTRA_STREAM, document.uri)
          clipData = ClipData.newRawUri("韵录导出文件", document.uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享导出文件"))
        clearExport()
      } catch (_: Exception) { clearExport(); viewModel.notify("无法打开分享面板；文件已导出，可从文件管理器转交") }
    }) { Text("分享文件") } },
    dismissButton = { TextButton(onClick = ::clearExport) { Text("完成") } }) }
}

@Composable
private fun StudioHome(
  packages: List<SurveyPackage>, sessions: List<SurveySessionEntity>, enabled: Boolean,
  section: String, onSection: (String) -> Unit,
  onSettings: () -> Unit,
  adminTab: Int, onAdminTab: (Int) -> Unit, onDefault: () -> Unit,
  recordings: Boolean, onRecordings: (Boolean) -> Unit,
  onStart: (SurveyPackage) -> Unit, onResume: (String) -> Unit, onEdit: (SurveyPackage) -> Unit,
  onManageSession: (SurveySessionEntity) -> Unit,
  onCopy: (SurveyPackage) -> Unit, onNew: () -> Unit, onImport: () -> Unit,
  libraryContent: @Composable (Boolean) -> Unit,
  onExport: (SurveyPackage) -> Unit,
  onImportQr: () -> Unit, onExportQr: (SurveyPackage) -> Unit,
) {
  if (section == "landing") {
    StudioLanding(enabled, onStart = { onSection("record") }, onAdmin = { onSection("admin") }, onSettings = onSettings)
    return
  }
  val admin = section == "admin"
  var menu by remember { mutableStateOf(false) }
  val pager = key(section) {
    rememberPagerState(initialPage = if (admin) adminTab else if (recordings) 1 else 0, pageCount = { 2 })
  }
  val scope = rememberCoroutineScope()
  LaunchedEffect(admin, pager.currentPage) {
    if (admin) onAdminTab(pager.currentPage) else onRecordings(pager.currentPage == 1)
  }
  Column(Modifier.fillMaxSize()) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      TabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.weight(1f),
        containerColor = MaterialTheme.colorScheme.background) {
        (if (admin) listOf("调查包", "字库") else listOf("录制方案", "我的录制")).forEachIndexed { index, label ->
          Tab(selected = pager.currentPage == index, enabled = enabled,
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(label) })
        }
      }
      Box {
        MoreButton(enabled, onClick = { menu = true })
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.QR) }, text = { Text("二维码导入") }, onClick = { menu = false; onImportQr() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.IMPORT) }, text = { Text("导入 JSON 文件") }, onClick = { menu = false; onImport() })
        }
      }
    }
    HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = enabled,
      verticalAlignment = Alignment.Top) { page ->
      if (admin) {
        if (page == 0) Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
          SurveyPackageList(packages, enabled, true, onStart, onEdit, onCopy, onExportQr, onExport, Modifier.weight(1f))
          Button(onClick = onNew, enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp)) {
            ActionIcon(ActionSymbol.ADD); Spacer(Modifier.width(8.dp)); Text("新建调查包")
          }
        } else libraryContent(pager.currentPage == 1)
      } else if (page == 1) RecordingList(sessions, enabled, onResume, onManageSession)
      else SurveyPackageList(packages, enabled, false, onStart, onEdit, onCopy, onExportQr, onExport,
        Modifier.fillMaxSize().padding(horizontal = 16.dp), onDefault = onDefault)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingList(sessions: List<SurveySessionEntity>, enabled: Boolean,
  onResume: (String) -> Unit, onManage: (SurveySessionEntity) -> Unit) {
      LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (sessions.isEmpty()) item {
          EmptyState(ActionSymbol.RECORDINGS, "暂无录制", "从「录制方案」开始")
        }
        items(sessions, key = { it.id }) { session ->
          Surface(color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
            onClickLabel = "打开录制", onClick = { onResume(session.id) },
            onLongClickLabel = "管理录制", onLongClick = { onManage(session) })) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                  maxLines = 2, overflow = TextOverflow.Ellipsis)
                MoreButton(enabled, onClick = { onManage(session) })
              }
              Text(listOf(session.speakerAlias, session.dialect).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未填写录制者信息" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              LinearProgressIndicator(progress = { session.completedSteps.toFloat() / session.totalSteps.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${session.completedSteps} / ${session.totalSteps}", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(if (session.completedSteps == session.totalSteps) "查看录制" else "继续",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                  ActionIcon(ActionSymbol.NEXT, Modifier.size(18.dp))
                }
              }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SurveyPackageList(packages: List<SurveyPackage>, enabled: Boolean, admin: Boolean,
  onStart: (SurveyPackage) -> Unit, onEdit: (SurveyPackage) -> Unit, onCopy: (SurveyPackage) -> Unit,
  onExportQr: (SurveyPackage) -> Unit, onExport: (SurveyPackage) -> Unit, modifier: Modifier = Modifier,
  onDefault: (() -> Unit)? = null) {
  LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
    if (!admin && onDefault != null) item(key = "default-plan") {
      Surface(onClick = onDefault, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("完整字库", style = MaterialTheme.typography.titleMedium)
            Text("默认方案", style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          ActionIcon(ActionSymbol.NEXT, Modifier.size(20.dp))
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (packages.isEmpty()) item {
      EmptyState(ActionSymbol.LIBRARY, "暂无调查包", if (admin) "新建或从更多菜单导入" else "从更多菜单导入")
    }
    items(packages, key = { "${it.packageId}-${it.revision}" }) { task ->
      var taskMenu by remember { mutableStateOf(false) }
      Surface(color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
        onClickLabel = if (admin) "编辑调查包" else "开始录制",
        onClick = { if (admin) onEdit(task) else onStart(task) },
        onLongClickLabel = if (admin) "更多" else null,
        onLongClick = if (admin) ({ taskMenu = true }) else null)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${task.items.size} 字目", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (admin) Box {
            MoreButton(enabled, onClick = { taskMenu = true })
            DropdownMenu(taskMenu, onDismissRequest = { taskMenu = false }) {
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.COPY) }, text = { Text("复制") }, onClick = { taskMenu = false; onCopy(task) })
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.QR) }, text = { Text("分享二维码") }, onClick = { taskMenu = false; onExportQr(task) })
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EXPORT) }, text = { Text("导出 JSON 文件") }, onClick = { taskMenu = false; onExport(task) })
            }
          } else ActionIcon(ActionSymbol.NEXT)
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun StudioLanding(enabled: Boolean, onStart: () -> Unit, onAdmin: () -> Unit, onSettings: () -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ActionIcon(ActionSymbol.WAVE, Modifier.size(24.dp))
        Text("韵录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings, enabled = enabled) {
          ActionIcon(ActionSymbol.SETTINGS, contentDescription = "设置")
        }
      }
      Spacer(Modifier.height(56.dp))
      Text("方言采集", style = MaterialTheme.typography.headlineLarge)
      Text("录音保存在本机", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(32.dp))
      Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        ActionIcon(ActionSymbol.MIC, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("开始录制", style = MaterialTheme.typography.titleMedium)
      }
      Spacer(Modifier.height(24.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Surface(onClick = onAdmin, enabled = enabled, color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          ActionIcon(ActionSymbol.LIBRARY, Modifier.size(22.dp))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("管理员", style = MaterialTheme.typography.titleMedium)
            Text("调查包 · 字库 · 导入导出", style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          ActionIcon(ActionSymbol.NEXT, Modifier.size(20.dp))
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

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
import com.wanluk.foundation.survey.SurveyPackageSummary
import com.wanluk.foundation.survey.SessionSummary
import com.wanluk.foundation.survey.DraftSummary
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
import com.wanluk.foundation.survey.BuiltinSurveys
import com.wanluk.foundation.survey.RecordingPlanType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioApp(viewModel: StudioViewModel, lifecycle: Lifecycle, transferViewModel: SurveyTransferViewModel = koinViewModel(), csvViewModel: SurveyCsvViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val packages by viewModel.packages.collectAsStateWithLifecycle()
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val drafts by viewModel.drafts.collectAsStateWithLifecycle()
  val csv by csvViewModel.state.collectAsStateWithLifecycle()
  val detail by viewModel.detail.collectAsStateWithLifecycle()
  val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
  val skippedItem by viewModel.skippedItem.collectAsStateWithLifecycle()
  val noteDraft by viewModel.noteDraft.collectAsStateWithLifecycle()
  val capture by viewModel.capture.collectAsStateWithLifecycle()
  val trial by viewModel.trial.collectAsStateWithLifecycle()
  val playing by viewModel.playing.collectAsStateWithLifecycle()
  val exported by viewModel.lastExport.collectAsStateWithLifecycle()
  val batchResult by viewModel.batchResult.collectAsStateWithLifecycle()
  val cleanupCount by viewModel.cleanupCount.collectAsStateWithLifecycle()
  val recorderSettings by viewModel.recorderSettings.collectAsStateWithLifecycle()
  val transfer by transferViewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val view = LocalView.current
  val snackbar = remember { SnackbarHostState() }
  val toast = rememberActionToastHostState()
  val scope = rememberCoroutineScope()
  val selectionBack = remember { SelectionBackState() }
  var startTask by remember { mutableStateOf<SurveyPackage?>(null) }
  var startDefault by rememberSaveable { mutableStateOf(false) }
  var skipReasonItem by remember { mutableStateOf<SkippedItem?>(null) }
  var showDrafts by remember { mutableStateOf(false) }
  var discardDraft by remember { mutableStateOf(false) }
  var permissionDenied by remember { mutableStateOf(false) }
  var pendingTrial by rememberSaveable { mutableStateOf(false) }
  var pendingHold by rememberSaveable { mutableStateOf(false) }
  var finishedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
  var homeSection by rememberSaveable { mutableStateOf("landing") }
  var adminTab by rememberSaveable { mutableIntStateOf(0) }
  var recordings by rememberSaveable { mutableStateOf(false) }
  var managedSession by remember { mutableStateOf<SessionSummary?>(null) }

  fun startRecordingTask(task: SurveyPackage) {
    if (recorderSettings.shouldAsk) startTask = task
    else viewModel.startSession(task, recorderSettings)
  }
  fun startDefaultRecording() {
    if (recorderSettings.shouldAsk) startDefault = true
    else viewModel.startDefaultSession(recorderSettings)
  }

  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(transferViewModel::readFile)
  }
  val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(csvViewModel::read) }
  val csvTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), csvViewModel::saveTemplate)
  fun showExportFailure(message: String) {
    scope.launch { toast.show(ActionToast.Builder(message).duration(3_000L).setY(12.dp).build()) }
  }
  val resultExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    viewModel.exportResult(uri, ::showExportFailure)
  }
  fun exportRecording(id: String) {
    viewModel.prepareResultExport(id, onReady = { resultExportLauncher.launch(it) }, onFailure = ::showExportFailure)
  }
  fun exportResults(ids: List<String>, positions: List<Int>? = null, takeIds: List<String>? = null) {
    viewModel.prepareResultsExport(ids, positions, takeIds, onReady = { resultExportLauncher.launch(it) }, onFailure = ::showExportFailure)
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
      selectionBack.exitAction != null -> selectionBack.exitAction?.invoke()
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

  CompositionLocalProvider(LocalSelectionBack provides selectionBack) {
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
      val enabled = state.ready && state.busy == null && capture == null && !transfer.busy && !csv.busy
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
          onStart = { viewModel.loadPackage(it, ::startRecordingTask) }, onResume = viewModel::resume, onManageSession = { managedSession = it },
          onEdit = { viewModel.loadPackage(it) { task -> viewModel.edit(task) } },
          onCopy = { viewModel.loadPackage(it) { task -> viewModel.edit(task, copy = true) } },
          onNew = viewModel::newPackage, onImport = { importLauncher.launch(arrayOf("*/*")) },
          onCsv = { csvPicker.launch(arrayOf("text/*", "application/octet-stream")) },
          onCsvTemplate = { csvTemplate.launch("wanluk-survey-template.csv") }, onDrafts = { showDrafts = true },
          onImportQr = transferViewModel::openImport, onExportQr = transferViewModel::generate,
          libraryContent = { active, selecting, onSelecting -> WordLibrary(false, enabled, viewModel::addWords,
            onCreate = viewModel::createPackage, active = active, multiSelecting = selecting, onMultiSelecting = onSelecting,
            onExport = transferViewModel::generateWords) },
          onExport = { transferViewModel.generateZip(listOf(it)) }, onBatchExport = transferViewModel::generateZip,
          onBatchCopy = viewModel::copyPackages, onBatchDelete = viewModel::deletePackages,
          onExportSessions = { exportResults(it) }, onDeleteSessions = viewModel::deleteSessions,
          onCleanup = if (cleanupCount > 0) viewModel::showCleanup else null)
        StudioScreen.SETTINGS -> RecorderSettingsScreen(recorderSettings, enabled,
          viewModel::saveRecorderSettings, viewModel::saveRecordingMode, viewModel::saveThemeMode)
        StudioScreen.EDITOR -> state.draft?.let { draft ->
          Column(Modifier.fillMaxSize()) {
            state.draftStatus?.let { status ->
              Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                  color = if (status.contains("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (status.contains("失败")) TextButton(onClick = viewModel::retryDraft, enabled = enabled) { Text("重试") }
              }
            }
            Box(Modifier.weight(1f)) {
              SurveyEditor(draft, enabled, viewModel::updateDraft, { viewModel.library(picking = true) }, viewModel::saveDraft,
                viewModel::resolveItem, onExport = transferViewModel::generateTask)
            }
          }
        }
        StudioScreen.LIBRARY -> WordLibrary(state.pickingWords, enabled, viewModel::addWords,
          creating = state.creatingPackage,
          maxSelection = minOf(SurveyPackage.MAX_ITEMS - (state.draft?.items?.size ?: 0),
            SurveyPackage.MAX_STEPS - (state.draft?.totalSteps ?: 0)).coerceAtLeast(0),
          onCreate = viewModel::createPackage)
        StudioScreen.SESSION -> detail?.takeIf { it.session.id == selectedSessionId }?.let { current ->
          key(current.session.id) {
            RecordingScreen(current, enabled, capture, viewModel.meter, trial, playing,
              onRecord = { held -> requestRecording(false, held) }, onTrial = { requestRecording(true) },
              holdToRecord = current.planType != RecordingPlanType.PASSAGE && recorderSettings.recordingMode == RecordingMode.HOLD,
              onStop = viewModel::stopRecording, onPlay = { viewModel.play(it) },
              onPlayTrial = { viewModel.play(isTrial = true) }, onStopPlayback = viewModel::stopPlayback,
              onPosition = viewModel::position, onSkip = viewModel::skip, onNote = viewModel::note,
              onSkipReason = viewModel::saveSkipReason,
              noteDraft = noteDraft, onNoteChange = viewModel::changeNote,
              onAdopt = viewModel::adopt, onReview = viewModel::review, busy = state.busy, toastHost = { ActionToastHost(toast) },
              onFinish = { viewModel.finishSession { summary ->
                homeSection = "record"
                recordings = true
                finishedSessionId = summary.id
              } },
              onExport = { exportRecording(current.session.id) },
              onExportSteps = { exportResults(listOf(current.session.id), positions = it) },
              onExportTakes = { exportResults(listOf(current.session.id), takeIds = it) },
              onClearSteps = viewModel::clearSteps, onDeleteTakes = viewModel::deleteTakes)
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
  }

  LaunchedEffect(state.screen) {
    if (state.screen == StudioScreen.SESSION) { startTask = null; startDefault = false }
    else { skipReasonItem = null; toast.dismiss() }
  }
  finishedSessionId?.let { id ->
    AppDialog(onDismissRequest = { if (state.busy == null) finishedSessionId = null },
      symbol = ActionSymbol.FINISH, title = { Text("进度已保存") }, text = {
        val summary = sessions.firstOrNull { it.id == id }
        Text(summary?.let { "已录 ${it.recordedSteps} 条 · 跳过 ${it.skippedSteps} 条 · 待录 ${it.pendingSteps} 条\n可立即导出录音 ZIP，或稍后继续处理。" } ?: "可立即导出录音 ZIP，或稍后继续处理。")
      },
      confirmButton = { Button(onClick = { finishedSessionId = null; exportRecording(id) },
        enabled = state.busy == null) { Text("立即导出") } },
      dismissButton = { TextButton(onClick = { finishedSessionId = null }, enabled = state.busy == null) { Text("稍后") } })
  }
  managedSession?.let { session -> RecordingSessionDialog(session, state.busy == null,
    onDismiss = { managedSession = null },
    onSave = { title, alias, dialect, researchCode, location, collector ->
      viewModel.updateSessionInfo(session.id, title, alias, dialect, researchCode, location, collector) { managedSession = null }
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
  SurveyTransferDialogs(transferViewModel, transfer)
  SurveyCsvDialog(csv, csvViewModel, viewModel::csvDraft)
  if (showDrafts) LocalDraftsDialog(drafts, state.busy == null, onDismiss = { showDrafts = false },
    onResume = { showDrafts = false; viewModel.resumeDraft(it) }, onDelete = viewModel::deleteLocalDraft)
  batchResult?.let { result -> AppDialog(onDismissRequest = viewModel::clearBatchResult,
    symbol = ActionSymbol.INFO, title = { Text(result.title) }, text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已完成 ${result.succeeded} 项")
        result.failures.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        if (result.cleanup.isNotEmpty()) Text("数据已移除，${result.cleanup.sumOf { it.takeIds?.size ?: 1 }} 项音频尚未清理，可重试。", color = MaterialTheme.colorScheme.error)
      }
    }, confirmButton = { TextButton(onClick = viewModel::clearBatchResult, enabled = state.busy == null) { Text("完成") } },
    dismissButton = if (result.cleanup.isNotEmpty()) ({
      Button(onClick = viewModel::retryCleanup, enabled = state.busy == null) { Text("重试清理") }
    }) else null) }
  if (discardDraft) AppDialog(onDismissRequest = { discardDraft = false },
    symbol = ActionSymbol.EDIT, title = { Text("离开编辑器？") }, text = { Text("保留为本机草稿后，可从更多菜单中的「本机草稿」继续编辑。") },
    confirmButton = { Button(onClick = { discardDraft = false; viewModel.home() }) { Text("保留草稿并离开") } },
    dismissButton = {
      Row {
        TextButton(onClick = { discardDraft = false; viewModel.discardDraft() }) { Text("丢弃", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { discardDraft = false }) { Text("继续编辑") }
      }
    })
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
@OptIn(ExperimentalLayoutApi::class)
private fun StudioHome(
  packages: List<SurveyPackageSummary>, sessions: List<SessionSummary>, enabled: Boolean,
  section: String, onSection: (String) -> Unit,
  onSettings: () -> Unit,
  adminTab: Int, onAdminTab: (Int) -> Unit, onDefault: () -> Unit,
  recordings: Boolean, onRecordings: (Boolean) -> Unit,
  onStart: (SurveyPackageSummary) -> Unit, onResume: (String) -> Unit, onEdit: (SurveyPackageSummary) -> Unit,
  onManageSession: (SessionSummary) -> Unit,
  onCopy: (SurveyPackageSummary) -> Unit, onNew: () -> Unit, onImport: () -> Unit,
  onCsv: () -> Unit, onCsvTemplate: () -> Unit, onDrafts: () -> Unit,
  libraryContent: @Composable (Boolean, Boolean, (Boolean) -> Unit) -> Unit,
  onExport: (SurveyPackageSummary) -> Unit,
  onImportQr: () -> Unit, onExportQr: (SurveyPackageSummary) -> Unit,
  onBatchExport: (List<SurveyPackageSummary>) -> Unit,
  onBatchCopy: (List<SurveyPackageSummary>) -> Unit, onBatchDelete: (List<SurveyPackageSummary>) -> Unit,
  onExportSessions: (List<String>) -> Unit, onDeleteSessions: (List<String>) -> Unit,
  onCleanup: (() -> Unit)?,
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
  val allPlans = remember(packages, admin) {
    val libraryId = RecordingTaskSnapshot.LIBRARY_PACKAGE_ID
    (packages.filterNot { it.packageId == libraryId } + SurveyPackageSummary(libraryId,
      BuiltinSurveys.revision(libraryId), BuiltinSurveys.title(libraryId), 0, 0, "按字库顺序逐条录制", ""))
      .sortedBy { BuiltinSurveys.rank(it.packageId) }
  }
  var packageQuery by rememberSaveable(section) { mutableStateOf("") }
  var sessionQuery by rememberSaveable(section) { mutableStateOf("") }
  var sessionFilter by rememberSaveable { mutableStateOf("") }
  val plans = remember(allPlans, packageQuery) { allPlans.filter { task ->
    listOf(task.title, task.description, task.dialect).any { it.contains(packageQuery.trim(), ignoreCase = true) }
  } }
  val visibleSessions = remember(sessions, sessionQuery, sessionFilter) { sessions.filter { session ->
    listOf(session.title, session.speakerAlias, session.dialect, session.researchCode, session.collectionLocation, session.collector)
      .any { it.contains(sessionQuery.trim(), ignoreCase = true) } && when (sessionFilter) {
        "pending" -> session.pendingSteps > 0; "export" -> session.needsExport; else -> true
      }
  } }
  val wordPage = admin && pager.currentPage == 1
  val sessionPage = !admin && pager.currentPage == 1
  val keys = if (wordPage) emptyList() else if (sessionPage) visibleSessions.map { it.id } else plans.map { it.selectionKey() }
  val selection = key(section) { rememberMultiSelection(keys, enabled) }
  var wordSelecting by rememberSaveable(section) { mutableStateOf(false) }
  var previousPage by rememberSaveable(section) { mutableIntStateOf(pager.currentPage) }
  var deleting by remember { mutableStateOf(false) }
  val chosenPlans = plans.filter { it.selectionKey() in selection.selected }
  val removable = chosenPlans.filterNot { BuiltinSurveys.isBuiltin(it.packageId) }
  LaunchedEffect(admin, pager.currentPage) {
    if (previousPage != pager.currentPage) { selection.exit(); wordSelecting = false; deleting = false; previousPage = pager.currentPage }
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
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.MULTISELECT) }, text = { Text("多选") },
            enabled = !selection.active && !wordSelecting, onClick = {
              menu = false
              if (wordPage) wordSelecting = true else selection.enter()
            })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.QR) }, text = { Text("扫一扫") }, onClick = { menu = false; onImportQr() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.IMPORT) }, text = { Text("从文件导入") }, onClick = { menu = false; onImport() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.IMPORT) }, text = { Text("从 CSV 生成调查包") }, onClick = { menu = false; onCsv() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EXPORT) }, text = { Text("保存 CSV 模板") }, onClick = { menu = false; onCsvTemplate() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EDIT) }, text = { Text("本机草稿") }, onClick = { menu = false; onDrafts() })
          if (onCleanup != null) DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.TRASH) },
            text = { Text("重试音频清理") }, onClick = { menu = false; onCleanup() })
        }
      }
    }
    if (!wordPage) {
      OutlinedTextField(if (sessionPage) sessionQuery else packageQuery, {
        if (sessionPage) sessionQuery = it.take(100) else packageQuery = it.take(100)
      }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
        placeholder = { Text(if (sessionPage) "搜索录制、研究编号、地点" else "搜索调查包") }, enabled = enabled,
        leadingIcon = { ActionIcon(ActionSymbol.SEARCH) })
      if (sessionPage) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to "全部", "pending" to "有待录题", "export" to "待导出").forEach { (value, label) ->
          FilterChip(sessionFilter == value, { sessionFilter = value }, enabled = enabled, label = { Text(label) })
        }
      }
    }
    if (selection.active && !wordPage) Box(Modifier.padding(horizontal = 16.dp)) {
      SelectionToolbar(selection, enabled) { selection.all(keys) }
    }
    HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = enabled,
      verticalAlignment = Alignment.Top) { page ->
      if (admin) {
        if (page == 0) Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
          SurveyPackageList(plans, enabled, true, onStart, onEdit, onCopy, onExportQr, onExport, Modifier.weight(1f), selection = selection, onDelete = { onBatchDelete(listOf(it)) })
          if (!selection.active) Button(onClick = onNew, enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp)) {
            ActionIcon(ActionSymbol.ADD); Spacer(Modifier.width(8.dp)); Text("新建调查包")
          }
        } else libraryContent(pager.currentPage == 1, wordSelecting) { wordSelecting = it }
      } else if (page == 1) RecordingList(visibleSessions, enabled, onResume, onManageSession, selection)
      else SurveyPackageList(plans, enabled, false, onStart, onEdit, onCopy, onExportQr, onExport,
        Modifier.fillMaxSize().padding(horizontal = 16.dp), onDefault = onDefault, selection = selection, onDelete = { onBatchDelete(listOf(it)) })
    }
    if (selection.active && !wordPage) Column(Modifier.padding(horizontal = 16.dp)) {
      if (!sessionPage && chosenPlans.size > com.wanluk.libsurveytransfer.SurveyTransferFiles.MAX_PLANS) {
        Text("一次最多导出 32 个方案", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      SelectionActions {
        TextButton(onClick = { if (sessionPage) onExportSessions(selection.ids) else onBatchExport(chosenPlans) },
          enabled = enabled && selection.ids.isNotEmpty() && (sessionPage || chosenPlans.size <= com.wanluk.libsurveytransfer.SurveyTransferFiles.MAX_PLANS)) { Text("导出 ZIP") }
        if (!sessionPage) TextButton(onClick = { onBatchCopy(chosenPlans) }, enabled = enabled && chosenPlans.isNotEmpty()) { Text("复制") }
        TextButton(onClick = { deleting = true }, enabled = enabled && if (sessionPage) selection.ids.isNotEmpty() else removable.isNotEmpty()) {
          Text("删除", color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
  if (deleting) DeleteSelectionDialog(if (sessionPage) "删除 ${selection.ids.size} 条录制？" else "删除 ${removable.size} 个调查包？",
    if (sessionPage) "所选录制及全部音频将删除，无法恢复。" else
      "删除所选调查包的全部版本，已有录制保留。${if (chosenPlans.size > removable.size) "保留 ${chosenPlans.size - removable.size} 个内置预设。" else ""}",
    enabled, onDismiss = { deleting = false }, onDelete = {
      deleting = false
      if (sessionPage) onDeleteSessions(selection.ids) else onBatchDelete(removable)
    })
}

private fun SurveyPackageSummary.selectionKey(): String = "$packageId:$revision"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingList(sessions: List<SessionSummary>, enabled: Boolean,
  onResume: (String) -> Unit, onManage: (SessionSummary) -> Unit, selection: MultiSelection) {
      LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (sessions.isEmpty()) item {
          EmptyState(ActionSymbol.RECORDINGS, "暂无录制", "从「录制方案」开始")
        }
        items(sessions, key = { it.id }) { session ->
          Surface(color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
            onClickLabel = if (selection.active) "选择录制" else "打开录制", onClick = { if (selection.active) selection.toggle(session.id) else onResume(session.id) },
            onLongClickLabel = "管理录制", onLongClick = { if (selection.active) selection.toggle(session.id) else onManage(session) })) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                  maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selection.active) Checkbox(session.id in selection.selected, { selection.toggle(session.id) }, enabled = enabled)
                else MoreButton(enabled, onClick = { onManage(session) })
              }
              Text(listOf(session.speakerAlias, session.dialect).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未填写录制者信息" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              LinearProgressIndicator(progress = { session.completedSteps.toFloat() / session.totalSteps.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                  Text("${session.completedSteps} / ${session.totalSteps}", style = MaterialTheme.typography.labelLarge)
                  Text(if (session.needsExport) "待导出" else "已导出当前内容", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
private fun SurveyPackageList(packages: List<SurveyPackageSummary>, enabled: Boolean, admin: Boolean,
  onStart: (SurveyPackageSummary) -> Unit, onEdit: (SurveyPackageSummary) -> Unit, onCopy: (SurveyPackageSummary) -> Unit,
  onExportQr: (SurveyPackageSummary) -> Unit, onExport: (SurveyPackageSummary) -> Unit, modifier: Modifier = Modifier,
  onDefault: (() -> Unit)? = null, selection: MultiSelection, onDelete: (SurveyPackageSummary) -> Unit) {
  var deleting by remember { mutableStateOf<SurveyPackageSummary?>(null) }
  LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
    if (packages.isEmpty()) item {
      EmptyState(ActionSymbol.LIBRARY, "暂无调查包", if (admin) "新建或从更多菜单导入" else "从更多菜单导入")
    }
    items(packages, key = { "${it.packageId}-${it.revision}" }) { task ->
      var taskMenu by remember { mutableStateOf(false) }
      Surface(color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
        onClickLabel = if (admin) "编辑调查包" else "开始录制",
        onClick = {
          when {
            selection.active -> selection.toggle(task.selectionKey())
            admin -> onEdit(task)
            task.packageId == RecordingTaskSnapshot.LIBRARY_PACKAGE_ID && onDefault != null -> onDefault()
            else -> onStart(task)
          }
        },
        onLongClickLabel = if (admin) "更多" else null,
        onLongClick = if (admin) ({ if (selection.active) selection.toggle(task.selectionKey()) else taskMenu = true }) else null)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(BuiltinSurveys.subtitle(task.packageId, task.itemCount) ?: "${task.itemCount} 题",
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (selection.active) Checkbox(task.selectionKey() in selection.selected, { selection.toggle(task.selectionKey()) }, enabled = enabled)
          else Box {
            MoreButton(enabled, onClick = { taskMenu = true })
            DropdownMenu(taskMenu, onDismissRequest = { taskMenu = false }) {
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.COPY) }, text = { Text("复制") }, onClick = { taskMenu = false; onCopy(task) })
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.QR) }, text = { Text("分享二维码") }, onClick = { taskMenu = false; onExportQr(task) })
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EXPORT) }, text = { Text("导出 ZIP") }, onClick = { taskMenu = false; onExport(task) })
              DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.TRASH) }, text = { Text("删除调查包", color = MaterialTheme.colorScheme.error) },
                enabled = !BuiltinSurveys.isBuiltin(task.packageId), onClick = { taskMenu = false; deleting = task })
            }
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
  deleting?.let { task -> DeleteSelectionDialog("删除《${task.title}》？", "删除调查包的全部版本，已有录制及录音保留。此操作无法恢复。", enabled,
    onDismiss = { deleting = null }, onDelete = { deleting = null; onDelete(task) }) }
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

@Composable
private fun LocalDraftsDialog(drafts: List<DraftSummary>, enabled: Boolean, onDismiss: () -> Unit,
  onResume: (String) -> Unit, onDelete: (String) -> Unit) {
  var deleting by remember { mutableStateOf<DraftSummary?>(null) }
  AppDialog(onDismissRequest = onDismiss, symbol = ActionSymbol.EDIT, title = { Text("本机草稿") }, text = {
    if (drafts.isEmpty()) Text("暂无草稿") else LazyColumn(Modifier.heightIn(max = 360.dp)) {
      items(drafts, key = { it.id }) { draft ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(draft.title.ifBlank { "未命名草稿" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
          TextButton(onClick = { onResume(draft.id) }, enabled = enabled) { Text("继续") }
          IconButton(onClick = { deleting = draft }, enabled = enabled) { ActionIcon(ActionSymbol.TRASH, contentDescription = "丢弃草稿") }
        }
      }
    }
  }, confirmButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text("关闭") } })
  deleting?.let { draft -> DeleteSelectionDialog("丢弃《${draft.title.ifBlank { "未命名草稿" }}》？", "丢弃未发布的本机草稿，无法恢复。", enabled,
    onDismiss = { deleting = null }, onDelete = { deleting = null; onDelete(draft.id) }) }
}

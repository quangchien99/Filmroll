package com.filmroll.camera.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.filmroll.camera.data.source.local.ThemeMode
import com.filmroll.camera.lut.LutDownloadManager
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.about
import com.filmroll.camera.resources.action_apply
import com.filmroll.camera.resources.app_version
import com.filmroll.camera.resources.appearance
import com.filmroll.camera.resources.cancel
import com.filmroll.camera.resources.clear
import com.filmroll.camera.resources.contact
import com.filmroll.camera.resources.daily_reminder
import com.filmroll.camera.resources.daily_reminder_summary
import com.filmroll.camera.resources.dark_mode
import com.filmroll.camera.resources.dark_mode_summary
import com.filmroll.camera.resources.debug
import com.filmroll.camera.resources.debug_clear_data
import com.filmroll.camera.resources.debug_clear_data_confirm
import com.filmroll.camera.resources.debug_clear_data_done
import com.filmroll.camera.resources.debug_clear_data_summary
import com.filmroll.camera.resources.default_picker
import com.filmroll.camera.resources.developer
import com.filmroll.camera.resources.download_luts
import com.filmroll.camera.resources.download_luts_summary
import com.filmroll.camera.resources.export_format
import com.filmroll.camera.resources.export_format_jpeg
import com.filmroll.camera.resources.export_format_original
import com.filmroll.camera.resources.export_format_summary_jpeg
import com.filmroll.camera.resources.export_format_summary_original
import com.filmroll.camera.resources.files
import com.filmroll.camera.resources.image_export_quality
import com.filmroll.camera.resources.images
import com.filmroll.camera.resources.language
import com.filmroll.camera.resources.notifications
import com.filmroll.camera.resources.settings
import com.filmroll.camera.resources.source_code
import com.filmroll.camera.resources.theme_mode_system
import com.filmroll.camera.screens.language.LanguageScreen
import com.filmroll.camera.util.AppContext
import com.filmroll.camera.util.Platform
import com.filmroll.camera.util.restartApp
import com.filmroll.camera.view.LutDownloadDialog
import com.filmroll.camera.view.LutDownloadProgressDialog
import com.filmroll.camera.view.SettingsSlider
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform.getKoin
import sh.calvin.autolinktext.rememberAutoLinkText


class SettingsScreen : Screen {

    // See HomeScreen — workaround for voyager#546.
    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        var showDefaultPickerDialog by remember { mutableStateOf(false) }
        var showExportFormatDialog by remember { mutableStateOf(false) }
        var showClearDataDialog by remember { mutableStateOf(false) }
        val vm = koinScreenModel<SettingsScreenModel>()
        val uiState by vm.uiState.collectAsState()
        val dataCleared by vm.dataCleared.collectAsState()
        val dataClearedMessage = stringResource(Res.string.debug_clear_data_done)

        val lutDownloadManager = remember { getKoin().get<LutDownloadManager>() }
        val lutDownloadState by lutDownloadManager.uiState.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        // The language picker writes straight to storage, so pull the fresh values back in
        // whenever we come back to this screen.
        LaunchedEffect(Unit) { vm.refresh() }

        // Restart only once the wipe has actually finished, and only after the user has had
        // a chance to read the confirmation.
        LaunchedEffect(dataCleared) {
            if (!dataCleared) return@LaunchedEffect
            snackbarHostState.showSnackbar(dataClearedMessage)
            restartApp()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            stringResource(Res.string.settings),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            },
        ) { paddingValues ->

            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                SectionHeader(stringResource(Res.string.appearance))
                SwitchRow(
                    title = stringResource(Res.string.theme_mode_system),
                    checked = uiState.themeMode == ThemeMode.SYSTEM,
                    onCheckedChange = { followSystem ->
                        vm.updateThemeMode(if (followSystem) ThemeMode.SYSTEM else ThemeMode.LIGHT)
                    },
                )
                SwitchRow(
                    title = stringResource(Res.string.dark_mode),
                    summary = stringResource(Res.string.dark_mode_summary),
                    checked = uiState.themeMode == ThemeMode.DARK,
                    enabled = uiState.themeMode != ThemeMode.SYSTEM,
                    onCheckedChange = { dark ->
                        vm.updateThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                    },
                )
                ListItem(
                    modifier = Modifier.clickable { navigator.push(LanguageScreen()) },
                    text = { RowTitle(stringResource(Res.string.language)) },
                    secondaryText = { RowSummary(uiState.language.endonym) },
                )

                SettingsDivider()
                SectionHeader(stringResource(Res.string.notifications))
                SwitchRow(
                    title = stringResource(Res.string.daily_reminder),
                    summary = stringResource(Res.string.daily_reminder_summary),
                    checked = uiState.dailyReminderEnabled,
                    onCheckedChange = vm::setDailyReminderEnabled,
                )

                SettingsDivider()
                SectionHeader(stringResource(Res.string.settings))
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSlider(
                    name = stringResource(Res.string.image_export_quality),
                    value = uiState.exportQuality.toFloat(),
                    steps = 8,
                    range = 10f..100f,
                    onValueChange = vm::updateExportQualitySettings
                )
                Spacer(modifier = Modifier.height(8.dp))
                ListItem(
                    modifier = Modifier.clickable { showExportFormatDialog = true },
                    text = { RowTitle(stringResource(Res.string.export_format)) },
                    secondaryText = { RowSummary(stringResource(uiState.exportFormat.getString())) },
                )
                ListItem(
                    modifier = Modifier.clickable { showDefaultPickerDialog = true },
                    text = { RowTitle(stringResource(Res.string.default_picker)) },
                    secondaryText = { RowSummary(stringResource(uiState.defaultPicker.getString())) },
                )
                ListItem(
                    modifier = Modifier.clickable { lutDownloadManager.showDownloadDialog() },
                    text = { RowTitle(stringResource(Res.string.download_luts)) },
                    secondaryText = { RowSummary(stringResource(Res.string.download_luts_summary)) },
                )

                SettingsDivider()
                SectionHeader(stringResource(Res.string.about))
                AboutSection()

                if (uiState.showDebugTools) {
                    SettingsDivider()
                    SectionHeader(stringResource(Res.string.debug))
                    ListItem(
                        modifier = Modifier.clickable { showClearDataDialog = true },
                        text = { RowTitle(stringResource(Res.string.debug_clear_data)) },
                        secondaryText = { RowSummary(stringResource(Res.string.debug_clear_data_summary)) },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        LutDownloadDialog(
            isVisible = lutDownloadState.showDownloadDialog,
            onDismiss = { lutDownloadManager.dismissDownloadDialog() },
            onConfirm = {
                lutDownloadManager.confirmDownloadLuts(coroutineScope) { _, message ->
                    message?.let {
                        coroutineScope.launch { snackbarHostState.showSnackbar(it) }
                    }
                }
            }
        )

        LutDownloadProgressDialog(
            isVisible = lutDownloadState.showDownloadProgress,
            current = lutDownloadState.downloadProgress.first,
            total = lutDownloadState.downloadProgress.second,
            onDismiss = {} // Progress dialog can't be dismissed
        )

        uiState.userMessage?.let { message ->
            LaunchedEffect(vm, message) {
                snackbarHostState.showSnackbar(message)
                vm.snackbarMessageShown()
            }
        }

        DefaultPickerDialog(showDefaultPickerDialog, onItemClick = vm::updateDefaultPickerSettings) {
            showDefaultPickerDialog = false
        }

        ExportFormatDialog(showExportFormatDialog, onItemClick = vm::updateExportFormatSettings) {
            showExportFormatDialog = false
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text(stringResource(Res.string.debug_clear_data)) },
                text = { Text(stringResource(Res.string.debug_clear_data_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDataDialog = false
                        vm.clearAllData()
                    }) { Text(stringResource(Res.string.clear)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }

    @Composable
    private fun SettingsDivider() {
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    @Composable
    private fun RowTitle(text: String) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }

    @Composable
    private fun RowSummary(text: String) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    private fun SwitchRow(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
    ) {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
            text = { RowTitle(title) },
            secondaryText = summary?.let { { RowSummary(it) } },
            trailing = {
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            },
        )
    }

    @OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun ExportFormatDialog(
        show: Boolean,
        onItemClick: (ExportFormat) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (!show) return
        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.export_format),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ListItem(
                        text = { RowSummary(stringResource(Res.string.export_format_jpeg)) },
                        secondaryText = {
                            Text(
                                text = stringResource(Res.string.export_format_summary_jpeg),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickable {
                            onItemClick(ExportFormat.JPEG)
                            onDismiss()
                        }
                    )
                    ListItem(
                        text = { RowSummary(stringResource(Res.string.export_format_original)) },
                        secondaryText = {
                            Text(
                                text = stringResource(Res.string.export_format_summary_original),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickable {
                            onItemClick(ExportFormat.ORIGINAL)
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = onDismiss
                    ) {
                        Text(
                            text = stringResource(Res.string.cancel),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun AboutSection() {
        Column {
            ListItem(
                text = { RowTitle(stringResource(Res.string.app_version)) },
                secondaryText = { RowSummary(Platform(AppContext).getAppVersion()) },
            )
            ListItem(
                text = { RowTitle(stringResource(Res.string.developer)) },
                secondaryText = { RowSummary("Quang Chien Pham") },
            )
            ListItem(
                text = { RowTitle(stringResource(Res.string.source_code)) },
                secondaryText = {
                    Text(
                        AnnotatedString.rememberAutoLinkText("https://github.com/quangchien99/Filmroll"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
            ListItem(
                text = { RowTitle(stringResource(Res.string.contact)) },
                secondaryText = {
                    Text(
                        AnnotatedString.rememberAutoLinkText("phamquangchien170499@gmail.com"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }

    @OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun DefaultPickerDialog(
        show: Boolean,
        onItemClick: (DefaultPickerType) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (!show) return
        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.default_picker),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ListItem(
                        text = { RowSummary(stringResource(Res.string.images)) },
                        modifier = Modifier.clickable {
                            onItemClick(DefaultPickerType.IMAGES)
                            onDismiss()
                        }
                    )
                    ListItem(
                        text = { RowSummary(stringResource(Res.string.files)) },
                        modifier = Modifier.clickable {
                            onItemClick(DefaultPickerType.FILES)
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = onDismiss
                    ) {
                        Text(
                            text = stringResource(Res.string.cancel),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

package com.filmroll.camera.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.filmroll.camera.resources.app_version
import com.filmroll.camera.resources.appearance
import com.filmroll.camera.resources.cancel
import com.filmroll.camera.resources.clear
import com.filmroll.camera.resources.contact
import com.filmroll.camera.resources.daily_reminder
import com.filmroll.camera.resources.daily_reminder_summary
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
import com.filmroll.camera.resources.settings_editing
import com.filmroll.camera.resources.settings_export_quality_summary
import com.filmroll.camera.resources.source_code
import com.filmroll.camera.resources.theme_mode
import com.filmroll.camera.resources.theme_mode_dark
import com.filmroll.camera.resources.theme_mode_light
import com.filmroll.camera.resources.theme_mode_system
import com.filmroll.camera.screens.language.LanguageScreen
import com.filmroll.camera.util.AppContext
import com.filmroll.camera.util.Platform
import com.filmroll.camera.util.restartApp
import com.filmroll.camera.view.AppDialog
import com.filmroll.camera.view.ChoiceRow
import com.filmroll.camera.view.ChromeIconButton
import com.filmroll.camera.view.LutDownloadDialog
import com.filmroll.camera.view.LutDownloadProgressDialog
import com.filmroll.camera.view.SegmentedTabs
import com.filmroll.camera.view.SettingsGroup
import com.filmroll.camera.view.SettingsRow
import com.filmroll.camera.view.SettingsRowDivider
import com.filmroll.camera.view.SettingsSliderRow
import com.filmroll.camera.view.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform.getKoin
import sh.calvin.autolinktext.rememberAutoLinkText

/**
 * Settings, grouped.
 *
 * The one behavioural change worth calling out is the theme control. It used to
 * be two switches — "follow system" and "dark mode" — where the second was
 * disabled by the first, which meant the app had three states expressed as four
 * combinations and one of them was unreachable. A three-way segment says the same
 * thing in one row and cannot be put into a nonsense state.
 */
class SettingsScreen : Screen {

    // See HomeScreen — workaround for voyager#546.
    override val key: ScreenKey = uniqueScreenKey

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

        uiState.userMessage?.let { message ->
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message)
                vm.snackbarMessageShown()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeIconButton(
                        onClick = { navigator.pop() },
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        backgroundAlpha = 0.06f,
                    )
                }

                Text(
                    text = stringResource(Res.string.settings),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 24.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    SettingsGroup(title = stringResource(Res.string.appearance)) {
                        ThemeRow(
                            themeMode = uiState.themeMode,
                            onThemeModeChange = vm::updateThemeMode,
                        )
                        SettingsRowDivider(insetIcon = false)
                        SettingsRow(
                            title = stringResource(Res.string.language),
                            icon = Icons.Rounded.Language,
                            value = uiState.language.endonym,
                            onClick = { navigator.push(LanguageScreen()) },
                        )
                    }

                    SettingsGroup(title = stringResource(Res.string.notifications)) {
                        SettingsSwitchRow(
                            title = stringResource(Res.string.daily_reminder),
                            summary = stringResource(Res.string.daily_reminder_summary),
                            icon = Icons.Rounded.NotificationsActive,
                            checked = uiState.dailyReminderEnabled,
                            onCheckedChange = vm::setDailyReminderEnabled,
                        )
                    }

                    SettingsGroup(title = stringResource(Res.string.settings_editing)) {
                        SettingsSliderRow(
                            title = stringResource(Res.string.image_export_quality),
                            summary = stringResource(Res.string.settings_export_quality_summary),
                            icon = Icons.Rounded.HighQuality,
                            value = uiState.exportQuality.toFloat(),
                            valueRange = 10f..100f,
                            steps = 8,
                            onValueChange = vm::updateExportQualitySettings,
                        )
                        SettingsRowDivider()
                        SettingsRow(
                            title = stringResource(Res.string.export_format),
                            icon = Icons.Rounded.Image,
                            value = stringResource(uiState.exportFormat.getString()),
                            onClick = { showExportFormatDialog = true },
                        )
                        SettingsRowDivider()
                        SettingsRow(
                            title = stringResource(Res.string.default_picker),
                            icon = Icons.Rounded.PhotoLibrary,
                            value = stringResource(uiState.defaultPicker.getString()),
                            onClick = { showDefaultPickerDialog = true },
                        )
                        SettingsRowDivider()
                        SettingsRow(
                            title = stringResource(Res.string.download_luts),
                            summary = stringResource(Res.string.download_luts_summary),
                            icon = Icons.Rounded.CloudDownload,
                            onClick = { lutDownloadManager.showDownloadDialog() },
                        )
                    }

                    SettingsGroup(title = stringResource(Res.string.about)) {
                        SettingsRow(
                            title = stringResource(Res.string.app_version),
                            icon = Icons.Rounded.Info,
                            value = Platform(AppContext).getAppVersion(),
                        )
                        SettingsRowDivider()
                        SettingsRow(
                            title = stringResource(Res.string.developer),
                            icon = Icons.Rounded.Person,
                            summary = "Quang Chien Pham",
                        )
                        SettingsRowDivider()
                        SettingsRow(
                            title = stringResource(Res.string.source_code),
                            icon = Icons.Rounded.Code,
                            trailing = {
                                Text(
                                    text = AnnotatedString.rememberAutoLinkText(
                                        "https://github.com/quangchien99/Filmroll",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                        )
                        SettingsRowDivider()
                        SettingsRow(
                            title = stringResource(Res.string.contact),
                            icon = Icons.Rounded.MailOutline,
                            trailing = {
                                Text(
                                    text = AnnotatedString.rememberAutoLinkText(
                                        "phamquangchien170499@gmail.com",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                        )
                    }

                    if (uiState.showDebugTools) {
                        SettingsGroup(title = stringResource(Res.string.debug)) {
                            SettingsRow(
                                title = stringResource(Res.string.debug_clear_data),
                                summary = stringResource(Res.string.debug_clear_data_summary),
                                icon = Icons.Rounded.DeleteForever,
                                iconTint = MaterialTheme.colorScheme.error,
                                onClick = { showClearDataDialog = true },
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(40.dp),
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
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
            },
        )

        LutDownloadProgressDialog(
            isVisible = lutDownloadState.showDownloadProgress,
            current = lutDownloadState.downloadProgress.first,
            total = lutDownloadState.downloadProgress.second,
            onDismiss = {}, // Progress dialog can't be dismissed
        )

        if (showExportFormatDialog) {
            AppDialog(
                onDismissRequest = { showExportFormatDialog = false },
                icon = Icons.Rounded.Image,
                title = stringResource(Res.string.export_format),
                dismissLabel = stringResource(Res.string.cancel),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                ChoiceRow(
                    title = stringResource(Res.string.export_format_jpeg),
                    summary = stringResource(Res.string.export_format_summary_jpeg),
                    selected = uiState.exportFormat == ExportFormat.JPEG,
                    onClick = {
                        vm.updateExportFormatSettings(ExportFormat.JPEG)
                        showExportFormatDialog = false
                    },
                )
                ChoiceRow(
                    title = stringResource(Res.string.export_format_original),
                    summary = stringResource(Res.string.export_format_summary_original),
                    selected = uiState.exportFormat == ExportFormat.ORIGINAL,
                    onClick = {
                        vm.updateExportFormatSettings(ExportFormat.ORIGINAL)
                        showExportFormatDialog = false
                    },
                )
            }
        }

        if (showDefaultPickerDialog) {
            AppDialog(
                onDismissRequest = { showDefaultPickerDialog = false },
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(Res.string.default_picker),
                dismissLabel = stringResource(Res.string.cancel),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                ChoiceRow(
                    title = stringResource(Res.string.images),
                    selected = uiState.defaultPicker == DefaultPickerType.IMAGES,
                    onClick = {
                        vm.updateDefaultPickerSettings(DefaultPickerType.IMAGES)
                        showDefaultPickerDialog = false
                    },
                )
                ChoiceRow(
                    title = stringResource(Res.string.files),
                    selected = uiState.defaultPicker == DefaultPickerType.FILES,
                    onClick = {
                        vm.updateDefaultPickerSettings(DefaultPickerType.FILES)
                        showDefaultPickerDialog = false
                    },
                )
            }
        }

        if (showClearDataDialog) {
            AppDialog(
                onDismissRequest = { showClearDataDialog = false },
                icon = Icons.Rounded.DeleteForever,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(Res.string.debug_clear_data),
                message = stringResource(Res.string.debug_clear_data_confirm),
                confirmLabel = stringResource(Res.string.clear),
                destructive = true,
                onConfirm = {
                    showClearDataDialog = false
                    vm.clearAllData()
                },
                dismissLabel = stringResource(Res.string.cancel),
            )
        }
    }
}

/**
 * Theme choice as one three-way control. The order matches the mental model —
 * automatic first, then the two manual overrides.
 */
@Composable
private fun ThemeRow(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val order = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val labels = listOf(
        stringResource(Res.string.theme_mode_system),
        stringResource(Res.string.theme_mode_light),
        stringResource(Res.string.theme_mode_dark),
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(Res.string.theme_mode),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SegmentedTabs(
            options = labels,
            selectedIndex = order.indexOf(themeMode).coerceAtLeast(0),
            onSelect = { onThemeModeChange(order[it]) },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            thumbColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

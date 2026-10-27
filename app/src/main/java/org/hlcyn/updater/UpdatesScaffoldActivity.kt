/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The Halcyon Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hlcyn.updater

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat as AndroidDateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.hlcyn.updater.controller.UpdaterController
import org.hlcyn.updater.data.Update
import org.hlcyn.updater.data.UpdateStatus
import org.hlcyn.updater.deviceinfo.DeviceInfoUtils
import org.hlcyn.updater.preferences.PreferencesActivity
import org.hlcyn.updater.updates.action.AlertDialogState
import org.hlcyn.updater.updates.action.UpdateActionDialog
import org.hlcyn.updater.updates.action.UpdateActionHandler
import org.hlcyn.updater.updates.action.UpdateActionType
import org.hlcyn.updater.updates.state.ProgressState
import org.hlcyn.updater.updates.state.UpdateItemStateMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val GoogleSans = FontFamily(
    Font(R.font.google_sans, FontWeight.Normal),
    Font(R.font.google_sans_bold, FontWeight.Bold),
    Font(R.font.google_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.google_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

abstract class UpdatesScaffoldActivity : ComponentActivity() {
    private val viewModel by viewModels<UpdatesViewModel>()
    private var activeUpdaterController: UpdaterController? by mutableStateOf(null)
    private var controllerStateVersion: Int by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    protected fun setupCompose() {
        setContent {
            SettingsTheme {
                val uiState by viewModel.uiState.collectAsState()
                val changelogUiState by viewModel.changelogUiState.collectAsState()

                HalcyonUpdatesScreen(
                    uiState = uiState,
                    changelogState = changelogUiState,
                    updaterController = activeUpdaterController,
                    controllerStateVersion = controllerStateVersion,
                    onBackClick = { finish() },
                    onRefreshClick = { onRefreshClick() },
                    onLocalUpdateClick = { onLocalUpdateClick() },
                    onPreferencesClick = {
                        startActivity(
                            Intent(
                                this@UpdatesScaffoldActivity,
                                PreferencesActivity::class.java,
                            )
                        )
                    },
                    onLoadChangelog = { update -> viewModel.loadChangelog(update) },
                    onDismissChangelog = { viewModel.dismissChangelog() },
                    onControllerStateChanged = { notifyControllerStateChanged() },
                )
            }
        }
    }

    protected fun setUpdaterController(controller: UpdaterController?) {
        activeUpdaterController = controller
        notifyControllerStateChanged()
    }

    protected fun notifyControllerStateChanged() {
        controllerStateVersion++
    }

    open fun onRefreshClick() {}
    open fun onLocalUpdateClick() {}
    open fun exportUpdate(update: Update) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HalcyonUpdatesScreen(
    uiState: UpdatesViewModel.UiState,
    changelogState: ChangelogUiState,
    updaterController: UpdaterController?,
    controllerStateVersion: Int,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onLoadChangelog: (Update) -> Unit,
    onDismissChangelog: () -> Unit,
    onControllerStateChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as UpdatesScaffoldActivity
    var menuExpanded by remember { mutableStateOf(false) }
    var showChangelogSheet by remember { mutableStateOf(false) }
    val actionDialogState = remember { mutableStateOf<AlertDialogState?>(null) }

    actionDialogState.value?.let { dialog ->
        UpdateActionDialog(
            dialog = dialog,
            onDismiss = { actionDialogState.value = null },
        )
    }

    val networkMonitor =
        remember { (context.applicationContext as UpdaterApplication).networkMonitor }
    val networkState by networkMonitor.networkState.collectAsState(
        initial = networkMonitor.currentNetworkState,
    )
    val userPreferencesRepository =
        remember { (context.applicationContext as UpdaterApplication).userPreferencesRepository }
    val streamUpdatesEnabled by userPreferencesRepository.streamUpdatesFlow.collectAsState(
        initial = true,
    )

    val updateItems = remember(
        uiState.updates,
        updaterController,
        networkState,
        streamUpdatesEnabled,
        controllerStateVersion,
    ) {
        val controller = updaterController ?: return@remember emptyList()
        val mapper = UpdateItemStateMapper(context, controller, streamUpdatesEnabled)
        uiState.updates.mapNotNull { update ->
            controller.getUpdate(update.downloadId)?.let {
                mapper.map(it, networkState)
            }
        }
    }

    val actionHandler = remember(updaterController) {
        updaterController?.let { controller ->
            UpdateActionHandler(
                activity = activity,
                updaterController = controller,
                exportUpdate = { update -> activity.exportUpdate(update) },
                showDialog = { actionDialogState.value = it },
            )
        }
    }

    val latestUpdate = uiState.updates.firstOrNull { it.downloadId == Update.LOCAL_ID }
        ?: uiState.updates.firstOrNull()
    val latestItem = updateItems.firstOrNull { it.downloadId == latestUpdate?.downloadId }
        ?: updateItems.firstOrNull()

    // Root container: Wallpaper shows through translucent system background overlay (like SetupWizard)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.30f))
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Back Button (Circular)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Center: Phone with downward arrow icon in pill/circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_system_update),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Right: 3-dots Menu Button
                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
                            .clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (latestItem != null && latestUpdate != null && latestItem.actions.overflow.isNotEmpty()) {
                            latestItem.actions.overflow.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.type.title(context)) },
                                    enabled = action.enabled,
                                    onClick = {
                                        menuExpanded = false
                                        actionHandler?.perform(action, latestUpdate)
                                        onControllerStateChanged()
                                    },
                                )
                            }
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Check for updates") },
                            onClick = {
                                menuExpanded = false
                                onRefreshClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.local_update_import)) },
                            onClick = {
                                menuExpanded = false
                                onLocalUpdateClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_preferences)) },
                            onClick = {
                                menuExpanded = false
                                onPreferencesClick()
                            },
                        )
                    }
                }
            }

            // Flexible space pushing content to bottom
            Spacer(modifier = Modifier.weight(1f))

            // --- BOTTOM CONTENT SECTION ---
            // 1. Logo Row: "h." + 4 Halcyon shapes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Text(
                    text = "h.",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 42.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_welcome_shapes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.height(26.dp),
                )
            }

            // 2. Title
            val titleText = when {
                uiState.isCheckingForUpdates -> "Checking for updates…"
                latestUpdate != null -> {
                    when {
                        latestUpdate.status == UpdateStatus.UPDATED_NEED_REBOOT -> "Restart to complete update"
                        latestItem?.progress != null && latestUpdate.status == UpdateStatus.INSTALLING -> "Installing system update…"
                        latestItem?.progress != null -> "Downloading update…"
                        latestUpdate.downloadId == Update.LOCAL_ID -> "Local update ready"
                        else -> "System update available"
                    }
                }
                else -> "Your system is up to date"
            }

            Text(
                text = titleText,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 42.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // 3. Subtitle / Details
            val currentVersion = remember {
                DeviceInfoUtils.halcyonVersion.trim()
            }

            val subtitleText = when {
                uiState.isCheckingForUpdates -> "Looking for newer versions of Halcyon…"
                latestUpdate != null -> {
                    val formattedDate = formatReleaseDate(latestUpdate.timestamp)
                    val formattedSize = Formatter.formatShortFileSize(context, latestUpdate.fileSize)
                    val versionPrefix = if (latestUpdate.version.startsWith("Halcyon", ignoreCase = true)) {
                        latestUpdate.version
                    } else if (latestUpdate.version.isNotEmpty()) {
                        "Halcyon ${latestUpdate.version}"
                    } else {
                        "Halcyon"
                    }

                    when (latestUpdate.status) {
                        UpdateStatus.UPDATED_NEED_REBOOT -> "Restart your device to apply $versionPrefix."
                        UpdateStatus.INSTALLING -> "Installing in the background. You can keep using your device."
                        else -> {
                            if (latestUpdate.downloadId == Update.LOCAL_ID) {
                                "Local package • $formattedSize • $formattedDate"
                            } else {
                                "$versionPrefix • $formattedSize • $formattedDate"
                            }
                        }
                    }
                }
                else -> {
                    val versionLabel = if (currentVersion.isNotEmpty()) "Halcyon $currentVersion" else "Halcyon"
                    val lastChecked = formatLastChecked(context, uiState.lastCheckedTimestamp)
                    if (lastChecked.isNotEmpty()) {
                        "$versionLabel • $lastChecked"
                    } else {
                        "$versionLabel • Android ${DeviceInfoUtils.androidVersion}"
                    }
                }
            }

            Text(
                text = subtitleText,
                fontFamily = GoogleSans,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f),
                modifier = Modifier.padding(bottom = 6.dp),
            )

            // 4. "What's new? >" clickable row (shown when online update is available)
            if (latestUpdate != null && latestUpdate.downloadId != Update.LOCAL_ID) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onLoadChangelog(latestUpdate)
                            showChangelogSheet = true
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "What's new?",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 5. Progress indicator if active
            latestItem?.progress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                when (progress) {
                    is ProgressState.Determinate -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            LinearProgressIndicator(
                                progress = { progress.percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${progress.downloadedSize} (${progress.percent.toInt()}%)",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                )
                                if (progress.eta.isNotEmpty()) {
                                    Text(
                                        text = progress.eta,
                                        fontFamily = GoogleSans,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                    is ProgressState.Indeterminate -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Primary Action Button
            val buttonText = when {
                uiState.isCheckingForUpdates -> "Checking for updates…"
                latestUpdate?.status == UpdateStatus.UPDATED_NEED_REBOOT -> "Restart now"
                latestItem != null -> {
                    val primaryType = latestItem.actions.primary.type
                    when (primaryType) {
                        UpdateActionType.START_INSTALL -> {
                            if (latestUpdate?.downloadId == Update.LOCAL_ID) {
                                "Install update"
                            } else {
                                "Download and install"
                            }
                        }
                        UpdateActionType.START_DOWNLOAD -> "Download"
                        UpdateActionType.REBOOT -> "Restart now"
                        else -> primaryType.title(context)
                    }
                }
                latestUpdate != null -> {
                    if (latestUpdate.downloadId == Update.LOCAL_ID) {
                        "Install update"
                    } else {
                        "Download and install"
                    }
                }
                else -> "Check for updates"
            }

            Button(
                onClick = {
                    if (latestItem != null && latestUpdate != null) {
                        val primaryAction = latestItem.actions.primary
                        actionHandler?.perform(primaryAction, latestUpdate)
                        onControllerStateChanged()
                    } else {
                        onRefreshClick()
                    }
                },
                enabled = !uiState.isCheckingForUpdates && (latestItem == null || latestItem.actions.primary.enabled),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                ),
            ) {
                if (uiState.isCheckingForUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = buttonText,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
            }

            // Secondary Action / Local Update Actions
            if (latestItem?.actions?.secondary != null) {
                val secondaryAction = latestItem.actions.secondary!!
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        if (latestUpdate != null) {
                            actionHandler?.perform(secondaryAction, latestUpdate)
                            onControllerStateChanged()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = secondaryAction.type.title(context),
                        fontFamily = GoogleSans,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                    )
                }
            } else if (latestUpdate?.downloadId == Update.LOCAL_ID &&
                latestUpdate.status != UpdateStatus.INSTALLING &&
                latestUpdate.status != UpdateStatus.UPDATED_NEED_REBOOT
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        val deleteAction = latestItem?.actions?.overflow?.firstOrNull { it.type == UpdateActionType.DELETE }
                        if (deleteAction != null) {
                            actionHandler?.perform(deleteAction, latestUpdate)
                        } else {
                            updaterController?.deleteUpdate(latestUpdate.downloadId)
                        }
                        onControllerStateChanged()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Delete local update",
                        fontFamily = GoogleSans,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // --- CHANGELOG BOTTOM SHEET ---
        if (showChangelogSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showChangelogSheet = false
                    onDismissChangelog()
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(48.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp),
                ) {
                    Text(
                        text = "What's new?",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    when (changelogState) {
                        is ChangelogUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                        is ChangelogUiState.Success -> {
                            val changelog = changelogState.changelog

                            if (changelog.deviceChangelog.isNotEmpty()) {
                                Text(
                                    text = "Device changelog",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                                )
                                changelog.deviceChangelog.forEach { item ->
                                    Text(
                                        text = "• $item",
                                        fontFamily = GoogleSans,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }

                            if (changelog.systemChangelog.isNotEmpty()) {
                                Text(
                                    text = "System changelog",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                                )
                                changelog.systemChangelog.forEach { item ->
                                    Text(
                                        text = "• $item",
                                        fontFamily = GoogleSans,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }

                            if (changelog.deviceChangelog.isEmpty() && changelog.systemChangelog.isEmpty()) {
                                Text(
                                    text = "No detailed changelog entries available.",
                                    fontFamily = GoogleSans,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }

                        is ChangelogUiState.Error -> {
                            Text(
                                text = changelogState.message,
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }

                        ChangelogUiState.Initial -> Unit
                    }
                }
            }
        }
    }
}

private fun formatReleaseDate(timestamp: Long): String {
    val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    return SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(millis))
}

private fun formatLastChecked(context: Context, timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val time = AndroidDateFormat.getTimeFormat(context).format(Date(timestampMillis))
    return if (DateUtils.isToday(timestampMillis)) {
        context.getString(R.string.header_last_updates_check_time, time)
    } else {
        val date = AndroidDateFormat.getDateFormat(context).format(Date(timestampMillis))
        context.getString(R.string.header_last_updates_check, date, time)
    }
}

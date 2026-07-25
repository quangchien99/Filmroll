package com.filmroll.camera.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.action_download
import com.filmroll.camera.resources.action_later
import com.filmroll.camera.resources.download_luts
import com.filmroll.camera.resources.download_luts_message
import com.filmroll.camera.resources.downloading_luts
import com.filmroll.camera.resources.downloading_luts_count
import org.jetbrains.compose.resources.stringResource

@Composable
fun LutDownloadDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!isVisible) return

    AppDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.CloudDownload,
        title = stringResource(Res.string.download_luts),
        message = stringResource(Res.string.download_luts_message),
        confirmLabel = stringResource(Res.string.action_download),
        onConfirm = onConfirm,
        dismissLabel = stringResource(Res.string.action_later),
    )
}

@Composable
fun LutDownloadProgressDialog(
    isVisible: Boolean,
    current: Int,
    total: Int,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    AppDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.CloudDownload,
        title = stringResource(Res.string.downloading_luts),
        dismissOnOutsideClick = false,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.downloading_luts_count, current, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            DeterminateBar(fraction = if (total > 0) current.toFloat() / total else 0f)
        }
    }
}

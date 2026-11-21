package com.salahmute.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salahmute.app.R
import com.salahmute.app.data.BackupManager
import com.salahmute.app.data.FoundBackup
import com.salahmute.app.data.Store
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Dedicated Restore Backup screen:
 * 1) Automatically checks `Documents/SalahMute` (and offers 1-tap folder check on Android 11+ reinstalls)
 * 2) Lets the user pick a `.salahmute` backup file from any other folder on the phone
 * 3) Opens the "Backup found" preview window with Restore & Delete options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    onRestored: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var autoFound by remember { mutableStateOf<FoundBackup?>(null) }
    var dialogBackup by remember { mutableStateOf<FoundBackup?>(null) }
    var confirmDeleteBackup by remember { mutableStateOf<FoundBackup?>(null) }

    fun refreshAutoScan() {
        autoFound = BackupManager.findReadableLocalBackup(ctx)
    }

    LaunchedEffect(Unit) {
        refreshAutoScan()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            BackupManager.saveTreeUri(ctx, treeUri)
        } catch (_: SecurityException) {
        }
        val found = BackupManager.findBackupInTree(ctx, treeUri)
        if (found != null) {
            autoFound = found
            dialogBackup = found
        } else {
            Toast.makeText(ctx, R.string.restore_none_found, Toast.LENGTH_LONG).show()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { fileUri ->
        if (fileUri == null) return@rememberLauncherForActivityResult
        val found = BackupManager.readBackupFromUri(ctx, fileUri)
        if (found != null) {
            dialogBackup = found
        } else {
            Toast.makeText(ctx, R.string.import_bad, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_screen_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.restore_screen_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // If a backup was immediately found in Documents/SalahMute, highlight it right at the top
            autoFound?.let { found ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.backup_found_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        BackupSummaryLines(found)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { dialogBackup = found },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.restore_this_backup))
                            }
                            OutlinedButton(
                                onClick = { confirmDeleteBackup = found },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.backup_delete), Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Card 1: Auto-check SalahMute folder (Documents/SalahMute)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.restore_folder_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.restore_folder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { folderLauncher.launch(BackupManager.salahMuteFolderInitialUri()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.restore_check_folder))
                    }
                }
            }

            // Card 2: Pick another folder or file
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.restore_other_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.restore_other_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { fileLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.restore_pick_file))
                    }
                }
            }
        }
    }

    dialogBackup?.let { found ->
        BackupFoundDialog(
            found = found,
            isStartupPrompt = false,
            onRestore = {
                Store.restoreFullBackup(ctx, found.data)
                BackupManager.markDeviceSetupDone(ctx)
                dialogBackup = null
                Toast.makeText(ctx, R.string.backup_restored_ok, Toast.LENGTH_SHORT).show()
                onRestored()
            },
            onDelete = {
                dialogBackup = null
                confirmDeleteBackup = found
            },
            onDismiss = { dialogBackup = null },
        )
    }

    confirmDeleteBackup?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteBackup = null },
            title = { Text(stringResource(R.string.backup_delete)) },
            text = { Text(stringResource(R.string.backup_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        BackupManager.deleteLocalBackup(ctx, target.sourceUri)
                        confirmDeleteBackup = null
                        refreshAutoScan()
                        Toast.makeText(ctx, R.string.backup_deleted_ok, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteBackup = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Friendly "Backup found" window shown automatically on reinstall or when a backup is picked,
 * with clear Restore and Delete options.
 */
@Composable
fun BackupFoundDialog(
    found: FoundBackup,
    isStartupPrompt: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.backup_delete)) },
            text = { Text(stringResource(R.string.backup_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.backup_found_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.backup_found_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BackupSummaryLines(found)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onRestore,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_this_backup))
                }
                OutlinedButton(
                    onClick = {
                        if (isStartupPrompt) confirmDelete = true else onDelete()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(if (isStartupPrompt) R.string.delete_and_start_fresh else R.string.backup_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (!isStartupPrompt && onDismiss != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun BackupSummaryLines(found: FoundBackup) {
    Text(
        stringResource(R.string.backup_found_summary, found.profileCount, found.slotCount, found.placeCount),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (found.cityName.isNotBlank()) {
        Text(
            found.cityName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (found.createdAtMs > 0L) {
        val dateText = remember(found.createdAtMs) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .format(Instant.ofEpochMilli(found.createdAtMs).atZone(ZoneId.systemDefault()))
        }
        Text(
            stringResource(R.string.backup_found_date, dateText),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

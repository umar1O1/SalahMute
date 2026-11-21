package com.salahmute.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.share.Bundle
import com.salahmute.app.share.ShareCodec
import java.io.File

private const val MAX_QR_CHARS = 2200

private fun makeQr(text: String): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2)
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 768, 768, hints)
    val px = IntArray(m.width * m.height)
    for (y in 0 until m.height) for (x in 0 until m.width) px[y * m.width + x] = if (m[x, y]) Color.BLACK else Color.WHITE
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

fun readUriText(ctx: Context, uri: android.net.Uri, maxBytes: Int = 1_000_000): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { s ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
            val n = s.read(buf, 0, minOf(buf.size, maxBytes - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        out.toByteArray().toString(Charsets.UTF_8)
    }
} catch (_: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(onClose: () -> Unit, onImportText: (String) -> Unit) {
    val ctx = LocalContext.current
    val data by Store.data.collectAsState()
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var includeLocations by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportText(readUriText(ctx, uri) ?: "")
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let(onImportText)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Card 1: Send
            EditorCard(title = stringResource(R.string.share_send_title)) {
                Text(
                    stringResource(R.string.share_send_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data.geoZones.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${stringResource(R.string.share_include_locations)} (${data.geoZones.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = includeLocations, onCheckedChange = { includeLocations = it })
                    }
                }
                Button(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
                        val f = File(dir, "SalahMute.salahmute")
                        f.writeText(ShareCodec.toJson(Store.value, includeLocations))
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, null))
                    },
                ) { Text(stringResource(R.string.share_file)) }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val code = ShareCodec.toCode(Store.value, includeLocations)
                            if (code.length > MAX_QR_CHARS) Toast.makeText(ctx, R.string.qr_too_big, Toast.LENGTH_LONG).show()
                            else qr = makeQr(code)
                        },
                    ) { Text(stringResource(R.string.show_qr)) }

                    OutlinedButton(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val cm = ctx.getSystemService(ClipboardManager::class.java)
                            cm.setPrimaryClip(ClipData.newPlainText("SalahMute", ShareCodec.toCode(Store.value, includeLocations)))
                            Toast.makeText(ctx, R.string.code_copied, Toast.LENGTH_SHORT).show()
                        },
                    ) { Text(stringResource(R.string.share_code)) }
                }
            }

            // Card 2: Import
            EditorCard(title = stringResource(R.string.share_import_title)) {
                Text(
                    stringResource(R.string.share_import_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scan.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(ctx.getString(R.string.scan_prompt))
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    },
                ) { Text(stringResource(R.string.import_scan)) }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { openFile.launch(arrayOf("*/*")) },
                    ) { Text(stringResource(R.string.import_file)) }

                    OutlinedButton(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { pasting = true },
                    ) { Text(stringResource(R.string.import_paste)) }
                }
            }
        }
    }

    qr?.let { bmp ->
        AlertDialog(
            onDismissRequest = { qr = null },
            confirmButton = { TextButton(onClick = { qr = null }) { Text(stringResource(R.string.ok)) } },
            text = {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.show_qr),
                    modifier = Modifier.fillMaxWidth().size(300.dp),
                )
            },
        )
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text(stringResource(R.string.import_paste)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.paste_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    pasting = false
                    onImportText(text)
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun ImportDialog(bundle: Bundle, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    fun done(block: () -> Unit) {
        block()
        Toast.makeText(ctx, R.string.import_ok, Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_preview)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val summary = if (bundle.geoZones.isNotEmpty()) {
                    stringResource(R.string.import_summary_loc, bundle.profiles.size, bundle.slotCount, bundle.geoZones.size)
                } else {
                    stringResource(R.string.import_summary, bundle.profiles.size, bundle.slotCount)
                }
                Text(summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                bundle.profiles.forEach { p ->
                    Text("• ${p.name} (${p.slots.size})", style = MaterialTheme.typography.bodyMedium)
                }
                if (bundle.geoZones.isNotEmpty()) {
                    Text(stringResource(R.string.tab_locations), style = MaterialTheme.typography.labelLarge)
                    bundle.geoZones.forEach { z ->
                        val name = z.label.ifBlank { stringResource(R.string.location) }
                        Text("• $name (${z.radiusMeters} m)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ImportChoiceRow(
                    title = stringResource(R.string.import_add),
                    desc = stringResource(R.string.import_add_desc),
                    highlighted = true,
                    onClick = { done { Store.importAsProfiles(ctx, bundle) } },
                )
                ImportChoiceRow(
                    title = stringResource(R.string.import_merge),
                    desc = stringResource(R.string.import_merge_desc),
                    highlighted = false,
                    onClick = { done { Store.importMerge(ctx, bundle) } },
                )
                ImportChoiceRow(
                    title = stringResource(R.string.import_replace),
                    desc = stringResource(R.string.import_replace_desc),
                    highlighted = false,
                    onClick = { done { Store.importReplace(ctx, bundle) } },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ImportChoiceRow(title: String, desc: String, highlighted: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (!highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

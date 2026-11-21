package com.salahmute.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.model.Profile

@Composable
fun ProfilesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val data by Store.data.collectAsState()
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles)) },
        text = {
            Column {
                data.profiles.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Store.setActiveProfile(ctx, p.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p.id == data.active.id, onClick = { Store.setActiveProfile(ctx, p.id) })
                        Text(p.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renaming = p }) { Icon(Icons.Filled.Edit, stringResource(R.string.rename)) }
                        IconButton(onClick = {
                            if (data.profiles.size > 1) deleting = p
                            else Toast.makeText(ctx, R.string.keep_one_profile, Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { creating = true }) { Text(stringResource(R.string.new_profile)) }
                    TextButton(onClick = { Store.duplicateProfile(ctx, data.active.id) }) { Text(stringResource(R.string.duplicate)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )

    renaming?.let { p ->
        NameDialog(stringResource(R.string.rename), p.name, { renaming = null }) {
            Store.renameProfile(ctx, p.id, it)
            renaming = null
        }
    }
    deleting?.let { p ->
        ConfirmDialog(stringResource(R.string.delete_confirm, p.name), { deleting = null }) {
            Store.deleteProfile(ctx, p.id)
            deleting = null
        }
    }
    if (creating) {
        NameDialog(stringResource(R.string.new_profile), "", { creating = false }) {
            Store.addProfile(ctx, it)
            creating = false
        }
    }
}

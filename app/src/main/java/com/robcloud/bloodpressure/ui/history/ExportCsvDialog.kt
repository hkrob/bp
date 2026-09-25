package com.robcloud.bloodpressure.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user pick a destination folder (via the system folder picker) and type their own
 * filename, instead of relying on the OS "Save as" dialog — some SAF providers (notably
 * Google Drive's document provider) only offer to overwrite an existing file there rather
 * than letting the user type an arbitrary new name.
 */
@Composable
fun ExportCsvDialog(
    defaultFileName: String,
    onDismiss: () -> Unit,
    onExport: (folderUri: Uri, fileName: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var folderUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var folderLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf(defaultFileName) }
    // A one-off export only needs the temporary grant the picker gives; no persistable grant.
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            folderUri = uri
            coroutineScope.launch {
                folderLabel = withContext(Dispatchers.IO) {
                    runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Export CSV", style = MaterialTheme.typography.titleLarge)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Destination folder", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(folderLabel ?: "Choose folder", maxLines = 1)
                    }
                }

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    suffix = { Text(".csv") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            val uri = folderUri ?: return@TextButton
                            val name = fileName.removeSuffix(".csv").trim()
                            if (name.isEmpty()) return@TextButton
                            onExport(uri, "$name.csv")
                        },
                        enabled = folderUri != null && fileName.removeSuffix(".csv").trim().isNotEmpty()
                    ) {
                        Text("Export")
                    }
                }
            }
        }
    }
}

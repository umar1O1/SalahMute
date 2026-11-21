package com.salahmute.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.salahmute.app.model.AppData
import com.salahmute.app.model.AppJson
import com.salahmute.app.model.Profile
import com.salahmute.app.model.Slot
import com.salahmute.app.share.ShareCodec
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@Serializable
data class LocalBackupPayload(
    val format: String = "salahmute-backup",
    val version: Int = 1,
    val createdAtMs: Long = 0L,
    val data: AppData,
)

enum class BackupOrigin { AUTO_CLOUD, LOCAL_FOLDER, PICKED_FILE }

data class FoundBackup(
    val data: AppData,
    val createdAtMs: Long,
    val origin: BackupOrigin,
    val sourceUri: Uri? = null,
    val displayPath: String = "Documents/SalahMute",
) {
    val profileCount: Int get() = data.profiles.size
    val slotCount: Int get() = data.profiles.sumOf { p -> p.slots.count { !it.archived } }
    val placeCount: Int get() = data.geoZones.size
    val cityName: String get() = data.settings.locationName
}

/**
 * Manages local SalahMute backups stored in `Documents/SalahMute/SalahMute-Backup.salahmute`
 * (and optional user-picked folders/files) plus device-local install tracking so reinstalls
 * can detect and offer a "Backup found" window cleanly.
 */
object BackupManager {
    const val FOLDER_DISPLAY = "Documents/SalahMute"
    const val BACKUP_FILENAME = "SalahMute-Backup.salahmute"
    private const val BACKUP_FALLBACK_FILENAME = "SalahMute-Backup.json"
    private const val MAX_BACKUP_BYTES = 1_000_000
    private const val PREF_NAME = "salah_device"
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isDeviceSetupDone(ctx: Context): Boolean = prefs(ctx).getBoolean("setup_done", false)

    fun markDeviceSetupDone(ctx: Context) {
        prefs(ctx).edit().putBoolean("setup_done", true).commit()
    }

    fun isAutoBackupEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_backup", true)

    fun setAutoBackupEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("auto_backup", enabled).commit()
    }

    fun lastBackupAt(ctx: Context): Long = prefs(ctx).getLong("last_backup_ms", 0L)

    fun savedTreeUri(ctx: Context): Uri? =
        prefs(ctx).getString("tree_uri", null)?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun saveTreeUri(ctx: Context, uri: Uri?) {
        prefs(ctx).edit().apply {
            if (uri == null) remove("tree_uri") else putString("tree_uri", uri.toString())
        }.commit()
    }

    /** Initial URI hint so Android's folder/file picker opens straight into Documents/SalahMute. */
    fun salahMuteFolderInitialUri(): Uri =
        Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents%2FSalahMute")

    /** Serializes [data] into a full SalahMute local backup JSON string. */
    fun encode(data: AppData, nowMs: Long = System.currentTimeMillis()): String =
        AppJson.encodeToString(
            LocalBackupPayload.serializer(),
            LocalBackupPayload(createdAtMs = nowMs, data = data.copy(settings = data.settings.copy(onboarded = true))),
        )

    /** Pure, JVM-testable parser supporting both full local backups and exported `.salahmute` share files. */
    fun decode(raw: String): Pair<AppData, Long>? {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_BACKUP_BYTES) return null
        try {
            val payload = AppJson.decodeFromString(LocalBackupPayload.serializer(), text)
            if (payload.format == "salahmute-backup" && payload.data.profiles.isNotEmpty()) {
                return sanitizeAppData(payload.data) to payload.createdAtMs.coerceAtLeast(0L)
            }
        } catch (_: Exception) {
        }
        // Fallback: also accept a shared `.salahmute` bundle file cleanly
        val bundle = ShareCodec.parse(text) ?: return null
        val activeId = bundle.profiles[bundle.activeProfile.coerceIn(0, bundle.profiles.lastIndex)].id
        val appData = AppData(
            profiles = bundle.profiles,
            activeProfileId = activeId,
            settings = com.salahmute.app.model.Settings(
                method = bundle.method,
                hanafi = bundle.hanafi,
                fiqh = bundle.fiqh,
                onboarded = true,
            ),
            geoZones = bundle.geoZones,
        )
        return sanitizeAppData(appData) to 0L
    }

    /** Clamps untrusted backup fields so crafted files can never inject invalid coords, minutes, or huge lists. */
    internal fun sanitizeAppData(raw: AppData): AppData {
        val safeProfiles = raw.profiles.take(20).map { p ->
            Profile(
                id = p.id.take(64).ifBlank { UUID.randomUUID().toString() },
                name = p.name.take(40).ifBlank { "Profile" },
                slots = p.slots.take(200).map { s ->
                    s.copy(
                        id = s.id.take(64).ifBlank { UUID.randomUUID().toString() },
                        label = s.label.take(40),
                        startMin = s.startMin.coerceIn(0, 1439),
                        endMin = s.endMin.coerceIn(0, 1439),
                        before = s.before.coerceIn(0, 180),
                        after = s.after.coerceIn(0, 180),
                        days = s.days.filter { it in 1..7 }.distinct().ifEmpty { Slot.ALL_DAYS },
                    )
                },
            )
        }.ifEmpty { listOf(Profile(id = "default", name = "Default")) }
        val safeActiveId = if (safeProfiles.any { it.id == raw.activeProfileId }) raw.activeProfileId else safeProfiles.first().id
        val safeGeoZones = raw.geoZones.take(50).mapNotNull { z ->
            if (!z.lat.isFinite() || !z.lon.isFinite() || z.lat !in -90.0..90.0 || z.lon !in -180.0..180.0) {
                null
            } else {
                z.copy(
                    id = z.id.take(64).ifBlank { UUID.randomUUID().toString() },
                    label = z.label.take(40),
                    radiusMeters = z.radiusMeters.coerceIn(30, 5000),
                )
            }
        }
        val s = raw.settings
        val safeLat = s.lat?.takeIf { it.isFinite() && it in -90.0..90.0 }
        val safeLon = s.lon?.takeIf { it.isFinite() && it in -180.0..180.0 }
        val safeHex = s.accentHex.trim().take(12)
        return raw.copy(
            profiles = safeProfiles,
            activeProfileId = safeActiveId,
            geoZones = safeGeoZones,
            settings = s.copy(
                lat = if (safeLat != null && safeLon != null) safeLat else null,
                lon = if (safeLat != null && safeLon != null) safeLon else null,
                locationName = s.locationName.take(80),
                countryName = s.countryName.take(60),
                accentHex = safeHex,
                onboarded = true,
            ),
        )
    }

    /**
     * Detects whether this is a fresh reinstall whose data was automatically restored
     * or already has a readable backup in Documents/SalahMute.
     * Returns null on normal app launches and normal in-place updates.
     */
    fun detectStartupBackup(ctx: Context): FoundBackup? {
        val appCtx = ctx.applicationContext
        if (isDeviceSetupDone(appCtx)) return null

        val data = Store.value
        val hasMeaningfulData = data.settings.onboarded ||
            data.profiles.any { p -> p.slots.isNotEmpty() } ||
            data.geoZones.isNotEmpty()

        val statePrefs = appCtx.getSharedPreferences("salah_state", Context.MODE_PRIVATE)
        val isInPlaceUpdate = statePrefs.all.isNotEmpty() || runCatching {
            val pi = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            pi.lastUpdateTime > pi.firstInstallTime + 60_000L
        }.getOrDefault(false)

        if (hasMeaningfulData) {
            if (isInPlaceUpdate) {
                markDeviceSetupDone(appCtx)
                return null
            }
            return FoundBackup(
                data = sanitizeAppData(data),
                createdAtMs = lastBackupAt(appCtx),
                origin = BackupOrigin.AUTO_CLOUD,
                displayPath = FOLDER_DISPLAY,
            )
        }

        return findReadableLocalBackup(appCtx)
    }

    /** Saves/updates `Documents/SalahMute/SalahMute-Backup.salahmute` asynchronously. */
    fun autoBackupIfEnabled(ctx: Context, data: AppData) {
        val appCtx = ctx.applicationContext
        if (!isAutoBackupEnabled(appCtx)) return
        val hasData = data.profiles.any { it.slots.isNotEmpty() } || data.geoZones.isNotEmpty()
        if (!hasData) return
        ioExecutor.execute {
            saveLocalBackup(appCtx, data)
        }
    }

    /** Writes/updates the local backup in `Documents/SalahMute/` (and user-granted folder if set). */
    fun saveLocalBackup(ctx: Context, data: AppData): Boolean {
        val appCtx = ctx.applicationContext
        val now = System.currentTimeMillis()
        val content = encode(data, now)
        var saved = false

        // 1) If user granted a folder tree (e.g. Documents/SalahMute on reinstall), update inside it too
        savedTreeUri(appCtx)?.let { treeUri ->
            if (writeToTree(appCtx, treeUri, content)) saved = true
        }

        // 2) Standard Documents/SalahMute via MediaStore VOLUME_EXTERNAL_PRIMARY on Android 10+ (zero permissions!)
        if (Build.VERSION.SDK_INT >= 29) {
            if (writeToMediaStore(appCtx, content)) saved = true
        } else {
            // Android 8-9: write to public Documents if permitted, else external app-specific Documents backup
            val dirs = listOfNotNull(
                File(
                    @Suppress("DEPRECATION")
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "SalahMute",
                ),
                appCtx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "SalahMute") },
            )
            for (dir in dirs) {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    File(dir, BACKUP_FILENAME).writeText(content)
                    saved = true
                    break
                } catch (_: Exception) {
                }
            }
        }

        if (saved) {
            prefs(appCtx).edit().putLong("last_backup_ms", now).commit()
        }
        return saved
    }

    /** Saves a backup copy to a user-picked file URI (from CreateDocument). */
    fun saveToDocumentUri(ctx: Context, uri: Uri, data: AppData): Boolean {
        val appCtx = ctx.applicationContext
        val now = System.currentTimeMillis()
        return try {
            appCtx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(encode(data, now).toByteArray(Charsets.UTF_8))
            } ?: return false
            prefs(appCtx).edit().putLong("last_backup_ms", now).commit()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Checks if a readable backup currently exists in `Documents/SalahMute` or a granted folder. */
    fun findReadableLocalBackup(ctx: Context): FoundBackup? {
        val appCtx = ctx.applicationContext
        savedTreeUri(appCtx)?.let { tree ->
            findBackupInTree(appCtx, tree)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            findMediaStoreUri(appCtx)?.let { uri ->
                readBackupFromUri(appCtx, uri, BackupOrigin.LOCAL_FOLDER, FOLDER_DISPLAY)?.let { return it }
            }
        } else {
            val candidates = listOfNotNull(
                File(
                    @Suppress("DEPRECATION")
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "SalahMute/$BACKUP_FILENAME",
                ),
                appCtx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "SalahMute/$BACKUP_FILENAME") },
            )
            for (file in candidates) {
                if (file.exists() && file.canRead() && file.length() <= MAX_BACKUP_BYTES) {
                    val parsed = runCatching { decode(file.readText()) }.getOrNull()
                    if (parsed != null) {
                        return FoundBackup(
                            data = parsed.first,
                            createdAtMs = parsed.second.takeIf { it > 0L } ?: file.lastModified(),
                            origin = BackupOrigin.LOCAL_FOLDER,
                            sourceUri = Uri.fromFile(file),
                            displayPath = FOLDER_DISPLAY,
                        )
                    }
                }
            }
        }
        return null
    }

    /** Scans a SAF folder URI (e.g. `Documents/SalahMute` chosen via OpenDocumentTree) for a backup file. */
    fun findBackupInTree(ctx: Context, treeUri: Uri): FoundBackup? {
        val appCtx = ctx.applicationContext
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            var bestId: String? = null
            var bestName = BACKUP_FILENAME
            var bestMod = -1L
            appCtx.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mod = c.getLong(2)
                    if (name.equals(BACKUP_FILENAME, ignoreCase = true) ||
                        name.equals(BACKUP_FALLBACK_FILENAME, ignoreCase = true)
                    ) {
                        if (mod >= bestMod) {
                            bestId = id
                            bestName = name
                            bestMod = mod
                        }
                    } else if ((name.endsWith(".salahmute", ignoreCase = true) ||
                            (name.startsWith("SalahMute", ignoreCase = true) && name.endsWith(".json", ignoreCase = true))) &&
                        mod > bestMod
                    ) {
                        bestId = id
                        bestName = name
                        bestMod = mod
                    }
                }
            }
            val foundId = bestId ?: return null
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId)
            readBackupFromUri(appCtx, docUri, BackupOrigin.LOCAL_FOLDER, "$FOLDER_DISPLAY/$bestName")
        } catch (_: Exception) {
            null
        }
    }

    /** Reads and decodes a backup from any content URI (strictly bounded at 1 MB for safety). */
    fun readBackupFromUri(
        ctx: Context,
        uri: Uri,
        origin: BackupOrigin = BackupOrigin.PICKED_FILE,
        displayPath: String = BACKUP_FILENAME,
    ): FoundBackup? {
        val raw = try {
            ctx.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (total < MAX_BACKUP_BYTES) {
                    val n = stream.read(buf, 0, minOf(buf.size, MAX_BACKUP_BYTES - total))
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toByteArray().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        } ?: return null
        val (data, ts) = decode(raw) ?: return null
        return FoundBackup(
            data = data,
            createdAtMs = ts,
            origin = origin,
            sourceUri = uri,
            displayPath = displayPath,
        )
    }

    /** Deletes the local backup file from `Documents/SalahMute/` and/or a specific [sourceUri]. */
    fun deleteLocalBackup(ctx: Context, sourceUri: Uri? = null): Boolean {
        val appCtx = ctx.applicationContext
        var deleted = false

        if (sourceUri != null) {
            deleted = deleteUri(appCtx, sourceUri) || deleted
        }

        savedTreeUri(appCtx)?.let { tree ->
            findBackupInTree(appCtx, tree)?.sourceUri?.let { uri ->
                if (deleteUri(appCtx, uri)) deleted = true
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            findMediaStoreUri(appCtx)?.let { uri ->
                if (deleteUri(appCtx, uri)) deleted = true
            }
        } else {
            val files = listOfNotNull(
                File(
                    @Suppress("DEPRECATION")
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "SalahMute/$BACKUP_FILENAME",
                ),
                appCtx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "SalahMute/$BACKUP_FILENAME") },
            )
            for (f in files) {
                if (f.exists() && f.delete()) deleted = true
            }
        }

        if (deleted) {
            prefs(appCtx).edit().putLong("last_backup_ms", 0L).commit()
        }
        return deleted
    }

    private fun deleteUri(ctx: Context, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() } == true
            } else if (DocumentsContract.isDocumentUri(ctx, uri)) {
                DocumentsContract.deleteDocument(ctx.contentResolver, uri)
            } else {
                ctx.contentResolver.delete(uri, null, null) > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun findMediaStoreUri(ctx: Context): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val collections = listOf(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        )
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "(${MediaStore.MediaColumns.DISPLAY_NAME} = ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} = ?) " +
            "AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(BACKUP_FILENAME, BACKUP_FALLBACK_FILENAME, "%SalahMute%")
        for (collection in collections) {
            try {
                ctx.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    args,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )?.use { c ->
                    if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0))
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun writeToMediaStore(ctx: Context, content: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = ctx.contentResolver
        val bytes = content.toByteArray(Charsets.UTF_8)

        // 1) Try overwriting our own existing backup row in place (cleanest, avoids duplicate "(1)" files)
        val existing = findMediaStoreUri(ctx)
        if (existing != null) {
            try {
                resolver.openOutputStream(existing, "rwt")?.use { it.write(bytes) }
                return true
            } catch (_: Exception) {
                // If in-place truncate isn't supported by ROM, delete our stale row and insert cleanly below
                try {
                    resolver.delete(existing, null, null)
                } catch (_: Exception) {
                }
            }
        }

        // 2) Insert into VOLUME_EXTERNAL_PRIMARY (required on Android 11-15!) with progressive MIME/collection fallback
        val attempts = listOf(
            Triple(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                BACKUP_FILENAME,
                "application/octet-stream" to "${Environment.DIRECTORY_DOCUMENTS}/SalahMute",
            ),
            Triple(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                BACKUP_FALLBACK_FILENAME,
                "application/json" to "${Environment.DIRECTORY_DOCUMENTS}/SalahMute",
            ),
            Triple(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                BACKUP_FILENAME,
                "application/octet-stream" to "${Environment.DIRECTORY_DOWNLOADS}/SalahMute",
            ),
            Triple(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                BACKUP_FALLBACK_FILENAME,
                "application/json" to "${Environment.DIRECTORY_DOWNLOADS}/SalahMute",
            ),
        )

        for ((collection, filename, mimeAndPath) in attempts) {
            val (mime, relPath) = mimeAndPath
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            var inserted: Uri? = null
            try {
                inserted = resolver.insert(collection, values) ?: continue
                resolver.openOutputStream(inserted)?.use { out ->
                    out.write(bytes)
                } ?: continue
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(inserted, done, null, null)
                return true
            } catch (_: Exception) {
                inserted?.let { runCatching { resolver.delete(it, null, null) } }
            }
        }
        return false
    }

    private fun writeToTree(ctx: Context, treeUri: Uri, content: String): Boolean {
        return try {
            val resolver = ctx.contentResolver
            val existing = findBackupInTree(ctx, treeUri)?.sourceUri
            val targetUri = existing ?: run {
                val parentId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", BACKUP_FILENAME)
            } ?: return false
            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: resolver.openOutputStream(targetUri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }
}

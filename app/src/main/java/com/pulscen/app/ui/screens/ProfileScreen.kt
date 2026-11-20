package com.pulscen.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulscen.app.BuildConfig
import com.pulscen.app.data.Dates
import com.pulscen.app.ui.AppViewModel
import com.pulscen.app.ui.AvatarView
import com.pulscen.app.ui.BrandMark
import com.pulscen.app.update.UpdateState
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val autoSync by vm.autoSync.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val profile = bundle?.profile

    var name by remember(profile?.updatedAt) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile?.updatedAt) { mutableStateOf(profile?.avatarDataUrl) }
    var showAbout by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var pendingApk by remember { mutableStateOf<File?>(null) }
    var showInstallPermission by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            avatar = readAvatarDataUrl(context, uri)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingApk
        pendingApk = null
        if (file != null && canInstall(context)) startApkInstall(context, file)
    }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message!!)
            vm.consumeMessage()
        }
    }

    LaunchedEffect(updateState) {
        val ready = updateState as? UpdateState.Ready ?: return@LaunchedEffect
        if (!canInstall(context)) {
            pendingApk = ready.file
            showInstallPermission = true
        } else {
            startApkInstall(context, ready.file)
        }
        vm.consumeReadyUpdate()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.goHome() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Профиль",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarView(dataUrl = avatar, name = name.ifBlank { "Профиль" }, size = 92.dp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { picker.launch("image/*") }) {
                                Text("Изменить фото")
                            }
                            if (avatar != null) {
                                TextButton(onClick = { avatar = "" }) { Text("Убрать") }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Имя") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bundle?.email.orEmpty(),
                            onValueChange = {},
                            label = { Text("Email") },
                            readOnly = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                vm.saveProfile(
                                    name,
                                    when {
                                        avatar.isNullOrBlank() -> ""
                                        else -> avatar
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Сохранить профиль") }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Синхронизация",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Шифрованные данные хранятся в GitHub-репозитории. " +
                                "Никто без вашего пароля не увидит товары и историю.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Автосинхронизация", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "после каждого изменения",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSync,
                                onCheckedChange = { vm.setAutoSync(it) }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sync.message ?: "Ещё не синхронизировалось",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sync.success) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                                if (sync.lastSync != null) {
                                    Text(
                                        "Последний раз: ${Dates.formatDateTime(sync.lastSync)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (sync.running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                OutlinedButton(onClick = { vm.syncNow() }) { Text("Синхронизировать") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(
                                if (showToken) "Скрыть настройки токена"
                                else "Настройки GitHub-токена"
                            )
                        }
                        if (showToken) {
                            Text(
                                if (vm.hasCustomToken) {
                                    "Используется пользовательский токен."
                                } else if (vm.tokenConfigured) {
                                    "Используется встроенный токен приложения."
                                } else {
                                    "Токен не настроен — запись в GitHub недоступна."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = token,
                                onValueChange = { token = it.trim() },
                                label = { Text("Fine-grained токен (Contents: Read and write)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    vm.saveToken(token)
                                    token = ""
                                }) { Text("Сохранить токен") }
                                TextButton(onClick = { vm.saveToken("") }) { Text("Сбросить") }
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Сайт и приложение",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            vm.siteUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openUrl(context, vm.siteUrl) }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Открыть сайт")
                            }
                            OutlinedButton(onClick = { showAbout = true }) { Text("О программе") }
                            OutlinedButton(onClick = { showSupport = true }) { Text("Поддержать") }
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Пульс цен ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showLogout = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выйти из аккаунта", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onUpdate = { showAbout = false; vm.checkForUpdates() },
            onEmail = { openUrl(context, "mailto:lyrv@t-inform.ru") },
            onGitHub = { openUrl(context, com.pulscen.app.update.GitHubUpdate.REPO_URL) }
        )
    }

    if (showSupport) {
        SupportDialog(
            onDismiss = { showSupport = false },
            onSupport = { openUrl(context, SUPPORT_URL) }
        )
    }

    UpdateStateDialogs(
        state = updateState,
        onDismiss = { vm.dismissUpdate() },
        onDownload = { vm.downloadUpdate(it) },
        onRetry = { vm.retryUpdate() }
    )

    if (showInstallPermission) {
        AlertDialog(
            onDismissRequest = {
                showInstallPermission = false
                pendingApk = null
            },
            title = { Text("Разрешите установку") },
            text = {
                Text(
                    "Чтобы установить обновление, разрешите «Пульсу цен» установку " +
                        "приложений из этого источника в настройках Android."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showInstallPermission = false
                    runCatching {
                        permissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }) { Text("Настройки") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showInstallPermission = false
                    pendingApk = null
                }) { Text("Позже") }
            }
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Выйти?") },
            text = {
                Text(
                    "Локальная копия будет удалена. Данные останутся на GitHub " +
                        "в зашифрованном виде — войти можно будет снова по email и паролю."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showLogout = false
                    vm.logout()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Отмена") }
            }
        )
    }
}

private fun readAvatarDataUrl(context: Context, uri: Uri): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return null
    val max = 320
    val scale = minOf(max.toFloat() / bitmap.width, max.toFloat() / bitmap.height, 1f)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    "data:image/jpeg;base64,$base64"
}.getOrNull()

private fun canInstall(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    return context.packageManager.canRequestPackageInstalls()
}

private const val SUPPORT_URL = "https://t.tb.ru/pm_short/AOdA22MAdsS"

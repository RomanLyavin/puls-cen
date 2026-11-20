package com.pulscen.app.ui.screens

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pulscen.app.BuildConfig
import com.pulscen.app.R
import com.pulscen.app.ui.BrandMark
import com.pulscen.app.update.GitHubRelease
import com.pulscen.app.update.UpdateState
import java.io.File

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onEmail: () -> Unit,
    onGitHub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("О программе") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Пульс цен",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Разработчик: Лявин Роман", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Почта: lyrv@t-inform.ru",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onEmail)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "При разработке использована ИИ-модель OpenCode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Кнопка «Обновить» проверяет последний релиз на GitHub, " +
                        "скачивает APK и сверяет его подпись с установленной версией.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onGitHub,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) { Text("Репозиторий на GitHub") }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text("Обновить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun SupportDialog(
    onDismiss: () -> Unit,
    onSupport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поддержать проект") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Спасибо за поддержку «Пульса цен»!")
                Spacer(Modifier.height(12.dp))
                Image(
                    painter = painterResource(R.drawable.donate),
                    contentDescription = "Картинка для поддержки проекта",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onSupport)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Нажмите на картинку, чтобы перейти к оплате.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun UpdateStateDialogs(
    state: UpdateState,
    onDismiss: () -> Unit,
    onDownload: (GitHubRelease) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        UpdateState.Idle, is UpdateState.Ready -> Unit
        UpdateState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Проверка обновлений…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Запрашиваю последний релиз на GitHub.")
                }
            },
            confirmButton = {}
        )

        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Обновления не найдены") },
            text = {
                Text("У вас установлена последняя версия «Пульса цен» ${state.version}.")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        )

        is UpdateState.Available -> {
            val context = LocalContext.current
            val size = if (state.release.apkSize > 0L) {
                Formatter.formatShortFileSize(context, state.release.apkSize)
            } else {
                "размер неизвестен"
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Доступна новая версия") },
                text = {
                    Text(
                        "«Пульс цен» ${state.release.version} уже доступен ($size).\n\n" +
                            "Скачать и установить?"
                    )
                },
                confirmButton = {
                    Button(onClick = { onDownload(state.release) }) { Text("Скачать") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Позже") }
                }
            )
        }

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка обновления") },
            text = {
                Column {
                    Text("Скачивание «Пульса цен» ${state.version}…")
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.percent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )

        is UpdateState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(state.title) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onRetry) { Text("Повторить") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        )
    }
}

internal fun startApkInstall(context: Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, "Файл обновления не найден", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (error: Exception) {
        Toast.makeText(context, "Не удалось открыть установщик APK.", Toast.LENGTH_LONG).show()
    }
}

package com.pulscen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulscen.app.BuildConfig
import com.pulscen.app.ui.AppViewModel
import com.pulscen.app.ui.BrandGradient
import com.pulscen.app.ui.BrandMark
import com.pulscen.app.ui.ErrorText
import com.pulscen.app.ui.InfoBanner

@Composable
fun AuthScreen(vm: AppViewModel) {
    val loading by vm.authLoading.collectAsStateWithLifecycle()
    val error by vm.authError.collectAsStateWithLifecycle()
    var mode by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(54.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        "Пульс цен",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "история цен — в одном месте",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Следите за ценой.\nНе пропускайте снижение.",
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Добавьте ссылку на товар — приложение найдёт фото, артикул и цену, " +
                    "а затем будет записывать каждое изменение в историю.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeaturePill("🔗 Ссылка")
                FeaturePill("📉 История")
                FeaturePill("🔒 Шифрование")
            }
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    TabRow(selectedTabIndex = mode) {
                        Tab(
                            selected = mode == 0,
                            onClick = { mode = 0; vm.clearAuthError() },
                            text = { Text("Вход") }
                        )
                        Tab(
                            selected = mode == 1,
                            onClick = { mode = 1; vm.clearAuthError() },
                            text = { Text("Регистрация") }
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim(); vm.clearAuthError() },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; vm.clearAuthError() },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(
                                    if (showPassword) "Скрыть" else "Показать",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (mode == 1) ImeAction.Next else ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == 1) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it; vm.clearAuthError() },
                            label = { Text("Повторите пароль") },
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ErrorText(error)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (mode == 0) vm.login(email, password)
                            else vm.register(email, password, confirm)
                        },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (mode == 0) "Войти" else "Зарегистрироваться")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (mode == 0) {
                            "Нет аккаунта? Вкладка «Регистрация»."
                        } else {
                            "После регистрации код подтверждения появится на экране. " +
                                "Письмо не отправляется — это демонстрационный режим почты."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            InfoBanner(
                "Профиль, товары и история цен шифруются паролем и попадают на GitHub " +
                    "только в зашифрованном виде. Пароль восстановить нельзя — сохраните его."
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Сайт: ${vm.siteUrl.removePrefix("https://")} · версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeaturePill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun VerifyScreen(vm: AppViewModel) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val loading by vm.authLoading.collectAsStateWithLifecycle()
    val error by vm.authError.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(BrandGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✉️", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text("Подтвердите email", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Код для ${pending?.email ?: "вашей почты"} действует 15 минут.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Демо-режим",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    pending?.code ?: "------",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Письмо не отправляется: почтовый сервер не подключён. " +
                        "Код показан здесь, чтобы завершить регистрацию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6)
                vm.clearAuthError()
            },
            label = { Text("Код подтверждения") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        ErrorText(error)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.verify(code) },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Подтвердить")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.resendCode() }, modifier = Modifier.fillMaxWidth()) {
            Text("Отправить новый код")
        }
        TextButton(onClick = { vm.cancelVerification() }, modifier = Modifier.fillMaxWidth()) {
            Text("Отмена")
        }
    }
}

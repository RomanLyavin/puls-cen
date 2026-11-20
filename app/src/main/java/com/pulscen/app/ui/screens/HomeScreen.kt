package com.pulscen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulscen.app.data.Dates
import com.pulscen.app.data.Product
import com.pulscen.app.data.UserBundle
import com.pulscen.app.ui.AppViewModel
import com.pulscen.app.ui.AvatarView
import com.pulscen.app.ui.BrandHeader
import com.pulscen.app.ui.EmptyState
import com.pulscen.app.ui.ErrorText
import com.pulscen.app.ui.Money
import com.pulscen.app.ui.PriceChangeBadge
import com.pulscen.app.ui.ProductImage
import com.pulscen.app.ui.Sparkline
import com.pulscen.app.ui.subtitleFor
import com.pulscen.app.ui.BrandGreen
import com.pulscen.app.ui.BrandRed

@Composable
fun HomeScreen(vm: AppViewModel) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val addLoading by vm.addLoading.collectAsStateWithLifecycle()
    val addError by vm.addError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message!!)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.clearAddError(); showAdd = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Добавить товар") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                BrandHeader(
                    subtitle = "товары и история цен",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.syncNow() }, enabled = !sync.running) {
                                if (sync.running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = "Синхронизировать")
                                }
                            }
                            val profile = bundle?.profile
                            IconButton(onClick = { vm.openProfile() }) {
                                AvatarView(
                                    dataUrl = profile?.avatarDataUrl,
                                    name = profile?.name ?: "Профиль",
                                    size = 36.dp
                                )
                            }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                SummaryCard(
                    bundle = bundle,
                    syncMessage = sync.message,
                    syncSuccess = sync.success,
                    lastSync = sync.lastSync,
                    refreshing = refreshing,
                    onRefresh = { vm.refreshAll() }
                )
                if (sync.message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        sync.message!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sync.success) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            val products = bundle?.sortedProducts.orEmpty()
            if (products.isEmpty()) {
                EmptyState(
                    title = "Пока пусто",
                    text = "Добавьте ссылку на товар — приложение найдёт цену, фото и артикул. " +
                        "Дальше история будет пополняться сама.",
                    action = {
                        Button(onClick = { vm.clearAddError(); showAdd = true }) {
                            Text("Добавить первый товар")
                        }
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductCard(product = product, onClick = { vm.openProduct(product.id) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddProductDialog(
            loading = addLoading,
            error = addError,
            onDismiss = { showAdd = false; vm.clearAddError() },
            onAdd = { url -> vm.addProduct(url) },
            onAddManual = { url, title, price -> vm.addProductManual(url, title, price) }
        )
    }
}

@Composable
private fun SummaryCard(
    bundle: UserBundle?,
    syncMessage: String?,
    syncSuccess: Boolean,
    lastSync: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    val products = bundle?.products.orEmpty()
    val changes = products.sumOf { (it.prices.size - 1).coerceAtLeast(0) }
    val lastChecked = products.mapNotNull { it.lastCheckedAt }.maxOrNull()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ваш ценовой пульс",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Последняя проверка: ${Dates.formatDateTime(lastChecked)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRefresh) { Text("Обновить") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("${products.size}", "товаров", Modifier.weight(1f))
                StatPill("$changes", "изменений", Modifier.weight(1f))
                StatPill(
                    if (syncSuccess) "✓" else "!",
                    if (syncMessage.isNullOrBlank()) "синхронизация" else "GitHub",
                    Modifier.weight(1f)
                )
            }
            if (refreshing) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    val change = product.changeAmount
    val sparkColor = when {
        change == null -> MaterialTheme.colorScheme.primary
        change < 0 -> BrandGreen
        change > 0 -> BrandRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductImage(product = product, size = 70.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    product.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitleFor(product),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Money.rub(product.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(8.dp))
                    PriceChangeBadge(product)
                }
                if (!product.lastError.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        product.lastError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (product.prices.size >= 2) {
                Spacer(Modifier.width(8.dp))
                Sparkline(
                    points = product.prices,
                    color = sparkColor,
                    modifier = Modifier
                        .width(58.dp)
                        .height(36.dp)
                )
            }
        }
    }
}

@Composable
private fun AddProductDialog(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onAddManual: (String, String, Double) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf("") }
    var manualPrice by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) manual = true
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Новый товар") },
        text = {
            Column(
                modifier = Modifier
                    .height(if (manual) 360.dp else 210.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Вставьте ссылку на товар. Поддерживаются Wildberries, Ozon и обычные " +
                        "сайты с ценой на странице.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("Ссылка на товар") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ErrorText(error)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { manual = !manual }) {
                    Text(if (manual) "Скрыть ручной ввод" else "Сайт не отдаёт цену? Ввести вручную")
                }
                if (manual) {
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Название товара") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualPrice,
                        onValueChange = { manualPrice = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        label = { Text("Цена, ₽") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading,
                onClick = {
                    val price = manualPrice.replace(',', '.').toDoubleOrNull()
                    if (manual && manualTitle.isNotBlank() && price != null) {
                        onAddManual(url, manualTitle, price)
                    } else {
                        onAdd(url)
                    }
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (manual) "Добавить вручную" else "Добавить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Отмена") }
        }
    )
}

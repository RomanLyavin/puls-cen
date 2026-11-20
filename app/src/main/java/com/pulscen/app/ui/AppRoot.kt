package com.pulscen.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulscen.app.ui.screens.AuthScreen
import com.pulscen.app.ui.screens.HomeScreen
import com.pulscen.app.ui.screens.ProductScreen
import com.pulscen.app.ui.screens.ProfileScreen
import com.pulscen.app.ui.screens.VerifyScreen

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    when (val current = screen) {
        Screen.Auth -> AuthScreen(vm)
        Screen.Verify -> VerifyScreen(vm)
        Screen.Home -> HomeScreen(vm)
        is Screen.ProductDetails -> ProductScreen(vm, current.id)
        Screen.Profile -> ProfileScreen(vm)
    }
}

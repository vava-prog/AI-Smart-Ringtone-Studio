package com.aismart.ringtone

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat

private const val TAB_DASHBOARD = 0
private const val TAB_CREATE = 1
private const val TAB_HISTORY = 2
private const val TAB_SETTINGS = 3

@Composable
private fun RingtoneTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFA5A8FF),
            onPrimary = Color(0xFF14146B),
            secondary = Color(0xFFFF8FD0),
            background = Color(0xFF0E0A2B),
            onBackground = Color(0xFFEDEBFF),
            surface = Color(0xFF1A1440),
            onSurface = Color(0xFFEDEBFF),
            onSurfaceVariant = Color(0xFFC9C4E8),
            outlineVariant = Color(0x55FFFFFF)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5),
            onPrimary = Color.White,
            secondary = Color(0xFFD946A8),
            background = Color(0xFFE9ECFF),
            onBackground = Color(0xFF1B1840),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1B1840),
            onSurfaceVariant = Color(0xFF4A4670),
            outlineVariant = Color(0x55000000)
        )
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun RingtoneApp(vm: RingtoneViewModel) {
    val dark = when (vm.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    RingtoneTheme(dark) {
        var tab by rememberSaveable { mutableStateOf(TAB_DASHBOARD) }
        BackHandler(enabled = tab != TAB_DASHBOARD) { tab = TAB_DASHBOARD }
        LaunchedEffect(vm.openCreateTick) {
            if (vm.openCreateTick > 0) tab = TAB_CREATE
        }

        // Ở các tab khác "Tạo mới", thông báo hiện dưới dạng snackbar
        val snackbar = remember { SnackbarHostState() }
        val message = vm.message
        val messageText = stringResource(message.res, *message.args)
        LaunchedEffect(message) {
            if (tab != TAB_CREATE && message.res != R.string.msg_start_hint) {
                snackbar.showSnackbar(messageText)
            }
        }

        GlassBackground(dark) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White.copy(alpha = if (dark) 0.08f else 0.55f)
                    ) {
                        NavItem("📊", R.string.nav_dashboard, tab == TAB_DASHBOARD) { tab = TAB_DASHBOARD }
                        NavItem("✂️", R.string.nav_create, tab == TAB_CREATE) { tab = TAB_CREATE }
                        NavItem("🕘", R.string.nav_history, tab == TAB_HISTORY) { tab = TAB_HISTORY }
                        NavItem("⚙️", R.string.nav_settings, tab == TAB_SETTINGS) { tab = TAB_SETTINGS }
                    }
                }
            ) { padding ->
                val content = Modifier.padding(padding)
                when (tab) {
                    TAB_DASHBOARD -> DashboardScreen(
                        vm = vm,
                        onCreate = { tab = TAB_CREATE },
                        onOpenHistory = { tab = TAB_HISTORY },
                        modifier = content
                    )
                    TAB_CREATE -> CreateScreen(vm, content)
                    TAB_HISTORY -> HistoryScreen(vm, content)
                    else -> SettingsScreen(vm, content)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    icon: String,
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(icon) },
        label = { Text(stringResource(labelRes)) }
    )
}

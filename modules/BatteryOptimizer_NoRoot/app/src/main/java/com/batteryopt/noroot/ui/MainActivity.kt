package com.batteryopt.noroot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.ui.screens.AboutScreen
import com.batteryopt.noroot.ui.screens.DiagnosticsScreen
import com.batteryopt.noroot.ui.screens.UpdateScreen
import com.batteryopt.noroot.ui.screens.FeaturesScreen
import com.batteryopt.noroot.ui.screens.HomeScreen
import com.batteryopt.noroot.ui.theme.BatteryOptimizerTheme
import com.batteryopt.noroot.ui.theme.ThemePresets
import com.batteryopt.noroot.utils.ConfigManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.init(applicationContext)
        setContent {
            val themeIndex = remember { mutableStateOf(ConfigManager.readThemeIndex()) }
            BatteryOptimizerTheme(themeIndex = themeIndex.value) {
                MainScreen(themeIndex = themeIndex)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(themeIndex: MutableState<Int> = remember { mutableStateOf(0) }) {
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    var cfg by remember { mutableStateOf(ConfigManager.getGlobalConfig()) }
    val onCfgChange: (BatteryConfig) -> Unit = { cfg = it }

    val screens = listOf(
        Triple("home", "总开关", Icons.Default.BatteryStd),
        Triple("features", "功能", Icons.Default.Build),
        Triple("diagnostics", "诊断", Icons.Default.BugReport),
        Triple("update", "更新", Icons.Default.CloudDownload),
        Triple("theme", "主题", Icons.Default.Palette),
        Triple("about", "关于", Icons.Default.Info)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("BatteryOptimizer NoRoot") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!isLandscape) {
                val current by navController.currentBackStackEntryAsState()
                val route = current?.destination?.route
                NavigationBar {
                    screens.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { navController.navigate(r) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(inner)) {
                val current by navController.currentBackStackEntryAsState()
                val route = current?.destination?.route
                NavigationRail {
                    screens.forEach { (r, label, icon) ->
                        NavigationRailItem(
                            selected = route == r,
                            onClick = { navController.navigate(r) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
                AppNavHost(navController, cfg, onCfgChange, themeIndex, PaddingValues(0.dp))
            }
        } else {
            AppNavHost(navController, cfg, onCfgChange, themeIndex, inner)
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    cfg: BatteryConfig,
    onCfgChange: (BatteryConfig) -> Unit,
    themeIndex: MutableState<Int>,
    padding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().padding(padding)
    ) {
        composable("home") { HomeScreen(cfg, onCfgChange) }
        composable("features") { FeaturesScreen(cfg, onCfgChange) }
        composable("diagnostics") { DiagnosticsScreen() }
        composable("update") { UpdateScreen() }
        composable("theme") { ThemePicker(themeIndex) }
        composable("about") { AboutScreen() }
    }
}

@Composable
fun ThemePicker(themeIndex: MutableState<Int>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("选择主题", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            itemsIndexed(ThemePresets) { index, preset ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            themeIndex.value = index
                            ConfigManager.writeThemeIndex(index)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == themeIndex.value) preset.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(preset.primary, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        if (index == themeIndex.value) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Check, "已选中", tint = preset.primary)
                        }
                    }
                }
            }
        }
    }
}

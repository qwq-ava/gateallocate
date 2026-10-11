package net.micode.airportgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.micode.airportgate.ui.assign.AssignScreen
import net.micode.airportgate.ui.components.BottomNavBar
import net.micode.airportgate.ui.gateinfo.GateInfoScreen
import net.micode.airportgate.ui.home.HomeScreen
import net.micode.airportgate.ui.import.ImportScreen
import net.micode.airportgate.ui.stats.StatsScreen
import net.micode.airportgate.ui.theme.AirportGateAppTheme
import net.micode.airportgate.ui.theme.BgGray

// ========================= 导航目标 =========================
enum class Screen(val title: String, val icon: ImageVector) {
    Import("航班导入", Icons.Outlined.FileDownload),
    GateInfo("机位信息维护", Icons.Outlined.Settings),
    Assign("机位分配", Icons.Outlined.GridView),
    Stats("统计利用率", Icons.Outlined.BarChart)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirportGateAppTheme {
                AppRoot()
            }
        }
    }
}

// ========================= 根布局：导航状态 =========================
@Composable
fun AppRoot() {
    var current by remember { mutableStateOf<Screen?>(null) } // null = 主界面
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMsg: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    // 系统返回键：子界面返回主界面
    BackHandler(enabled = current != null) { current = null }

    Scaffold(
        containerColor = BgGray,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavBar(selected = current, onSelect = { current = it })
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(BgGray)
                .statusBarsPadding()
        ) {
            when (current) {
                null -> HomeScreen(
                    onNavigate = { current = it },
                    showMsg = showMsg
                )
                Screen.Import -> ImportScreen(onBack = { current = null }, showMsg = showMsg)
                Screen.GateInfo -> GateInfoScreen(onBack = { current = null }, showMsg = showMsg)
                Screen.Assign -> AssignScreen(onBack = { current = null })
                Screen.Stats -> StatsScreen(onBack = { current = null }, showMsg = showMsg)
            }
        }
    }
}

package net.micode.airportgate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.Screen
import net.micode.airportgate.data.model.FlightItem
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.GreenLight
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.OrangeLight
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 跨界面通用组件 =========================

/** 底部导航栏（五个界面都有） */
@Composable
fun BottomNavBar(selected: Screen?, onSelect: (Screen) -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
        ) {
            Screen.entries.forEach { screen ->
                val active = selected == screen
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = if (active) Blue else Color(0xFF9AA0A6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = screen.title,
                        fontSize = 11.sp,
                        color = if (active) Blue else Color(0xFF9AA0A6)
                    )
                }
            }
        }
    }
}

/** 子界面顶部栏：返回按钮 + 居中标题 */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Surface(color = Color.White) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextMain
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.align(Alignment.Center)
            )
            if (action != null) {
                Box(Modifier.align(Alignment.CenterEnd)) { action() }
            }
        }
    }
}

/** 区块标题：蓝色竖条 + 文字 */
@Composable
fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(Blue, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextMain,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

/** 状态标签（已分配 / 已完成 / 导入成功 / 部分失败） */
@Composable
fun StatusChip(text: String) {
    val (bg, fg) = when {
        text.contains("已完成") || text.contains("成功") -> GreenLight to Green
        text.contains("失败") -> OrangeLight to Orange
        else -> BlueLight to Blue
    }
    Text(
        text = text,
        fontSize = 12.sp,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** 通用卡片容器 */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

/** 航班动态行 */
@Composable
fun FlightRow(f: FlightItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(f.time, fontSize = 13.sp, color = TextSub, modifier = Modifier.width(46.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.flightNo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${f.from} → ${f.to}",
                    fontSize = 13.sp,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text("分配机位 ", fontSize = 12.sp, color = TextSub)
                Text(f.gate, fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold)
                Text(" · ${f.desc}", fontSize = 12.sp, color = TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(f.status)
    }
}

/** 细竖向分隔条（统计卡内三栏之间） */
@Composable
fun VerticalDividerCompat() {
    Box(
        Modifier
            .height(40.dp)
            .width(1.dp)
            .background(Color(0xFFE5E7EB))
    )
}

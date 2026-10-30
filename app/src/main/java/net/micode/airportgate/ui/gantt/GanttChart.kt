package net.micode.airportgate.ui.gantt

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.nowMinuteOfDay
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.ConflictRed
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 甘特图（机位 × 时间） =========================

private val GATE_COL_W = 62.dp
private val GANTT_HEADER_H = 26.dp
private val GANTT_ROW_H = 38.dp
private const val AXIS_START_MIN = 0          // 时间轴起点 00:00
private const val AXIS_END_MIN = 24 * 60      // 时间轴终点 24:00（全天 24 小时）

/**
 * 甘特图主体。
 * fitWidth = false：竖屏内嵌，固定刻度，可横向滑动 + 双指缩放
 * fitWidth = true ：全屏横屏，刻度自动铺满宽度，整轴一屏呈现
 *
 * 绘制：单个视口尺寸的 Canvas，表头/网格/色块全部按 -ox 平移自绘，
 * 与点击命中测试共用同一坐标系（避免超大子元素带来的布局对齐问题）。
 */
@Composable
fun GateGanttChart(
    gates: List<String>,
    slots: List<GateSlot>,
    fitWidth: Boolean,
    onSlotClick: (GateSlot) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = GANTT_ROW_H,
    axisStartMin: Int = AXIS_START_MIN,
    axisEndMin: Int = AXIS_END_MIN,
    basePxPerMin: Float = 1.6f
) {
    var scale by remember(fitWidth) { mutableFloatStateOf(1f) }
    var offset by remember(fitWidth) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val axisLen = (axisEndMin - axisStartMin).toFloat()

    Row(Modifier.fillMaxWidth()) {
        // ---- 左侧固定机位列 ----
        Column(Modifier.width(GATE_COL_W).background(Color(0xFFFAFAFC))) {
            Box(Modifier.height(GANTT_HEADER_H), contentAlignment = Alignment.CenterStart) {
                Text("机位", fontSize = 11.sp, color = TextSub)
            }
            gates.forEach { gate ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(gate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue)
                }
            }
        }
        // ---- 右侧时间轴绘图区 ----
        BoxWithConstraints(Modifier.weight(1f)) {
            val labelPaint = remember {
                Paint().apply {
                    color = android.graphics.Color.parseColor("#999999")
                    textSize = with(density) { 11.sp.toPx() }
                    isAntiAlias = true
                }
            }
            // 时间轴右端预留余量：末端刻度 24:00 的标签要有地方站，
            // 否则贴边被迫左移一个标签宽度，和 22:00 挤在一起
            val endPadPx = remember(labelPaint) {
                labelPaint.measureText("24:00") + with(density) { 8.dp.toPx() }
            }
            val endPadDp = with(density) { endPadPx.toDp().value }
            // 全屏时 fitScale 让整轴刚好铺满可视宽度（已扣除末端标签预留）
            val fitScale = if (fitWidth) {
                ((maxWidth.value - endPadDp) / (axisLen * basePxPerMin)).coerceAtLeast(0.1f)
            } else 1f
            val pxPerMinDp = basePxPerMin * scale * fitScale
            val pxPerMin = with(density) { pxPerMinDp.dp.toPx() }
            val viewportW = with(density) { maxWidth.toPx() }
            val contentW = axisLen * pxPerMin + endPadPx
            val maxOffset = (contentW - viewportW).coerceAtLeast(0f)
            val ox = offset.coerceIn(0f, maxOffset)

            val headerPx = with(density) { GANTT_HEADER_H.toPx() }
            val rowPx = with(density) { rowHeight.toPx() }
            val barPx = with(density) { 22.dp.toPx() }
            val totalPx = headerPx + rowPx * gates.size

            // 全天 24 小时轴：刻度密度随缩放自适应，避免 24 个整点标签挤在一起
            val hourDp = 60f * pxPerMinDp
            val gridStepMin = when {
                hourDp >= 40f -> 30
                hourDp >= 13f -> 60
                else -> 120
            }
            val labelStepMin = when {
                hourDp >= 40f -> 60
                hourDp >= 20f -> 120
                hourDp >= 13f -> 180
                else -> 240
            }
            val nowMin = remember { nowMinuteOfDay() }

            val barTextPaint = remember {
                Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = with(density) { 11.sp.toPx() }
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
            }
            val oxState = rememberUpdatedState(ox)
            val pxState = rememberUpdatedState(pxPerMin)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { totalPx.toDp() })
                    .clipToBounds()
                    .pointerInput(Unit) {
                        // 横向拖动 / 双指缩放由甘特图消费；
                        // 纵向拖动不消费，放行给外层页面滚动（24 小时图较长，必须能正常滚过）
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val pinch = event.changes.size > 1
                                if (pinch || abs(pan.x) > abs(pan.y)) {
                                    val newScale = (scale * zoom).coerceIn(0.6f, 4f)
                                    val newPx = with(density) { (basePxPerMin * newScale * fitScale).dp.toPx() }
                                    val newMax = (axisLen * newPx - viewportW).coerceAtLeast(0f)
                                    scale = newScale
                                    offset = (offset - pan.x).coerceIn(0f, newMax)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                // ---- 视口 Canvas：表头 / 网格 / 虚线 / 色块全部按 -ox 平移绘制 ----
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    // 竖向网格 + 整点刻度（00:00 - 24:00，密度随缩放自适应）
                    var m = axisStartMin
                    while (m <= axisEndMin) {
                        val x = (m - axisStartMin) * pxPerMin - ox
                        if (x >= -1f && x <= w + 1f) {
                            val isMajor = m % labelStepMin == 0
                            drawLine(
                                color = if (isMajor) Color(0xFFE5E7EB) else Color(0xFFF6F7F8),
                                start = Offset(x, if (isMajor) 0f else headerPx),
                                end = Offset(x, totalPx),
                                strokeWidth = 1f
                            )
                            if (isMajor) {
                                val label = "%02d:00".format(m / 60)
                                val tw = labelPaint.measureText(label)
                                // 末端刻度（24:00）若贴边则右对齐，避免被裁掉
                                val tx = if (x + 4.dp.toPx() + tw <= w) x + 4.dp.toPx()
                                else x - 4.dp.toPx() - tw
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawText(label, tx, headerPx - 7.dp.toPx(), labelPaint)
                                }
                            }
                        }
                        m += gridStepMin
                    }
                    // 横向行线（画到时间轴末端为止，末端标签预留的余量保持空白）
                    val axisEndX = (axisEndMin - axisStartMin) * pxPerMin - ox
                    val lineRight = w.coerceAtMost(axisEndX)
                    if (lineRight > 0f) {
                        for (i in 0..gates.size) {
                            val y = headerPx + i * rowPx
                            drawLine(Color(0xFFEDEEF0), Offset(0f, y), Offset(lineRight, y), 1f)
                        }
                    }
                    // 当前时刻虚线（按系统时间实时定位）
                    val nowX = (nowMin - axisStartMin) * pxPerMin - ox
                    if (nowX >= 0f && nowX <= w) {
                        drawLine(
                            color = ConflictRed,
                            start = Offset(nowX, headerPx),
                            end = Offset(nowX, totalPx),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                    // 航班色块
                    slots.forEach { slot ->
                        val row = gates.indexOf(slot.gate)
                        if (row < 0) return@forEach
                        val left = (slot.startMin - axisStartMin) * pxPerMin - ox
                        val right = (slot.endMin - axisStartMin) * pxPerMin - ox
                        if (right < 0f || left > w) return@forEach
                        val barColor = when {
                            slot.conflict -> ConflictRed
                            slot.finished -> Green
                            slot.isBridge -> Blue
                            else -> Orange
                        }
                        val top = headerPx + row * rowPx + (rowPx - barPx) / 2f
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left + 1f, top),
                            size = Size((right - left - 2f).coerceAtLeast(2f), barPx),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                        if (right - left > 52.dp.toPx()) {
                            drawIntoCanvas { c ->
                                c.nativeCanvas.drawText(
                                    slot.flightNo,
                                    left + 6.dp.toPx(),
                                    top + barPx / 2f + 4.dp.toPx(),
                                    barTextPaint
                                )
                            }
                        }
                    }
                }
                // ---- 点击命中层：子节点先于父级手势处理，点击不会被拖动/缩放吞掉 ----
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(gates, slots) {
                            detectTapGestures { pos ->
                                val contentX = pos.x + oxState.value
                                val contentY = pos.y - headerPx
                                if (contentY < 0f) return@detectTapGestures
                                val row = (contentY / rowPx).toInt()
                                val gate = gates.getOrNull(row) ?: return@detectTapGestures
                                val minute = axisStartMin + (contentX / pxState.value).toInt()
                                slots.firstOrNull {
                                    it.gate == gate && minute >= it.startMin && minute < it.endMin
                                }?.let(onSlotClick)
                            }
                        }
                )
            }
        }
    }
}

/** 甘特图卡片（竖屏内嵌：横向滑动 + 双指缩放 + 全屏入口） */
@Composable
fun GanttCard(
    gates: List<String>,
    slots: List<GateSlot>,
    onExpand: () -> Unit,
    onSlotClick: (GateSlot) -> Unit
) {
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Blue, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "机位分配甘特图",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BlueLight)
                    .clickable { onExpand() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Fullscreen,
                    contentDescription = "全屏",
                    tint = Blue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("全屏", fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        GanttLegend()
        Spacer(Modifier.height(10.dp))
        GateGanttChart(gates = gates, slots = slots, fitWidth = false, onSlotClick = onSlotClick)
        Spacer(Modifier.height(8.dp))
        Text("左右滑动查看全天 24 小时 · 双指缩放 · 点击色块查看详情", fontSize = 11.sp, color = TextSub)
    }
}

@Composable
fun GanttLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot("廊桥", Blue)
        Spacer(Modifier.width(10.dp))
        LegendDot("远机位", Orange)
        Spacer(Modifier.width(10.dp))
        LegendDot("已完成", Green)
        Spacer(Modifier.width(10.dp))
        LegendDot("冲突", ConflictRed)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = TextSub)
    }
}

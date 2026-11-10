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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.MINUTES_PER_DAY
import net.micode.airportgate.data.model.dateOf
import net.micode.airportgate.data.model.fmtTime
import net.micode.airportgate.data.model.nextDate
import net.micode.airportgate.data.model.nowMinuteOfDay
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.ConflictRed
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 甘特图（一张连续时间轴：日期 × 机位） =========================

private val GATE_COL_W = 66.dp
private val DATE_HEADER_H = 22.dp      // 第一行表头：日期带
private val TIME_HEADER_H = 22.dp      // 第二行表头：00:00 - 24:00 时间刻度
private val HEADER_H = DATE_HEADER_H + TIME_HEADER_H
private val GANTT_ROW_H = 38.dp

/**
 * 甘特图的缩放 / 平移状态。
 * 卡片与全屏各自持有一份，使「+ / − 按钮」与「双指缩放 / 单指滑动」操作同一组 scale/offset。
 */
class GanttZoomState(initialScale: Float = 1f) {
    var scale by mutableFloatStateOf(initialScale)
    var offset by mutableFloatStateOf(0f)

    /** 日期快捷跳转请求：非 null 时由图表把横轴滚到第 index 天的 00:00，处理完自动清空 */
    var scrollToDay by mutableStateOf<Int?>(null)

    fun zoomIn() { scale = (scale * 1.3f).coerceIn(MIN_SCALE, MAX_SCALE) }
    fun zoomOut() { scale = (scale / 1.3f).coerceIn(MIN_SCALE, MAX_SCALE) }
    fun reset() { scale = 1f; offset = 0f }
    fun goToDay(index: Int) { scrollToDay = index }

    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 4f
    }
}

@Composable
fun rememberGanttZoomState(): GanttZoomState = remember { GanttZoomState() }

/** 日期带标签：今天 / 明天额外标注，便于在长轴上定位 */
private fun dateLabel(date: String, today: String): String = when (date) {
    today -> "$date  今天"
    nextDate(today) -> "$date  明天"
    else -> date
}

/**
 * 一张连续甘特图：时间轴按 [dates] 顺序把每个日历日的 24 小时首尾相接，
 * 今天的 00:00–24:00 排完紧接下一天的 00:00–24:00，日期带与跨日分界线标明换天位置。
 *
 * - 横向单指滑动查看前后日期（纵向滑动放行给外层页面）；
 * - 双指缩放，或卡片上的 + / − 按钮缩放；刻度文字、日期、航班号、左侧机位名随缩放同步放大；
 * - fitWidth = true（全屏横屏）时让「一天 24 小时」刚好铺满一屏，其余日期左右滑动查看。
 *
 * 绘制采用单个视口 Canvas，表头 / 网格 / 色块全部按 -ox 平移自绘，与点击命中测试共用同一坐标系。
 */
@Composable
fun GateGanttChart(
    gates: List<String>,
    slots: List<GateSlot>,
    dates: List<String>,
    fitWidth: Boolean,
    onSlotClick: (GateSlot) -> Unit,
    modifier: Modifier = Modifier,
    zoomState: GanttZoomState = rememberGanttZoomState(),
    rowHeight: Dp = GANTT_ROW_H,
    basePxPerMin: Float = 1.6f,
    showNowLine: Boolean = true,
    today: String = dateOf(0)
) {
    val density = LocalDensity.current
    val dayCount = dates.size.coerceAtLeast(1)
    val axisLen = (dayCount * MINUTES_PER_DAY).toFloat()
    val scale = zoomState.scale
    // 字号随缩放同步放大，设上限避免字大到溢出固定行高
    val textScale = scale.coerceIn(0.8f, 2.2f)
    // 左侧机位列宽度固定，长机位号（如 T2-14）不能跟着放大到 2.2 倍，否则会被挤断行；
    // 「上面的字」（日期带、时间刻度、色块航班号）仍按 textScale 完整放大。
    val gateTextScale = textScale.coerceAtMost(1.3f)
    val dayIndexOf = remember(dates) { dates.mapIndexed { i, d -> d to i }.toMap() }

    Row(modifier.fillMaxWidth()) {
        // ---- 左侧固定机位列（表头两行留空，机位名随缩放放大） ----
        Column(Modifier.width(GATE_COL_W).background(Color(0xFFFAFAFC))) {
            Box(Modifier.height(HEADER_H), contentAlignment = Alignment.BottomStart) {
                Text(
                    "机位",
                    fontSize = (11 * gateTextScale).sp,
                    color = TextSub,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            gates.forEach { gate ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        gate,
                        fontSize = (12 * gateTextScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = Blue,
                        maxLines = 1
                    )
                }
            }
        }

        // ---- 右侧：连续日期时间轴 ----
        BoxWithConstraints(Modifier.weight(1f)) {
            val baseLabelPx = with(density) { 11.sp.toPx() }
            val baseDatePx = with(density) { 12.sp.toPx() }
            val baseBarTextPx = with(density) { 11.sp.toPx() }
            // 字号是 key：缩放时重建 Paint，保证表头 / 色块文字一起变大
            val labelPaint = remember(textScale, baseLabelPx) {
                Paint().apply {
                    color = android.graphics.Color.parseColor("#8A9099")
                    textSize = baseLabelPx * textScale
                    isAntiAlias = true
                }
            }
            val datePaint = remember(textScale, baseDatePx) {
                Paint().apply {
                    color = android.graphics.Color.parseColor("#2F5FA8")
                    textSize = baseDatePx * textScale
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
            }
            val barTextPaint = remember(textScale, baseBarTextPx) {
                Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = baseBarTextPx * textScale
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
            }

            // 时间轴末端留一点余量，让最后一个刻度标签不至于贴边被裁
            val endPadPx = with(density) { 36.dp.toPx() }
            val endPadDp = with(density) { endPadPx.toDp().value }
            val viewportW = with(density) { maxWidth.toPx() }
            // 全屏：一整天（1440 分钟）铺满一屏；内嵌：固定刻度密度
            val fitScale = if (fitWidth) {
                ((maxWidth.value - endPadDp) / (MINUTES_PER_DAY * basePxPerMin)).coerceAtLeast(0.1f)
            } else 1f
            val pxPerMinDp = basePxPerMin * scale * fitScale
            val pxPerMin = with(density) { pxPerMinDp.dp.toPx() }
            val contentW = axisLen * pxPerMin + endPadPx
            val maxOffset = (contentW - viewportW).coerceAtLeast(0f)
            val ox = zoomState.offset.coerceIn(0f, maxOffset)

            // 日期快捷跳转：把横轴定位到目标日的 00:00（内嵌视图一天有好几屏宽，
            // 点日期标签比手动滑过去更快；滑动仍然随时可用）
            LaunchedEffect(zoomState.scrollToDay, pxPerMin, maxOffset) {
                val target = zoomState.scrollToDay ?: return@LaunchedEffect
                zoomState.offset = (target * MINUTES_PER_DAY * pxPerMin).coerceIn(0f, maxOffset)
                zoomState.scrollToDay = null
            }

            val dateHeaderPx = with(density) { DATE_HEADER_H.toPx() }
            val timeHeaderPx = with(density) { TIME_HEADER_H.toPx() }
            val headerPx = dateHeaderPx + timeHeaderPx
            val rowPx = with(density) { rowHeight.toPx() }
            val barPx = (with(density) { 22.dp.toPx() } * textScale)
                .coerceAtMost(rowPx - with(density) { 6.dp.toPx() })
            val totalPx = headerPx + rowPx * gates.size

            // 刻度密度随缩放自适应（步长都能整除 1440，跨日边界不会错位）
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
            val todayIndex = dayIndexOf[today] ?: -1

            val oxState = rememberUpdatedState(ox)
            val pxState = rememberUpdatedState(pxPerMin)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { totalPx.toDp() })
                    .clipToBounds()
                    .pointerInput(Unit) {
                        // 横向拖动 / 双指缩放由甘特图消费；
                        // 纵向拖动不消费，放行给外层页面滚动
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val pinch = event.changes.size > 1
                                if (pinch && zoom != 1f) {
                                    // 以触点为锚点缩放：锚点下的时刻保持不动
                                    val newScale = (zoomState.scale * zoom)
                                        .coerceIn(GanttZoomState.MIN_SCALE, GanttZoomState.MAX_SCALE)
                                    val ratio = newScale / zoomState.scale
                                    val anchorX = event.changes.first().position.x
                                    val wanted = (anchorX + zoomState.offset) * ratio - anchorX - pan.x
                                    val newPx = with(density) {
                                        (basePxPerMin * newScale * fitScale).dp.toPx()
                                    }
                                    val newMax = (axisLen * newPx + endPadPx - viewportW).coerceAtLeast(0f)
                                    zoomState.scale = newScale
                                    zoomState.offset = wanted.coerceIn(0f, newMax)
                                    event.changes.forEach { it.consume() }
                                } else if (!pinch && abs(pan.x) > abs(pan.y) && pan.x != 0f) {
                                    val curPx = with(density) {
                                        (basePxPerMin * zoomState.scale * fitScale).dp.toPx()
                                    }
                                    val curMax = (axisLen * curPx + endPadPx - viewportW).coerceAtLeast(0f)
                                    zoomState.offset = (zoomState.offset - pan.x).coerceIn(0f, curMax)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width

                    // ---- 1) 日期带：每 24h 一块，交替底色；日期标签吸顶显示 ----
                    // 用 dates.indices 而非 dayCount，保证数据尚未加载完时不会越界
                    for (i in dates.indices) {
                        val left = i * MINUTES_PER_DAY * pxPerMin - ox
                        val right = (i + 1) * MINUTES_PER_DAY * pxPerMin - ox
                        if (right < 0f || left > w) continue
                        drawRect(
                            color = if (i % 2 == 0) Color(0xFFF1F6FF) else Color(0xFFFAFBFE),
                            topLeft = Offset(left, 0f),
                            size = Size(right - left, dateHeaderPx)
                        )
                        val label = dateLabel(dates[i], today)
                        val tw = datePaint.measureText(label)
                        // 日期滚过视口左缘时把标签固定在左边，滑动中始终看得到「当前是哪天」
                        val maxTx = (w - tw - 4.dp.toPx()).coerceAtLeast(4.dp.toPx())
                        val tx = (left + 8.dp.toPx()).coerceIn(4.dp.toPx(), maxTx)
                        drawIntoCanvas { c ->
                            c.nativeCanvas.drawText(
                                label, tx,
                                dateHeaderPx / 2f + datePaint.textSize / 3f, datePaint
                            )
                        }
                    }
                    drawLine(
                        Color(0xFFDEE4EE), Offset(0f, dateHeaderPx), Offset(w, dateHeaderPx), 1f
                    )

                    // ---- 2) 竖网格 + 时间刻度（跨日边界画蓝色粗线） ----
                    var m = 0
                    while (m <= axisLen.toInt()) {
                        val x = m * pxPerMin - ox
                        val dayBoundary = m % MINUTES_PER_DAY == 0
                        if (x >= -1f && x <= w + 1f) {
                            val isMajor = m % labelStepMin == 0
                            val lineColor = when {
                                dayBoundary -> Color(0xFF9DB8E8)
                                isMajor -> Color(0xFFE5E7EB)
                                else -> Color(0xFFF6F7F8)
                            }
                            drawLine(
                                color = lineColor,
                                start = Offset(x, if (dayBoundary) 0f else if (isMajor) dateHeaderPx else headerPx),
                                end = Offset(x, totalPx),
                                strokeWidth = if (dayBoundary) 2f else 1f
                            )
                            if (isMajor) {
                                // 1440 分即次日 00:00，按「00:00」标注而非「24:00」
                                val label = "%02d:00".format((m % MINUTES_PER_DAY) / 60)
                                val tw = labelPaint.measureText(label)
                                val tx = if (x + 4.dp.toPx() + tw <= w) x + 4.dp.toPx()
                                else x - 4.dp.toPx() - tw
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawText(label, tx, headerPx - 6.dp.toPx(), labelPaint)
                                }
                            }
                        }
                        m += gridStepMin
                    }

                    // ---- 3) 横向行线 ----
                    val axisEndX = axisLen * pxPerMin - ox
                    val lineRight = w.coerceAtMost(axisEndX)
                    if (lineRight > 0f) {
                        for (i in 0..gates.size) {
                            val y = headerPx + i * rowPx
                            drawLine(Color(0xFFEDEEF0), Offset(0f, y), Offset(lineRight, y), 1f)
                        }
                    }

                    // ---- 4) 当前时刻虚线（按真实日期定位到「今天」那个 24h 区块） ----
                    if (showNowLine && todayIndex >= 0) {
                        val nowX = (todayIndex * MINUTES_PER_DAY + nowMin) * pxPerMin - ox
                        if (nowX >= 0f && nowX <= w) {
                            drawLine(
                                color = ConflictRed,
                                start = Offset(nowX, dateHeaderPx),
                                end = Offset(nowX, totalPx),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                            )
                        }
                    }

                    // ---- 5) 航班色块：按「日期序 × 1440 + 当日分钟」落到连续轴上 ----
                    slots.forEach { slot ->
                        val di = dayIndexOf[slot.date] ?: return@forEach
                        val row = gates.indexOf(slot.gate)
                        if (row < 0) return@forEach
                        val absStart = di * MINUTES_PER_DAY + slot.startMin
                        val absEnd = di * MINUTES_PER_DAY + slot.endMin
                        val left = absStart * pxPerMin - ox
                        val right = absEnd * pxPerMin - ox
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
                        val barW = right - left
                        if (barW > 52.dp.toPx() * textScale) {
                            // 放大后色块更宽，顺带把占用时间也显示出来，充分利用空间
                            val text = if (barW > 150.dp.toPx() * textScale) {
                                "${slot.flightNo}  ${fmtTime(slot.startMin)}-${fmtTime(slot.endMin)}"
                            } else slot.flightNo
                            if (barTextPaint.measureText(text) <= barW - 10.dp.toPx()) {
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawText(
                                        text,
                                        left + 6.dp.toPx(),
                                        top + barPx / 2f + barTextPaint.textSize / 3f,
                                        barTextPaint
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- 点击命中层：子节点先于父级手势处理，点击不会被拖动/缩放吞掉 ----
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(gates, slots, dates) {
                            detectTapGestures { pos ->
                                val contentX = pos.x + oxState.value
                                val contentY = pos.y - headerPx
                                if (contentY < 0f) return@detectTapGestures
                                val row = (contentY / rowPx).toInt()
                                val gate = gates.getOrNull(row) ?: return@detectTapGestures
                                val absMin = (contentX / pxState.value).toInt()
                                val di = absMin / MINUTES_PER_DAY
                                val date = dates.getOrNull(di) ?: return@detectTapGestures
                                val minute = absMin % MINUTES_PER_DAY
                                slots.firstOrNull {
                                    it.date == date && it.gate == gate &&
                                        minute >= it.startMin && minute < it.endMin
                                }?.let(onSlotClick)
                            }
                        }
                )
            }
        }
    }
}

/** 缩放控件：+ / − 放大缩小，点百分比复位到 100%（放大时刻度与航班号同步变大） */
@Composable
fun ZoomControls(zoomState: GanttZoomState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ZoomButton("-") { zoomState.zoomOut() }
        Text(
            "${(zoomState.scale * 100).toInt()}%",
            fontSize = 11.sp,
            color = TextSub,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(40.dp)
                .clickable { zoomState.reset() }
        )
        ZoomButton("+") { zoomState.zoomIn() }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BlueLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Blue)
    }
}

/** 甘特图卡片（竖屏内嵌：一张连续日期轴 + 滑动 + 缩放 + 全屏入口） */
@Composable
fun GanttCard(
    gates: List<String>,
    slots: List<GateSlot>,
    dates: List<String>,
    onExpand: () -> Unit,
    onSlotClick: (GateSlot) -> Unit,
    showNowLine: Boolean = true,
    today: String = dateOf(0)
) {
    val zoom = rememberGanttZoomState()
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
            ZoomControls(zoom)
            Spacer(Modifier.width(6.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            GanttLegend()
            Spacer(Modifier.weight(1f))
            Text(
                "共 ${dates.size} 天 · ${dates.firstOrNull() ?: ""} 起",
                fontSize = 11.sp,
                color = TextSub
            )
        }
        Spacer(Modifier.height(8.dp))
        // 日期快捷跳转：连续轴很长时点一下就到那天的 00:00，不必一路滑过去
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dates.forEachIndexed { i, d ->
                val label = when (d) {
                    today -> "今天"
                    nextDate(today) -> "明天"
                    else -> d.takeLast(5)
                }
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlueLight)
                        .clickable { zoom.goToDay(i) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        GateGanttChart(
            gates = gates,
            slots = slots,
            dates = dates,
            fitWidth = false,
            onSlotClick = onSlotClick,
            zoomState = zoom,
            showNowLine = showNowLine,
            today = today
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "左右滑动 / 点日期标签定位 · 双指或 + / − 放大 · 点击色块看详情；机位行只画有占用的及部分空闲机位，完整清单见「机位信息维护」",
            fontSize = 11.sp,
            color = TextSub
        )
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

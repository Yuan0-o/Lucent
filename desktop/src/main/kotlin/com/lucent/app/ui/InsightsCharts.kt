package com.lucent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.TaskInsights
import com.lucent.app.i18n.S

@Composable
fun InsightsCharts(tasks: List<com.lucent.app.data.Task>) {
    val foreground = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val accent = androidx.compose.ui.graphics.Color(0xFF8CD8C0)
    val blue = androidx.compose.ui.graphics.Color(0xFF83B9F2)
    val warm = androidx.compose.ui.graphics.Color(0xFFFF8D78)
    val completed = TaskInsights.completedPerDay(tasks)
    val daily = TaskInsights.createdAndCompletedPerDay(tasks)
    val status = TaskInsights.statusDistribution(tasks)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().frostedGlass().padding(18.dp)) {
            Text(S.insightsCompletedTrend, color = foreground, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (completed.any { it.value > 0 }) {
                Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                    val left = 10f; val right = size.width - 10f; val top = 8f; val bottom = size.height - 22f
                    val max = (completed.maxOf { it.value }).coerceAtLeast(1)
                    val points = completed.mapIndexed { index, item ->
                        Offset(left + (right - left) * index / (completed.size - 1), bottom - (bottom - top) * item.value / max)
                    }
                    drawLine(foreground.copy(alpha = .12f), Offset(left, bottom), Offset(right, bottom), 1f)
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.zipWithNext().forEach { (a, b) ->
                            val mid = (a.x + b.x) / 2f
                            cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                        }
                    }
                    val area = Path().apply { addPath(line); lineTo(right, bottom); lineTo(left, bottom); close() }
                    drawPath(area, accent.copy(alpha = .16f))
                    drawPath(line, accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
                    points.forEach { drawCircle(accent, 3.5f, it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    completed.forEachIndexed { index, day -> if (index % 2 == 0 || index == completed.lastIndex) Text(day.label, color = muted, fontSize = 9.sp) }
                }
            } else EmptyChart()
        }

        Column(Modifier.fillMaxWidth().frostedGlass().padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(accent, S.insightsCreatedTrend, muted)
                LegendDot(blue, S.insightsDoneTrend, muted)
            }
            Spacer(Modifier.height(14.dp))
            if (daily.any { it.first > 0 || it.second > 0 }) {
                Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                    val slot = size.width / daily.size
                    val max = daily.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1)
                    val chartHeight = size.height - 18f
                    daily.forEachIndexed { index, item ->
                        val center = slot * (index + .5f)
                        val barWidth = slot * .22f
                        val h1 = chartHeight * item.first / max
                        val h2 = chartHeight * item.second / max
                        drawRect(accent, Offset(center - barWidth - 2f, chartHeight - h1), Size(barWidth, h1.coerceAtLeast(if (item.first > 0) 2f else 0f)))
                        drawRect(blue, Offset(center + 2f, chartHeight - h2), Size(barWidth, h2.coerceAtLeast(if (item.second > 0) 2f else 0f)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    daily.forEach { Text(it.label, color = muted, fontSize = 9.sp) }
                }
            } else EmptyChart()
        }

        Row(Modifier.fillMaxWidth().frostedGlass().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text(S.insightsStatusTrend, color = foreground, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                LegendDot(blue, S.insightsDoneStatus, muted)
                LegendDot(accent, S.insightsActive, muted)
                LegendDot(warm, S.insightsOverdueStatus, muted)
            }
            if (status.total > 0) {
                Canvas(Modifier.size(94.dp)) {
                    val values = listOf(status.done, status.active, status.overdue)
                    val colors = listOf(blue, accent, warm)
                    var start = -90f
                    values.forEachIndexed { index, value ->
                        if (value > 0) {
                            val sweep = 360f * value / status.total
                            drawArc(colors[index], start, sweep, false, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt))
                            start += sweep
                        }
                    }
                }
            } else EmptyChart()
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String, muted: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Text(label, color = muted, fontSize = 10.sp)
    }
}

@Composable
private fun EmptyChart() {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        Text(S.insightsEmpty, color = LocalOnGradientMuted.current, fontSize = 11.sp, maxLines = 1)
    }
}

package cn.edu.hsnc.xjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import cn.edu.hsnc.xjw.data.ExamEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ExamScreen(exams: List<ExamEvent>, fetchedAt: Long) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        if (exams.isEmpty()) {
            EmptyExams(fetchedAt)
            return@Column
        }

        val dated = exams.filter { it.time.isNotBlank() }
        val undated = exams.filter { it.time.isBlank() }

        // 最近一场
        val upcoming = nearest(dated)
        if (upcoming != null) {
            val days = daysUntil(upcoming.time)
            CardBox(Modifier.fillMaxWidth(), bg = Color(0xFF141A2B)) {
                Column {
                    Text("最近一场", color = Color(0xFF8C9DC4), fontSize = 11.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        upcoming.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            upcoming.time.ifBlank { null },
                            upcoming.place.ifBlank { null },
                            upcoming.seat.takeIf { it.isNotBlank() }?.let { "座位 $it" }
                        ).joinToString(" · "),
                        color = Color(0xFF8C9DC4),
                        fontSize = 12.sp
                    )
                    if (days != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                days < 0 -> "已结束"
                                days == 0L -> "就在今天"
                                days == 1L -> "明天"
                                else -> "还有 $days 天"
                            },
                            color = if (days in 0..3) Warn else Color(0xFF8C9DC4),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        SectionTitle("全部考试")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dated.sortedBy { it.time }.forEach { ExamCard(it) }
            undated.forEach { ExamCard(it) }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "共 ${exams.size} 场 · 更新于 " +
                SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(fetchedAt)),
            color = TextDim,
            fontSize = 10.5.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ExamCard(e: ExamEvent) {
    val started = isPast(e.time)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                color = if (started) TextSub else TextMain,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                listOfNotNull(
                    e.time.ifBlank { "时间待定" },
                    e.place.ifBlank { null },
                    e.seat.takeIf { it.isNotBlank() }?.let { "座位 $it" }
                ).joinToString(" · "),
                color = TextDim,
                fontSize = 11.sp
            )
        }
        if (e.type.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Chip(e.type, if (started) TextDim else Accent)
        }
    }
}

@Composable
private fun EmptyExams(fetchedAt: Long) {
    Spacer(Modifier.height(60.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(Line)
        )
        Spacer(Modifier.height(16.dp))
        Text("暂无考试安排", color = TextSub, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "教务系统还没有发布本学期的考场安排。\n通常期末前一个月会陆续放出，届时刷新即可看到。",
            color = TextDim,
            fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "更新于 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(fetchedAt)),
            color = TextDim.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

private fun nearest(list: List<ExamEvent>): ExamEvent? {
    val upcoming = list.filter { !isPast(it.time) }.minByOrNull { it.time }
    return upcoming ?: list.maxByOrNull { it.time }
}

private fun daysUntil(time: String): Long? {
    val d = parseDate(time) ?: return null
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return (d - today) / (24 * 60 * 60 * 1000L)
}

private fun isPast(time: String): Boolean {
    val d = parseDate(time) ?: return false
    return d < System.currentTimeMillis()
}

private fun parseDate(time: String): Long? {
    val m = Regex("""(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2}))?""").find(time) ?: return null
    val cal = Calendar.getInstance()
    val year = if (cal.get(Calendar.MONTH) >= 7) {
        cal.get(Calendar.YEAR)
    } else {
        cal.get(Calendar.YEAR) - 1
    }
    return try {
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, m.groupValues[1].toInt() - 1)
        cal.set(Calendar.DAY_OF_MONTH, m.groupValues[2].toInt())
        cal.set(Calendar.HOUR_OF_DAY, m.groupValues[3].ifBlank { "0" }.toInt())
        cal.set(Calendar.MINUTE, m.groupValues[4].ifBlank { "0" }.toInt())
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    } catch (e: Exception) {
        null
    }
}

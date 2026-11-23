package cn.edu.hsnc.xjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.hsnc.xjw.data.Schedule
import cn.edu.hsnc.xjw.data.ScheduleCourse
import cn.edu.hsnc.xjw.data.ScheduleSlot
import cn.edu.hsnc.xjw.data.SectionTime
import cn.edu.hsnc.xjw.data.UrpApi
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DAY_NAMES = WEEKDAY_NAMES

private val SlotColors = listOf(
    Color(0xFF4F7CFF),
    Color(0xFF35C79B),
    Color(0xFFE0A94B),
    Color(0xFF9B7BFF),
    Color(0xFFE06C6C),
    Color(0xFF3FB6D6)
)

private fun colorOf(name: String): Color = SlotColors[abs(name.hashCode()) % SlotColors.size]

private fun todayIndex(): Int = todayWeekday()

@Composable
fun ScheduleScreen(api: UrpApi, initial: Schedule?) {
    val scope = rememberCoroutineScope()
    var schedule by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val homeWeek = remember { initial?.week ?: 0 }

    fun goto(week: Int) {
        if (busy) return
        busy = true
        err = null
        scope.launch {
            try {
                schedule = api.loadScheduleOnly(week)
            } catch (e: Exception) {
                err = e.message ?: "课表加载失败"
            } finally {
                busy = false
            }
        }
    }

    val s = schedule
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        if (s == null) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text("课表暂不可用", color = TextDim, fontSize = 12.sp)
            }
            return@Column
        }

        WeekHeader(s, busy, onPrev = { goto((s.week - 1).coerceAtLeast(1)) }, onNext = { goto((s.week + 1).coerceAtMost(s.totalWeeks)) })

        err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Bad, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(14.dp))

        val today = todayIndex()
        val currentWeek = s.week == homeWeek
        if (currentWeek) {
            val todayCourses = s.scheduledCourses.filter { c -> c.slots.any { it.day == today } }
            SectionTitle("今天 · ${DAY_NAMES[today - 1]}")
            Spacer(Modifier.height(10.dp))
            if (todayCourses.isEmpty()) {
                CardBox(Modifier.fillMaxWidth()) {
                    Text("今天没有课", color = TextDim, fontSize = 12.5.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todayCourses.forEach { c ->
                        CourseCard(
                            course = c,
                            slots = c.slots.filter { it.day == today },
                            highlight = true
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        SectionTitle("本周课程")
        Spacer(Modifier.height(10.dp))

        val grouped = (1..7).map { day -> day to s.scheduledCourses.filter { c -> c.slots.any { it.day == day } } }
        if (grouped.all { it.second.isEmpty() }) {
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    Text("第 ${s.week} 周没有排课", color = TextSub, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("可能是还未开学、已结课，或处在考试周", color = TextDim, fontSize = 11.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                grouped.forEach { (day, list) ->
                    if (list.isEmpty()) return@forEach
                    DayBlock(day, list, day == today && currentWeek)
                }
            }
        }

        if (s.unscheduledCourses.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("第 ${s.week} 周没有课的课程")
            Spacer(Modifier.height(4.dp))
            Text(
                "这些课可能已经结课、还没开课，或者不需要按周上课。换到其他周次可以确认。",
                color = TextDim,
                fontSize = 10.5.sp
            )
            Spacer(Modifier.height(10.dp))
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    s.unscheduledCourses.forEachIndexed { i, c ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(colorOf(c.name).copy(alpha = 0.6f))
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.name, color = TextSub, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    listOfNotNull(
                                        c.teacher.ifBlank { null },
                                        "${fmtCredit(c.credit)} 学分"
                                    ).joinToString(" · "),
                                    color = TextDim, fontSize = 10.5.sp
                                )
                            }
                            Text("未排课", color = TextDim, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "共 ${s.courses.size} 门课 · ${fmtCredit(s.totalCredit)} 学分",
            color = TextDim,
            fontSize = 10.5.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "上课时间按${SectionTime.seasonLabel()}换算（每年 5–8 月为夏季作息，下午晚上顺延 30 分钟）",
            color = TextDim.copy(alpha = 0.8f),
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeekHeader(s: Schedule, busy: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    CardBox(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.termLabel.ifBlank { "本学期" },
                    color = TextSub,
                    fontSize = 11.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("第 ${s.week} 周", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    if (busy) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            color = Accent, strokeWidth = 1.5.dp,
                            modifier = Modifier.size(12.dp).padding(bottom = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${s.scheduledCourses.size} 门有课时安排 · 共 ${fmtCredit(s.totalCredit)} 学分",
                    color = TextDim,
                    fontSize = 10.5.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavButton("‹", enabled = !busy && s.week > 1, onClick = onPrev)
                Spacer(Modifier.width(8.dp))
                NavButton("›", enabled = !busy && s.week < s.totalWeeks, onClick = onNext)
            }
        }
    }
}

@Composable
private fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg2)
            .border(0.5.dp, Line, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) TextMain else TextDim.copy(alpha = 0.4f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DayBlock(day: Int, courses: List<ScheduleCourse>, isToday: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isToday) Accent else Line)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                DAY_NAMES[day - 1],
                color = if (isToday) Accent else TextSub,
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.width(6.dp))
            Text("${courses.size} 门", color = TextDim, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            courses.forEach { c ->
                CourseCard(c, c.slots.filter { it.day == day }, false)
            }
        }
    }
}

@Composable
private fun CourseCard(course: ScheduleCourse, slots: List<ScheduleSlot>, highlight: Boolean) {
    val tint = colorOf(course.name)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) tint.copy(alpha = 0.10f) else CardBg)
            .border(
                0.5.dp,
                if (highlight) tint.copy(alpha = 0.45f) else Line,
                RoundedCornerShape(14.dp)
            )
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(tint)
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                course.name,
                color = TextMain,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            slots.sortedBy { it.start }.forEach { sl ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sl.sessionText, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    if (sl.timeText.isNotBlank()) {
                        Text(" · ${sl.timeText}", color = TextSub, fontSize = 11.sp)
                    }
                }
                val sub = listOfNotNull(
                    sl.place.ifBlank { null },
                    sl.weeks.ifBlank { null }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        color = TextDim,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    course.teacher.ifBlank { null },
                    "${fmtCredit(course.credit)} 学分",
                    course.category.ifBlank { null }
                ).joinToString(" · "),
                color = TextDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

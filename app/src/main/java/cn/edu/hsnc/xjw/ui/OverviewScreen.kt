package cn.edu.hsnc.xjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import cn.edu.hsnc.xjw.data.AppData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(d: AppData, onOpenSchedule: () -> Unit = {}, onOpenExam: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    d.name.take(1),
                    color = Accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(d.major.ifBlank { "—" }, d.studentId).joinToString(" · "),
                    color = TextDim,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        CardBox(Modifier.fillMaxWidth(), bg = Color(0xFF141A2B)) {
            Column {
                Text("加权平均绩点", color = Color(0xFF8C9DC4), fontSize = 11.5.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format("%.3f", d.gpa),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        " / 5.0",
                        color = Color(0xFF8C9DC4),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Column {
                        Text("已修学分", color = Color(0xFF8C9DC4), fontSize = 10.5.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(fmtCredit(d.earnedCredit), color = Color.White, fontSize = 16.sp)
                    }
                    Column {
                        Text("课程门数", color = Color(0xFF8C9DC4), fontSize = 10.5.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("${d.allCourses.size}", color = Color.White, fontSize = 16.sp)
                    }
                    Column {
                        Text("学期数", color = Color(0xFF8C9DC4), fontSize = 10.5.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("${d.semesters.size}", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        CardBox(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("培养方案进度", color = TextMain, fontSize = 14.sp)
                    Text(percent(d.progress), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                ProgressLine(d.progress)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatColumn("已修学分", fmtCredit(d.earnedCredit), color = Accent)
                    StatColumn("方案要求", fmtCredit(d.requiredCredit), color = TextMain)
                    StatColumn(
                        "还差",
                        fmtCredit((d.requiredCredit - d.earnedCredit).coerceAtLeast(0.0)),
                        color = Warn
                    )
                }
            }
        }

        // ---- 今日课程 / 最近考试 双卡 ----
        val sched = d.schedule
        if (sched != null) {
            val today = todayWeekday()
            val todayCourses = sched.scheduledCourses
                .filter { c -> c.slots.any { it.day == today } }
                .sortedBy { c -> c.slots.filter { it.day == today }.minOf { it.start } }

            Spacer(Modifier.height(18.dp))
            SectionTitle("今天 · ${WEEKDAY_NAMES[today - 1]}") {
                Text(
                    "整周课表",
                    color = Accent,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clickable { onOpenSchedule() }
                        .padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            if (todayCourses.isEmpty()) {
                CardBox(Modifier.fillMaxWidth()) {
                    Text("今天没有课", color = TextDim, fontSize = 12.5.sp)
                }
            } else {
                CardBox(Modifier.fillMaxWidth()) {
                    Column {
                        todayCourses.forEachIndexed { i, c ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
                            val sl = c.slots.filter { it.day == today }.minByOrNull { it.start }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    sl?.sessionText ?: "—",
                                    color = Accent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(58.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name,
                                        color = TextMain,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val detail = listOfNotNull(
                                        sl?.timeText?.ifBlank { null },
                                        sl?.place?.ifBlank { null }
                                    ).joinToString(" · ")
                                    if (detail.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            detail,
                                            color = TextDim,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val nextExam = d.exams.filter { it.time.isNotBlank() }.minByOrNull { it.time }
            if (nextExam != null) {
                Spacer(Modifier.height(12.dp))
                CardBox(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenExam() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("最近考试", color = TextDim, fontSize = 10.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                nextExam.title,
                                color = TextMain,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                listOfNotNull(
                                    nextExam.time.ifBlank { null },
                                    nextExam.place.ifBlank { null }
                                ).joinToString(" · "),
                                color = TextSub,
                                fontSize = 10.5.sp
                            )
                        }
                        Chip("查看", Accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("学期绩点")
        Spacer(Modifier.height(10.dp))

        CardBox(Modifier.fillMaxWidth()) {
            Column {
                d.semesters.sortedByDescending { it.order }.forEachIndexed { i, s ->
                    if (i > 0) {
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.name,
                            color = TextSub,
                            fontSize = 11.5.sp,
                            modifier = Modifier.width(118.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(Modifier.weight(1f)) {
                            ProgressLine((s.gpa / 5.0).toFloat(), if (s.gpa >= 3.0) Good else Accent, 5)
                        }
                        Text(
                            fmtGpa(s.gpa),
                            color = TextMain,
                            fontSize = 12.5.sp,
                            modifier = Modifier
                                .width(38.dp)
                                .padding(start = 10.dp)
                        )
                    }
                }
                if (d.semesters.isEmpty()) {
                    Text("暂无成绩数据", color = TextDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        val last = d.semesters.maxByOrNull { it.order }
        if (last != null && last.courses.isNotEmpty()) {
            SectionTitle("最近一学期 · ${last.name}")
            Spacer(Modifier.height(10.dp))
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    last.courses.take(4).forEachIndexed { i, c ->
                        if (i > 0) {
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
                        }
                        CourseRow(
                            name = c.name,
                            attr = c.attr,
                            credit = c.credit,
                            score = fmtScore(c.score),
                            gpa = c.gpa?.let { String.format("%.1f", it) } ?: "—",
                            scoreColor = scoreColorOf(c.score)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "更新于 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(d.fetchedAt)),
            color = TextDim,
            fontSize = 10.5.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

package cn.edu.hsnc.xjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.hsnc.xjw.data.AppData
import cn.edu.hsnc.xjw.data.Module
import cn.edu.hsnc.xjw.data.PlanCourse

@Composable
fun PlanScreen(d: AppData) {
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val inProgress = d.currentTermCourseNumbers

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        d.major.ifBlank { "培养方案" },
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatColumn("方案要求", fmtCredit(d.requiredCredit), "学分")
                        StatColumn("方案内已修", fmtCredit(d.planPassedCredit), "学分", Accent)
                        StatColumn("方案课程", "${d.planTotalCourses}", "门")
                    }
                    Spacer(Modifier.height(14.dp))
                    ProgressLine(
                        if (d.requiredCredit > 0) (d.planPassedCredit / d.requiredCredit).toFloat() else 0f
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "另：成绩单中累计已修 ${fmtCredit(d.earnedCredit)} 学分（含方案外课程）",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    "模块完成情况 · ${d.modules.size} 个模块",
                    color = TextDim,
                    fontSize = 11.5.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "点课程名可展开课程号" + if (inProgress.isNotEmpty())
                        "；标「此学期进行中」的课已选但成绩未出" else "",
                    color = TextDim.copy(alpha = 0.75f),
                    fontSize = 10.sp
                )
            }
        }

        items(d.modules) { m ->
            ModuleCard(
                m = m,
                isOpen = expanded.contains(m.name),
                inProgress = inProgress,
                onToggle = {
                    expanded = if (expanded.contains(m.name)) expanded - m.name else expanded + m.name
                }
            )
        }

        if (inProgress.isNotEmpty()) {
            item {
                Text(
                    "标有「此学期进行中」的课程已在本学期选课名单里，成绩尚未录入。",
                    color = TextDim,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(
    m: Module,
    isOpen: Boolean,
    inProgress: Set<String>,
    onToggle: () -> Unit
) {
    // 记录本模块内展开课程号的课程（key 用课程号+名称，避免同名不同号相撞）
    var openCourses by remember { mutableStateOf<Set<String>>(emptySet()) }

    CardBox(Modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onToggle() }
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        m.name,
                        color = TextMain,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "要求 ${fmtCredit(m.required)} · 已修 ${fmtCredit(m.passedCredit)} · " +
                            "${m.passedCourses}/${m.courses.size} 门",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
                if (m.required <= 0.0) {
                    Chip("无需修读", TextDim)
                } else {
                    Chip(if (m.done) "已完成" else "进行中", if (m.done) Good else Warn)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isOpen) "收起" else "展开", color = TextDim, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(10.dp))
            ProgressLine(m.progress, if (m.done) Good else Accent, 5)
            if (!m.done && m.missingRequired > 0) {
                Spacer(Modifier.height(8.dp))
                Text("必修课还有 ${m.missingRequired} 门未修读", color = Warn, fontSize = 10.5.sp)
            }
            if (isOpen) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
                m.courses.forEachIndexed { i, c ->
                    if (i > 0) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line.copy(alpha = 0.6f)))
                    }
                    val key = "${c.code}|${c.name}"
                    PlanCourseRow(
                        c = c,
                        showCode = openCourses.contains(key),
                        // 只有「未通过 + 本学期在选」才算进行中
                        inProgress = !c.passed && c.code.isNotBlank() && inProgress.contains(c.code),
                        onToggle = {
                            openCourses = if (openCourses.contains(key)) openCourses - key
                            else openCourses + key
                        }
                    )
                }
                if (m.courses.isEmpty()) {
                    Text("该模块暂无课程明细", color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PlanCourseRow(
    c: PlanCourse,
    showCode: Boolean,
    inProgress: Boolean,
    onToggle: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable { onToggle() }) {
        CourseRow(
            name = c.name,
            attr = when {
                c.attr.isNotBlank() -> c.attr
                c.passed -> "已通过"
                else -> "未修读"
            },
            credit = c.credit,
            score = fmtScore(c.score),
            gpa = if (c.passed) "✓" else "—",
            scoreColor = scoreColorOf(c.score),
            passed = c.passed
        )

        if (inProgress) {
            Box(Modifier.padding(bottom = 8.dp)) {
                InProgressTag()
            }
        }

        if (showCode) {
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg2)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("课程号", color = TextDim, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.code.ifBlank { "未提供" },
                    color = TextMain,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (c.term.isNotBlank()) {
                    Spacer(Modifier.width(14.dp))
                    Text("建议学期 ${c.term}", color = TextDim, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 「此学期进行中」小标签，和普通 Chip 区分开：实底 + 呼吸点 */
@Composable
private fun InProgressTag() {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Accent.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .padding(end = 5.dp)
                .width(4.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accent)
        )
        Text("此学期进行中", color = Accent, fontSize = 10.sp)
    }
}

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.hsnc.xjw.data.AppData
import cn.edu.hsnc.xjw.data.Semester

@Composable
fun ScoresScreen(d: AppData) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "共 ${d.semesters.size} 个学期 · ${d.allCourses.size} 门课程 · 平均绩点 ${fmtGpa(d.gpa)}",
                color = TextDim,
                fontSize = 11.5.sp
            )
        }
        items(d.semesters.sortedByDescending { it.order }) { s ->
            SemesterCard(s)
        }
    }
}

@Composable
private fun SemesterCard(s: Semester) {
    CardBox(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${s.courses.size} 门 · ${fmtCredit(s.credit)} 学分",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
                Text(fmtGpa(s.gpa), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(" 绩点", color = TextDim, fontSize = 10.5.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
            s.courses.forEachIndexed { i, c ->
                if (i > 0) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line.copy(alpha = 0.6f)))
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

package cn.edu.hsnc.xjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    bg: Color = CardBg,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(0.5.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) { content() }
}

@Composable
fun ProgressLine(progress: Float, color: Color = Accent, height: Int = 6) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2C36))
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(height.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun StatColumn(label: String, value: String, unit: String = "", color: Color = TextMain) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextDim, fontSize = 11.sp)
    }
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = TextMain)
        trailing?.invoke()
    }
}

@Composable
fun Chip(text: String, color: Color = Good, filled: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(0.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp)
    }
}

@Composable
fun CourseRow(
    name: String,
    attr: String,
    credit: Double,
    score: String,
    gpa: String,
    scoreColor: Color = TextMain,
    passed: Boolean? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (passed != null) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (passed) Good else Warn)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    name,
                    color = if (passed == false) TextSub else TextMain,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "$attr · ${fmtCredit(credit)} 学分",
                color = TextDim,
                fontSize = 11.sp
            )
        }
        Text(score, color = scoreColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            gpa,
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier
                .width(46.dp)
                .padding(start = 10.dp)
        )
    }
}

/** 学分保留最多两位小数，去掉无意义的尾随零：21.75 → "21.75"，1.5 → "1.5"，3.0 → "3" */
fun fmtCredit(v: Double): String {
    if (v % 1.0 == 0.0) return v.toInt().toString()
    val s = String.format("%.2f", v)
    return if (s.endsWith("0")) s.dropLast(1) else s
}

fun fmtGpa(v: Double): String = String.format("%.2f", v)

fun fmtScore(v: Double?): String {
    if (v == null) return "—"
    return if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
}

fun scoreColorOf(v: Double?): Color = when {
    v == null -> TextDim
    v >= 90 -> Good
    v >= 60 -> TextMain
    else -> Bad
}

fun percent(v: Float): String = "${(v * 100).roundToInt()}%"

/** 1=周一 … 7=周日，与课表接口的 classDay 对齐 */
fun todayWeekday(): Int =
    ((java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1

val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")


@Composable
fun LoadingScreen(text: String = "正在读取教务系统…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(14.dp))
            Text(text, color = TextSub, fontSize = 12.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("加载失败", color = TextMain, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Row {
                TextButton(onClick = onRetry) { Text("重试", color = Accent) }
                TextButton(onClick = onLogout) { Text("重新登录", color = TextSub) }
            }
        }
    }
}

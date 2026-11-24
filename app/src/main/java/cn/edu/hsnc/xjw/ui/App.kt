package cn.edu.hsnc.xjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.hsnc.xjw.data.AppData
import cn.edu.hsnc.xjw.data.SessionExpiredException
import cn.edu.hsnc.xjw.data.UrpApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NAV_LABELS = listOf("概览", "课表", "成绩", "学业", "考试")

@Composable
fun AppRoot(startTab: Int = 0) {
    val ctx = LocalContext.current
    val api = remember { UrpApi(ctx) }
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf<AppData?>(null) }
    var loggedIn by remember { mutableStateOf(api.hasSession()) }
    var loading by remember { mutableStateOf(false) }
    var autoLogging by remember { mutableStateOf(!api.hasSession() && api.hasSavedCredential()) }
    var autoFailMsg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(startTab.coerceIn(0, NAV_LABELS.size - 1)) }
    var banner by remember { mutableStateOf<String?>(null) }

    fun applyData(d: AppData) {
        data = d
        if (d.gradeChange.hasChange) {
            banner = d.gradeChange.summary +
                d.gradeChange.items.take(2).joinToString("、", "：", "")
        }
        if (d.schedule == null) {
            scope.launch {
                runCatching {
                    val s = withContext(Dispatchers.IO) { api.loadScheduleOnly() }
                    data = d.copy(schedule = s)
                }
            }
        }
    }

    fun load() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val d = withContext(Dispatchers.IO) { api.loadAll() }
                applyData(d)
            } catch (e: SessionExpiredException) {
                // 会话过期：尝试用保存的凭据静默重登一次
                val ok = runCatching { withContext(Dispatchers.IO) { api.tryAutoLogin() } }.getOrDefault(false)
                val d = if (ok) {
                    runCatching { withContext(Dispatchers.IO) { api.loadAll() } }.getOrNull()
                } else null
                if (d != null) {
                    applyData(d)
                } else {
                    api.clearSession()
                    loggedIn = false
                    data = null
                    error = null
                    autoFailMsg = api.lastAutoLoginMessage
                        ?: "登录状态已过期，验证码已尽量帮你填好，核对后点登录即可"
                }
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (autoLogging) {
            val ok = runCatching { withContext(Dispatchers.IO) { api.tryAutoLogin() } }.getOrDefault(false)
            autoLogging = false
            if (ok) {
                loggedIn = true
            } else {
                // 清掉过期会话，下次启动才会重新走一遍自动登录
                api.clearSession()
                autoFailMsg = api.lastAutoLoginMessage
                    ?: "自动登录未成功，验证码已尽量帮你填好，核对后点登录即可"
            }
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn && data == null) load()
    }

    when {
        autoLogging -> Column(
            Modifier
                .fillMaxSize()
                .background(Bg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(14.dp))
            Text("正在自动登录…", color = TextSub, fontSize = 12.sp)
        }

        !loggedIn -> LoginScreen(api, autoFailMsg) {
            loggedIn = true
            autoFailMsg = null
            data = null
            error = null
            tab = 0
            banner = null
        }

        data == null && error != null -> Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .statusBarsPadding()
        ) {
            ErrorScreen(error!!, onRetry = { load() }, onLogout = {
                api.clearSession()
                api.forgetCredential()
                loggedIn = false
                data = null
                error = null
            })
        }

        data == null -> Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .statusBarsPadding()
        ) { LoadingScreen() }

        else -> MainScaffold(
            api = api,
            d = data!!,
            tab = tab,
            onTab = { tab = it },
            loading = loading,
            banner = banner,
            onDismissBanner = { banner = null },
            onRefresh = { load() },
            onLogout = {
                api.clearSession()
                api.forgetCredential()
                loggedIn = false
                data = null
                error = null
                banner = null
            }
        )
    }
}

@Composable
private fun MainScaffold(
    api: UrpApi,
    d: AppData,
    tab: Int,
    onTab: (Int) -> Unit,
    loading: Boolean,
    banner: String?,
    onDismissBanner: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("衡院教务", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            if (loading) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "刷新",
                color = TextSub,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(enabled = !loading) { onRefresh() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text(
                "退出",
                color = TextDim,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { onLogout() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        banner?.let {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Good.copy(alpha = 0.12f))
                    .border(0.5.dp, Good.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { onTab(2) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Good))
                Spacer(Modifier.width(8.dp))
                Text(
                    it,
                    color = Good,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "知道了",
                    color = TextDim,
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .clickable { onDismissBanner() }
                        .padding(start = 8.dp)
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> OverviewScreen(d, onOpenSchedule = { onTab(1) }, onOpenExam = { onTab(4) })
                1 -> ScheduleScreen(api, d.schedule)
                2 -> ScoresScreen(d)
                3 -> PlanScreen(d)
                else -> ExamScreen(d.exams, d.fetchedAt)
            }
        }

        BottomBar(tab, onTab)
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NAV_LABELS.forEachIndexed { i, label ->
            val active = i == tab
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onTab(i) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = if (active) Accent else TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(if (active) Accent else Color.Transparent)
                )
            }
        }
    }
}

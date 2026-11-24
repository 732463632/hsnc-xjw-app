package cn.edu.hsnc.xjw.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SessionExpiredException : Exception("登录状态已过期，请重新登录")
class UrpException(message: String) : Exception(message)

data class Captcha(val token: String, val jpeg: ByteArray)

/** 登录结果：区分「验证码错误」可立即换一张重试，其余错误不再重试 */
sealed class LoginOutcome {
    object Success : LoginOutcome()
    object BadCaptcha : LoginOutcome()
    data class Failure(val message: String) : LoginOutcome()
}

class UrpApi(context: Context) {

    companion object {
        const val BASE = "http://xjw.hsnc.edu.cn:7777"
        private const val FORM = "application/x-www-form-urlencoded; charset=UTF-8"
    }

    val appContext: Context = context.applicationContext
    private val cookiePrefs = appContext.getSharedPreferences("urp_cookies", Context.MODE_PRIVATE)
    private val settingPrefs = appContext.getSharedPreferences("urp_settings", Context.MODE_PRIVATE)
    private val gradeWatcher = GradeWatcher(appContext)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(PrefsCookieJar(cookiePrefs))
        .build()

    var lastUser: String
        get() = settingPrefs.getString("last_user", "") ?: ""
        set(v) = settingPrefs.edit().putString("last_user", v).apply()

    /**
     * 是否处于已登录状态。
     *
     * 不能只看 cookie：抓验证码时服务端就会种下 JSESSIONID，光凭「有 cookie」会在从未登录的
     * 情况下误判为已登录，冷启动先白跑一遍加载、再弹「登录已过期」。所以额外记一个登录成功标记。
     */
    fun hasSession(): Boolean =
        settingPrefs.getBoolean("logged_in", false) && cookiePrefs.all.isNotEmpty()

    fun clearSession() {
        settingPrefs.edit().putBoolean("logged_in", false).apply()
        cookiePrefs.edit().clear().apply()
    }

    // ---------------- 基础请求 ----------------

    private fun getBytes(path: String, referer: String? = null): ByteArray {
        val b = Request.Builder().url(if (path.startsWith("http")) path else BASE + path)
        referer?.let { b.header("Referer", BASE + it) }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw UrpException("服务器返回 ${r.code}")
            return r.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun getText(path: String, referer: String? = null, allowLoginPage: Boolean = false): String {
        return try {
            getTextOnce(path, referer, allowLoginPage)
        } catch (e: SessionExpiredException) {
            if (allowLoginPage || !ensureSession()) throw e
            getTextOnce(path, referer, allowLoginPage)
        }
    }

    private fun getTextOnce(path: String, referer: String?, allowLoginPage: Boolean): String {
        val b = Request.Builder().url(if (path.startsWith("http")) path else BASE + path)
        referer?.let { b.header("Referer", BASE + it) }
        client.newCall(b.build()).execute().use { r ->
            val finalUrl = r.request.url.toString()
            if (!allowLoginPage && finalUrl.contains("/login")) throw SessionExpiredException()
            if (!r.isSuccessful) throw UrpException("服务器返回 ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    // ---------------- 会话保活 ----------------

    private val reloginLock = Any()
    @Volatile private var lastReloginOk = 0L

    /** 会话失效时用保存的凭据自动重登；同一时刻只允许一次重登 */
    private fun ensureSession(): Boolean = synchronized(reloginLock) {
        if (System.currentTimeMillis() - lastReloginOk < 5000) return true
        if (tryAutoLogin()) {
            lastReloginOk = System.currentTimeMillis()
            true
        } else false
    }

    // ---------------- 登录 ----------------

    fun fetchCaptcha(): Captcha {
        val page = getTextOnce("/login", null, allowLoginPage = true)
        val token = Parser.parseToken(page)
        val bytes = getBytes("/img/captcha.jpg?${System.currentTimeMillis()}")
        return Captcha(token, bytes)
    }

    fun login(user: String, password: String, captcha: String, token: String): LoginOutcome {
        val body = listOf(
            "lang" to "zh",
            "tokenValue" to token,
            "j_username" to user,
            "j_password" to encodePassword(password),
            "j_captcha" to captcha,
            "schoolId" to "100044"
        ).joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }
            .toRequestBody(FORM.toMediaType())

        val req = Request.Builder()
            .url("$BASE/j_spring_security_check")
            .header("Referer", "$BASE/login")
            .header("Origin", BASE)
            .post(body)
            .build()

        client.newCall(req).execute().use { r ->
            val finalUrl = r.request.url.toString()
            val text = r.body?.string() ?: ""
            // 失败时服务端可能返回 302 到 /login，也可能直接 200 重新渲染登录页，
            // 所以两种都要识别出来，避免把失败当成功。
            val loginPage = finalUrl.contains("/login") ||
                text.contains("id=\"input_password\"") || text.contains("name=\"j_password\"")
            if (!loginPage) {
                lastUser = user
                settingPrefs.edit().putBoolean("logged_in", true).apply()
                return LoginOutcome.Success
            }
            if (finalUrl.contains("errorCode=badCaptcha")) return LoginOutcome.BadCaptcha
            if (text.contains("验证码错误")) return LoginOutcome.BadCaptcha
            if (text.contains("锁定")) {
                return LoginOutcome.Failure("账号已被锁定，请稍后再试或联系教务")
            }
            // 服务端提示统一以「发生错误！」开头，去掉前缀才是人话，别整段丢掉
            val msg = Regex("""<div[^>]*class="[^"]*(?:alert|error)[^"]*"[^>]*>([\s\S]{0,200}?)</div>""")
                .find(text)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), "")
                ?.replace(Regex("发生错误[!！]?"), "")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
            return LoginOutcome.Failure(
                msg?.takeIf { it.isNotBlank() && it.length < 60 }
                    ?: "登录失败，请检查学号和密码"
            )
        }
    }

    // ---------------- 自动登录 ----------------

    /** 是否已保存凭据（用于决定要不要在启动时静默登录） */
    fun hasSavedCredential(): Boolean = SecureStore.has(appContext)

    /** 用户「记住密码」的意愿，默认开启；与是否已存凭据分开，避免刚装好就显示成关闭 */
    var saveCredentialPref: Boolean
        get() = settingPrefs.getBoolean("save_cred", true)
        set(v) = settingPrefs.edit().putBoolean("save_cred", v).apply()

    fun saveCredential(user: String, password: String) {
        SecureStore.save(appContext, user, password)
    }

    fun forgetCredential() {
        SecureStore.clear(appContext)
    }

    /** 识别器加载一次即可复用，避免每次自动登录都重新读 500KB 权重 */
    private val solver: CaptchaSolver by lazy { CaptchaSolver(appContext) }

    /** 自动登录失败时给用户看的具体原因（例如密码已失效），null 表示用默认文案 */
    @Volatile var lastAutoLoginMessage: String? = null

    /**
     * 用保存的凭据自动登录。
     *
     * 实测识别率并不高（四个字符全中的概率只有两三成），所以只提交一次：
     * 成了就直接进主页，没成就退回登录页并把验证码填好，让用户点一下即可。
     * 学校对连续失败会锁账号，绝不能拿用户的账号去反复试验证码。
     */
    fun tryAutoLogin(): Boolean {
        lastAutoLoginMessage = null
        val cred = SecureStore.load(appContext) ?: return false
        if (!solver.available) return false

        var fetched = 0
        while (fetched < 3) {
            val cap = runCatching { fetchCaptcha() }.getOrNull() ?: return false
            fetched++
            val guess = solver.solve(cap.jpeg) ?: continue
            // 太没把握的图就不提交了，换一张再识别
            if (guess.confidence < CaptchaSolver.MIN_CONFIDENCE_FILL) continue
            when (val r = login(cred.first, cred.second, guess.code, cap.token)) {
                LoginOutcome.Success -> {
                    lastReloginOk = System.currentTimeMillis()
                    return true
                }
                LoginOutcome.BadCaptcha -> return false   // 只提交一次，失败即回退
                is LoginOutcome.Failure -> {
                    // 密码已经不对了（在学校改过密码），立刻丢掉本地凭据，
                    // 否则以后每次冷启动都拿旧密码去撞，最后真的会被锁号。
                    if (r.message.contains("密码") || r.message.contains("锁定")) {
                        forgetCredential()
                        lastAutoLoginMessage = "保存的密码已失效（${r.message}），已清除，请重新输入"
                    }
                    return false
                }
            }
        }
        return false
    }

    // ---------------- 数据 ----------------

    suspend fun loadAll(): AppData = coroutineScope {
        val indexDeferred = async(Dispatchers.IO) { getText("/index") }
        val scoreDeferred = async(Dispatchers.IO) { loadScores() }
        val planDeferred = async(Dispatchers.IO) { loadPlan() }
        val scheduleDeferred = async(Dispatchers.IO) { runCatching { loadSchedule() }.getOrNull() }
        val examDeferred = async(Dispatchers.IO) { runCatching { loadExams() }.getOrDefault(emptyList()) }

        val index = indexDeferred.await()
        val name = Parser.parseUserName(index)
        val sid = Parser.parseUserId(index)

        val semesters = scoreDeferred.await()
        val plan = planDeferred.await()
        val schedule = scheduleDeferred.await()
        val exams = examDeferred.await()

        val graded = semesters.flatMap { it.courses }.filter { it.gpa != null }
        val gradeCredit = graded.sumOf { it.credit }
        val gpa = if (gradeCredit > 0) graded.sumOf { it.gpa!! * it.credit } / gradeCredit else 0.0
        val earned = semesters.flatMap { it.courses }.sumOf { it.credit }

        val change = gradeWatcher.check(semesters)

        AppData(
            name = name.ifBlank { "同学" },
            studentId = sid.ifBlank { lastUser },
            major = plan?.major ?: "",
            gpa = gpa,
            earnedCredit = earned,
            requiredCredit = plan?.totalCredit ?: 0.0,
            planTotalCourses = plan?.totalCourses ?: 0,
            semesters = semesters,
            modules = plan?.modules ?: emptyList(),
            schedule = schedule,
            exams = exams,
            gradeChange = change
        )
    }

    /** 切换周次只重拉课表，其他数据不动 */
    suspend fun loadScheduleOnly(week: Int? = null): Schedule =
        withContext(Dispatchers.IO) { loadSchedule(week) }

    private fun loadScores(): List<Semester> {
        val pagePath = "/student/integratedQuery/scoreQuery/allPassingScores/index"
        val html = getText(pagePath)
        val url = Regex("""var\s+url\s*=\s*"([^"]*allPassingScores/callback)"""")
            .find(html)?.groupValues?.get(1)
            ?: Regex("""["'](/student/[^"']*allPassingScores/callback)["']""")
                .find(html)?.groupValues?.get(1)
            ?: throw UrpException("成绩页面结构变化，未找到数据接口")
        val json = getText(url, referer = pagePath)
        return Parser.parseScores(json)
    }

    private fun loadPlan(): Parser.Plan? {
        val html = getText("/student/integratedQuery/planCompletion/index")
        return Parser.parsePlan(html)
    }

    private fun loadSchedule(week: Int? = null): Schedule {
        val pagePath = "/student/personalSenate/giveLessonInfo/thisSemesterSchedule/weekLySchedule"
        val html = getText(pagePath)
        val (curWeek, term) = Parser.parseScheduleMeta(html)
        val w = (week ?: curWeek).coerceIn(1, 24)
        val json = getText(
            "/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/weekLySchedule/callback/$w",
            referer = pagePath
        )
        return Parser.parseSchedule(json, w, term)
    }

    private fun loadExams(): List<ExamEvent> {
        val path = "/student/examinationManagement/examPlan/detail"
        return try {
            Parser.parseExams(getText(path, referer = "/student/examinationManagement/examPlan/index"))
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------------- 工具 ----------------

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            val v = b.toInt() and 0xff
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /** 与登录页 md5.min.js 一致：md5(md5(pwd+盐)) + "*" + md5(md5(pwd)) */
    fun encodePassword(pwd: String): String =
        md5(md5(pwd + "{Urp602019}")) + "*" + md5(md5(pwd))
}

private class PrefsCookieJar(private val prefs: android.content.SharedPreferences) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val e = prefs.edit()
        cookies.forEach { e.putString(it.name, it.value) }
        e.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        prefs.all.entries.mapNotNull { (k, v) ->
            val value = v as? String ?: return@mapNotNull null
            Cookie.Builder().domain(url.host).path("/").name(k).value(value).build()
        }
}

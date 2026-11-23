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

class UrpApi(context: Context) {

    companion object {
        const val BASE = "http://xjw.hsnc.edu.cn:7777"
        private const val FORM = "application/x-www-form-urlencoded; charset=UTF-8"
    }

    private val appContext = context.applicationContext
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

    fun hasSession(): Boolean = cookiePrefs.all.isNotEmpty()

    fun clearSession() = cookiePrefs.edit().clear().apply()

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
        val b = Request.Builder().url(if (path.startsWith("http")) path else BASE + path)
        referer?.let { b.header("Referer", BASE + it) }
        client.newCall(b.build()).execute().use { r ->
            val finalUrl = r.request.url.toString()
            if (!allowLoginPage && finalUrl.contains("/login")) throw SessionExpiredException()
            if (!r.isSuccessful) throw UrpException("服务器返回 ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    // ---------------- 登录 ----------------

    suspend fun fetchCaptcha(): Captcha {
        val page = getText("/login", allowLoginPage = true)
        val token = Parser.parseToken(page)
        val bytes = getBytes("/img/captcha.jpg?${System.currentTimeMillis()}")
        return Captcha(token, bytes)
    }

    /** 成功返回 null，失败返回提示语 */
    suspend fun login(user: String, password: String, captcha: String, token: String): String? {
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
            if (r.request.url.toString().contains("/login")) {
                val text = r.body?.string() ?: ""
                val msg = Regex("""<div[^>]*class="[^"]*(?:alert|error)[^"]*"[^>]*>([\s\S]{0,120}?)</div>""")
                    .find(text)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
                return msg?.takeIf { it.isNotBlank() } ?: "登录失败，请检查学号、密码和验证码"
            }
        }
        lastUser = user
        return null
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

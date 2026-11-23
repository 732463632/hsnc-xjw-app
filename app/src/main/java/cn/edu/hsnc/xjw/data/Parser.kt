package cn.edu.hsnc.xjw.data

import org.json.JSONArray
import org.json.JSONObject

object Parser {

    private fun clean(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .trim()

    /** URP 会在课程名前加 ▲▼●★ 之类的标记（学位课、主干课等），界面上不显示 */
    private val MARKS = Regex("""^[\s▲▼●★△▽○◆◇■□※·\u3000]+""")

    private fun stripMarks(s: String): String = s.replace(MARKS, "").trim()

    fun parseUserName(html: String): String =
        Regex("""欢迎您[，,]\s*</small>\s*([^\s<]+)""").find(html)?.groupValues?.get(1) ?: ""

    fun parseUserId(html: String): String =
        Regex("""id="userId"\s+value="\s*([0-9A-Za-z]+)""").find(html)?.groupValues?.get(1) ?: ""

    fun parseToken(html: String): String =
        Regex("""id="tokenValue"[^>]*value="([^"]*)"""").find(html)?.groupValues?.get(1) ?: ""

    /** 成绩接口返回的 lnList：按学期分组 */
    fun parseScores(json: String): List<Semester> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val arr = root.optJSONArray("lnList") ?: return emptyList()
        val out = ArrayList<Semester>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val cl = s.optJSONArray("cjList") ?: continue
            val courses = ArrayList<Course>()
            for (j in 0 until cl.length()) {
                val c = cl.optJSONObject(j) ?: continue
                val name = stripMarks(c.optString("courseName"))
                if (name.isEmpty()) continue
                val credit = c.optDouble("credit", 0.0)
                val score = if (c.isNull("courseScore")) null else c.optDouble("courseScore", Double.NaN)
                    .takeIf { !it.isNaN() }
                val gp = if (c.isNull("gradePointScore")) null
                else c.optDouble("gradePointScore", Double.NaN).takeIf { !it.isNaN() }
                courses.add(
                    Course(
                        name = name,
                        credit = if (credit.isNaN()) 0.0 else credit,
                        score = score,
                        gpa = gp,
                        attr = c.optString("courseAttributeName").ifBlank { "—" }
                    )
                )
            }
            if (courses.isEmpty()) continue
            val rawName = s.optString("cjlx").ifBlank { s.optString("zxjxjhh") }
            val totalCredit = courses.sumOf { it.credit }
            val gpCredit = courses.filter { it.gpa != null }.sumOf { it.credit }
            val gpa = if (gpCredit > 0) courses.filter { it.gpa != null }
                .sumOf { it.gpa!! * it.credit } / gpCredit else 0.0
            out.add(
                Semester(
                    name = rawName.replace("(两学期)", "").replace("（两学期）", "").trim(),
                    courses = courses,
                    credit = totalCredit,
                    gpa = gpa,
                    order = semesterOrder(rawName)
                )
            )
        }
        return out.sortedBy { it.order }
    }

    /** 学年学期排序键：2025-2026学年春 = 20252，秋 = 20251 */
    private fun semesterOrder(name: String): Int {
        val m = Regex("""(\d{4})-(\d{4})学年\s*(.)""").find(name) ?: return 0
        val year = m.groupValues[1].toIntOrNull() ?: 0
        val season = when (m.groupValues[3]) {
            "秋" -> 1
            "春" -> 2
            "夏" -> 3
            else -> 0
        }
        return year * 10 + season
    }

    data class Plan(
        val major: String,
        val totalCredit: Double,
        val totalCourses: Int,
        val modules: List<Module>
    )

    /** 培养方案完成情况页：解析 zNodes 模块树 */
    fun parsePlan(html: String): Plan? {
        val major = Regex("""([\u4e00-\u9fa5]{2,24}专业)培养方案""").find(html)
            ?.groupValues?.get(1) ?: ""
        val totalCredit = Regex("""([\d.]+)</span>\s*<div class="infobox-content">\s*总学分""")
            .find(html)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val totalCourses = Regex("""([\d.]+)</span>\s*<div class="infobox-content">\s*总门数""")
            .find(html)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0

        val m = Regex("""var\s+zNodes\s*=\s*(\[[\s\S]*?\])\s*;""").find(html) ?: return null
        val arr = try {
            JSONArray(m.groupValues[1])
        } catch (e: Exception) {
            return null
        }

        val modRegex = Regex(
            """最低修读学分:([\d.]+),通过学分:([\d.]+),已修课程门数:(\d+),已及格课程门数:(\d+),未及格课程门数:(\d+),必修课未修读:(\d+)"""
        )

        data class Meta(
            val id: String, val name: String, val required: Double, val passed: Double,
            val total: Int, val passedCount: Int, val failed: Int, val missing: Int,
            val done: Boolean, val courses: MutableList<PlanCourse>
        )

        val order = ArrayList<String>()
        val map = HashMap<String, Meta>()

        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            if (n.optString("flagType") != "002") continue
            val text = clean(n.optString("name"))
            val g = modRegex.find(text)
            val label = text.substringBefore("(").replace("&nbsp;", "").trim()
            if (label.isEmpty()) continue
            val id = n.optString("id")
            val required = g?.groupValues?.get(1)?.toDoubleOrNull()
                ?: n.optString("zsxf").toDoubleOrNull() ?: 0.0
            val passed = g?.groupValues?.get(2)?.toDoubleOrNull()
                ?: n.optString("yxxf").toDoubleOrNull() ?: 0.0
            map[id] = Meta(
                id = id,
                name = label,
                required = required,
                passed = passed,
                total = g?.groupValues?.get(3)?.toIntOrNull() ?: 0,
                passedCount = g?.groupValues?.get(4)?.toIntOrNull() ?: 0,
                failed = g?.groupValues?.get(5)?.toIntOrNull() ?: 0,
                missing = g?.groupValues?.get(6)?.toIntOrNull() ?: 0,
                done = n.optString("sfwc") == "是",
                courses = ArrayList()
            )
            order.add(id)
        }

        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            if (n.optString("flagType") != "kch") continue
            val parent = n.optString("pId")
            val meta = map[parent] ?: continue
            meta.courses.add(parsePlanCourse(n.optString("name")))
        }

        val modules = order.mapNotNull { map[it] }.map {
            Module(
                name = it.name,
                required = it.required,
                passedCredit = it.passed,
                totalCourses = it.total,
                passedCourses = it.passedCount,
                failedCourses = it.failed,
                missingRequired = it.missing,
                done = it.done,
                courses = it.courses.sortedWith(
                    compareByDescending<PlanCourse> { it.passed }.thenBy { it.term }
                )
            )
        }
        return Plan(major, totalCredit, totalCourses, modules)
    }

    // ---------------- 课表 ----------------

    /** 从课表页解析当前周次与学期标签，如 "2026-2027 秋"、第 13 周 */
    fun parseScheduleMeta(html: String): Pair<Int, String> {
        val week = Regex("""id="soliderboxval"[^>]*value="\s*(\d+)""").find(html)?.groupValues?.get(1)
            ?.toIntOrNull()
            ?: Regex("""end:\s*\d+,\s*step:\s*\d+,\s*value:\s*(\d+)""")
                .find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0
        val term = Regex("""(\d{4}-\d{4})\s*([春秋夏])""").find(html)
            ?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: ""
        return week to term
    }

    /**
     * 课表接口 /ajaxStudentSchedule/weekLySchedule/callback/{week} 返回：
     * { allUnits, xkxx:[{ "课程号_课序号": {...} }], dateList:[...], experimentList:[] }
     */
    fun parseSchedule(json: String, week: Int, termLabel: String): Schedule {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return Schedule(week, 24, termLabel, emptyList())
        }
        val groups = root.optJSONArray("xkxx")
        val courses = ArrayList<ScheduleCourse>()
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val g = groups.optJSONObject(i) ?: continue
                val keys = g.keys()
                while (keys.hasNext()) {
                    val c = g.optJSONObject(keys.next()) ?: continue
                    val name = stripMarks(c.optString("courseName"))
                    if (name.isEmpty()) continue
                    val idObj = c.optJSONObject("id")
                    val code = idObj?.optString("coureNumber")?.trim().orEmpty()
                    val seq = idObj?.optString("coureSequenceNumber")?.trim().orEmpty()

                    val slots = ArrayList<ScheduleSlot>()
                    val tps = c.optJSONArray("timeAndPlaceList")
                    if (tps != null) {
                        for (j in 0 until tps.length()) {
                            val t = tps.optJSONObject(j) ?: continue
                            val day = t.optString("classDay").toIntOrNull() ?: 0
                            val start = t.optString("classSessions").toIntOrNull() ?: 0
                            if (day !in 1..7 || start <= 0) continue
                            val span = t.optString("continuingSession").toIntOrNull()?.coerceAtLeast(1) ?: 1
                            val place = listOf(
                                t.optString("campusName"),
                                t.optString("teachingBuildingName"),
                                t.optString("classroomName")
                            ).joinToString("") { it.trim() }.trim()
                            slots.add(
                                ScheduleSlot(
                                    day = day,
                                    start = start,
                                    span = span,
                                    weeks = t.optString("weekDescription").trim(),
                                    place = place
                                )
                            )
                        }
                    }

                    courses.add(
                        ScheduleCourse(
                            code = if (seq.isNotBlank()) "$code-$seq" else code,
                            number = code,
                            name = name,
                            teacher = cleanTeachers(c.optString("attendClassTeacher")),
                            credit = c.optString("unit").toDoubleOrNull() ?: 0.0,
                            category = c.optString("courseCategoryName").ifBlank {
                                c.optString("coursePropertiesName")
                            }.ifBlank { "—" },
                            examType = c.optString("examTypeName").ifBlank { "—" },
                            slots = slots
                        )
                    )
                }
            }
        }
        return Schedule(
            week = week,
            totalWeeks = 24,
            termLabel = termLabel,
            courses = courses.sortedWith(
                compareByDescending<ScheduleCourse> { it.scheduled }
                    .thenBy { it.slots.minOfOrNull { s -> s.day } ?: 9 }
            )
        )
    }

    /** 教师串形如 "郭海丽* " 或 "田煜衡* 崔浩* "，星号是主/辅讲标记 */
    private fun cleanTeachers(raw: String): String =
        raw.split("*", "/", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

    // ---------------- 考试 ----------------

    /**
     * 考试安排走 FullCalendar，GET /examPlan/detail 返回事件数组。
     * 学校这套系统没排考时只返回一个孤立的 "]"（不是合法 JSON），必须容错。
     */
    fun parseExams(text: String): List<ExamEvent> {
        val s = text.trim()
        if (!s.startsWith("[")) return emptyList()
        val arr = try {
            JSONArray(s)
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<ExamEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = firstOf(o, "title", "courseName", "examName", "kcmc")
            if (title.isEmpty()) continue
            val time = formatExamTime(firstOf(o, "start", "examTime", "startTime", "kssj"))
            val place = firstOf(o, "className", "place", "examRoom", "cdmc", "location")
            val seat = firstOf(o, "seatNumber", "seat", "zwh", "seatNo")
            val type = firstOf(o, "examTypeName", "type", "examType")
            out.add(ExamEvent(title, time, place, seat, type))
        }
        return out
    }

    private fun firstOf(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k)
            if (v.isNotBlank() && v != "null") return clean(v)
        }
        return ""
    }

    /** 兼容 "2026-11-30T14:00:00"、"2026-11-30 14:00"、"2026-11-30" 三种写法 */
    private fun formatExamTime(raw: String): String {
        if (raw.isBlank()) return ""
        val m = Regex("""\d{4}-(\d{2})-(\d{2})[T ]?(\d{2}:\d{2})?""").find(raw) ?: return raw
        val mo = m.groupValues[1]
        val d = m.groupValues[2]
        val hm = m.groupValues[3]
        val date = "$mo-$d"
        return if (hm.isNotBlank()) "$date $hm" else date
    }

    private fun parsePlanCourse(raw: String): PlanCourse {
        val text = clean(raw)
        val head = Regex("""^\[(.+?)\](.+?)\[([\d.]+)学分(?:,\s*([^\]]*))?\]""").find(text)
        val code = head?.groupValues?.get(1)?.let { stripMarks(it) } ?: ""
        val name = head?.groupValues?.get(2)?.let { stripMarks(it) } ?: stripMarks(text)
        val credit = head?.groupValues?.get(3)?.toDoubleOrNull() ?: 0.0
        val term = head?.groupValues?.get(4)?.trim() ?: ""
        val rest = if (head != null) text.substring(head.range.last + 1) else ""
        val attr = Regex("""\(\s*([^,()]+)\s*,""").find(rest)?.groupValues?.get(1)?.trim() ?: ""
        val score = Regex("""\(\s*[^,()]+\s*,\s*(-?[\d.]+)""").find(rest)
            ?.groupValues?.get(1)?.toDoubleOrNull()
        val passed = if (score != null) score >= 60 else raw.contains("fa-smile")
        return PlanCourse(code, name, credit, term, attr, score, passed)
    }
}

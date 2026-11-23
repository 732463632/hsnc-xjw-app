package cn.edu.hsnc.xjw.data

import java.util.Calendar

/**
 * 衡水学院作息时间（学校官网《衡水学院作息时间表》）。
 * 课表接口返回的 classSessions 是「小节」号（1–13），与此表的序号一一对应。
 *
 * 5 月 1 日至 8 月底执行夏季作息（只有下午、晚上不同），其余时间执行春秋季作息。
 * 教务系统本身没有配置节次时间（/ajax/student/getSectionAndTime 不返回 sectionTime），
 * 所以这份时间表是内置的，不来自接口。
 */
object SectionTime {

    /** 春秋季（秋季开学 ~ 次年 4 月 30 日） */
    private val AUTUMN = listOf(
        "08:00" to "08:45",
        "08:45" to "09:30",
        "09:50" to "10:35",
        "10:35" to "11:20",
        "11:25" to "12:10",
        "14:00" to "14:45",
        "14:45" to "15:30",
        "15:50" to "16:35",
        "16:35" to "17:20",
        "17:25" to "18:10",
        "19:00" to "19:45",
        "19:45" to "20:30",
        "20:35" to "21:20"
    )

    /** 夏季（5 月 1 日 ~ 8 月底）：上午不变，下午与晚上整体后移半小时 */
    private val SUMMER = listOf(
        "08:00" to "08:45",
        "08:45" to "09:30",
        "09:50" to "10:35",
        "10:35" to "11:20",
        "11:25" to "12:10",
        "14:30" to "15:15",
        "15:15" to "16:00",
        "16:20" to "17:05",
        "17:05" to "17:50",
        "17:55" to "18:40",
        "19:30" to "20:15",
        "20:15" to "21:00",
        "21:05" to "21:50"
    )

    const val MAX_SECTION = 13

    fun isSummer(): Boolean {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return month in 5..8
    }

    fun seasonLabel(): String = if (isSummer()) "夏季作息" else "春秋季作息"

    /**
     * 起始节 + 连续节数 → "14:00-15:30"
     * 跨小节时取最后一节的结束时间。
     */
    fun range(start: Int, span: Int): String {
        if (start !in 1..MAX_SECTION) return ""
        val table = if (isSummer()) SUMMER else AUTUMN
        val end = (start + span - 1).coerceIn(start, MAX_SECTION)
        val from = table[start - 1].first
        val to = table[end - 1].second
        return "$from-$to"
    }

    /** 单节的起始时间，用于按时间排序 */
    fun startOf(section: Int): String {
        if (section !in 1..MAX_SECTION) return ""
        return (if (isSummer()) SUMMER else AUTUMN)[section - 1].first
    }
}

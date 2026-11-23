package cn.edu.hsnc.xjw.data

data class Course(
    val name: String,
    val credit: Double,
    val score: Double?,
    val gpa: Double?,
    val attr: String
) {
    val level: String
        get() = when {
            score == null -> "—"
            score >= 90 -> "优秀"
            score >= 80 -> "良好"
            score >= 70 -> "中等"
            score >= 60 -> "及格"
            else -> "不及格"
        }
}

data class Semester(
    val name: String,
    val courses: List<Course>,
    val credit: Double,
    val gpa: Double,
    val order: Int = 0
)

data class PlanCourse(
    val code: String,
    val name: String,
    val credit: Double,
    val term: String,
    val attr: String,
    val score: Double?,
    val passed: Boolean
)

data class Module(
    val name: String,
    val required: Double,
    val passedCredit: Double,
    val totalCourses: Int,
    val passedCourses: Int,
    val failedCourses: Int,
    val missingRequired: Int,
    val done: Boolean,
    val courses: List<PlanCourse>
) {
    val progress: Float
        get() = if (required <= 0.0) {
            if (done) 1f else 0f
        } else (passedCredit / required).toFloat().coerceIn(0f, 1f)
}

/** 一条排课时段：星期几、起始节、连续节数、周次说明、地点 */
data class ScheduleSlot(
    val day: Int,
    val start: Int,
    val span: Int,
    val weeks: String,
    val place: String
) {
    val end: Int get() = start + span - 1
    val sessionText: String get() = if (span <= 1) "第${start}节" else "第${start}-${end}节"

    /** 按学校作息换算出的上课时间，如 "14:00-15:30" */
    val timeText: String get() = SectionTime.range(start, span)
}

data class ScheduleCourse(
    val code: String,
    /** 纯课程号（不含课序号），用于与培养方案对齐 */
    val number: String,
    val name: String,
    val teacher: String,
    val credit: Double,
    val category: String,
    val examType: String,
    val slots: List<ScheduleSlot>
) {
    val scheduled: Boolean get() = slots.isNotEmpty()
    val dayGroups: List<Pair<Int, String>> get() = slots
        .sortedWith(compareBy({ it.day }, { it.start }))
        .map { it.day to "${it.sessionText} ${it.place}".trim() }
}

data class Schedule(
    val week: Int,
    val totalWeeks: Int,
    val termLabel: String,
    val courses: List<ScheduleCourse>
) {
    val scheduledCourses: List<ScheduleCourse> get() = courses.filter { it.scheduled }
    val unscheduledCourses: List<ScheduleCourse> get() = courses.filter { !it.scheduled }
    val totalCredit: Double get() = courses.sumOf { it.credit }
}

data class ExamEvent(
    val title: String,
    val time: String,
    val place: String,
    val seat: String,
    val type: String
)

/** 成绩快照比对结果 */
data class GradeChange(
    val newCount: Int,
    val updatedCount: Int,
    val items: List<String>
) {
    val hasChange: Boolean get() = newCount + updatedCount > 0
    val summary: String
        get() = when {
            !hasChange -> ""
            newCount > 0 && updatedCount > 0 -> "新增 $newCount 门成绩，$updatedCount 门更新"
            newCount > 0 -> "新增 $newCount 门成绩"
            else -> "$updatedCount 门成绩有更新"
        }
}

data class AppData(
    val name: String,
    val studentId: String,
    val major: String,
    val gpa: Double,
    val earnedCredit: Double,
    val requiredCredit: Double,
    val planTotalCourses: Int,
    val semesters: List<Semester>,
    val modules: List<Module>,
    val schedule: Schedule? = null,
    val exams: List<ExamEvent> = emptyList(),
    val gradeChange: GradeChange = GradeChange(0, 0, emptyList()),
    val fetchedAt: Long = System.currentTimeMillis()
) {
    /** 本学期已选课程的课程号（不含课序号），用于标记培养方案里"进行中"的课 */
    val currentTermCourseNumbers: Set<String>
        get() = schedule?.courses?.map { it.number }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    val planPassedCredit: Double get() = modules.sumOf { it.passedCredit }

    val progress: Float
        get() = if (requiredCredit <= 0.0) 0f
        else (earnedCredit / requiredCredit).toFloat().coerceIn(0f, 1f)

    val planCourses: Int get() = modules.sumOf { it.courses.size }

    val planPassedCourses: Int get() = modules.sumOf { m -> m.courses.count { it.passed } }

    val activeModules: List<Module> get() = modules.filter { it.required > 0.0 }

    val allCourses: List<Course> get() = semesters.flatMap { it.courses }

    val bestCourse: Course?
        get() = allCourses.filter { it.score != null && it.credit > 0 }.maxByOrNull { it.score!! }
}

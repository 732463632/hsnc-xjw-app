package cn.edu.hsnc.xjw.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.edu.hsnc.xjw.MainActivity
import cn.edu.hsnc.xjw.R

/**
 * 成绩变动监测：把每次拉到的「课程-学期 → 分数」存成快照，
 * 下次刷新时比对，出现新课程或分数变化即视为有新成绩。
 * 首次运行只建立基线，不误报。
 */
class GradeWatcher(private val context: Context) {

    companion object {
        private const val PREFS = "urp_grade_snapshot"
        private const val CHANNEL = "grade_update"
        private const val CHANNEL_NAME = "成绩更新"
        private const val NOTIFY_ID = 1001

        /** 解析规则变化时改这个值，会重建基线，避免把老数据误判成新成绩 */
        private const val SNAPSHOT_VERSION = "2"
        private const val KEY_VERSION = "__ver"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun check(semesters: List<Semester>): GradeChange {
        val current = LinkedHashMap<String, String>()
        semesters.forEach { s ->
            s.courses.forEach { c ->
                val sc = c.score ?: return@forEach
                val key = "${s.name}|${c.name}"
                current[key] = fmt(sc)
            }
        }
        if (current.isEmpty()) return GradeChange(0, 0, emptyList())

        val old = prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith("__")) null else (v as? String)?.let { k to it }
        }.toMap()

        // 首次运行、或解析规则升级：只建立基线
        if (old.isEmpty() || prefs.getString(KEY_VERSION, null) != SNAPSHOT_VERSION) {
            save(current)
            return GradeChange(0, 0, emptyList())
        }

        val added = current.filterKeys { it !in old }
        val changed = current.filter { (k, v) -> old[k] != null && old[k] != v }

        save(current)

        if (added.isEmpty() && changed.isEmpty()) return GradeChange(0, 0, emptyList())

        val items = (added.keys + changed.keys).map { it.substringAfter("|") }.distinct()
        val change = GradeChange(added.size, changed.size, items)
        notifyIfPossible(change)
        return change
    }

    private fun save(map: Map<String, String>) {
        prefs.edit().clear().apply()
        prefs.edit().apply {
            map.forEach { (k, v) -> putString(k, v) }
            putString(KEY_VERSION, SNAPSHOT_VERSION)
        }.apply()
    }

    private fun notifyIfPossible(change: GradeChange) {
        if (!canNotify()) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "有新的课程成绩时提醒"
            mgr.createNotificationChannel(ch)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 2)
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = change.items.take(3).joinToString("、")
            .let { if (change.items.size > 3) "$it 等 ${change.items.size} 门" else it }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(change.summary)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            mgr.notify(NOTIFY_ID, n)
        } catch (e: SecurityException) {
            // 用户拒绝了通知权限，静默跳过
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
}

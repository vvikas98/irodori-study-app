package com.irodoristudy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LessonBookmark(
    val title: String,
    val url: String,
    val savedAt: String
)

class StudyStorage(context: Context) {

    private val prefs = context.getSharedPreferences("irodori_study_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_NOTES = "study_notes"
        private const val KEY_BOOKMARKS = "lesson_bookmarks"
        private const val KEY_KEEP_AWAKE = "keep_awake"
        private const val KEY_STREAK = "study_streak"
        private const val KEY_LAST_STUDY_DAY = "last_study_day"
        private const val KEY_TODAY_MINUTES = "today_minutes"
        private const val KEY_TODAY_DATE = "today_date"
        const val HOME_URL = "https://www.irodori-online.jpf.go.jp/"
    }

    fun getLastUrl(): String = prefs.getString(KEY_LAST_URL, HOME_URL) ?: HOME_URL

    fun saveLastUrl(url: String) {
        if (url.startsWith("https://www.irodori-online.jpf.go.jp")) {
            prefs.edit().putString(KEY_LAST_URL, url).apply()
        }
    }

    fun getNotes(): String = prefs.getString(KEY_NOTES, "") ?: ""

    fun saveNotes(text: String) {
        prefs.edit().putString(KEY_NOTES, text).apply()
    }

    fun isKeepAwakeEnabled(): Boolean = prefs.getBoolean(KEY_KEEP_AWAKE, true)

    fun setKeepAwakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply()
    }

    fun getBookmarks(): List<LessonBookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        val list = mutableListOf<LessonBookmark>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                LessonBookmark(
                    title = obj.optString("title", "Lesson"),
                    url = obj.optString("url", HOME_URL),
                    savedAt = obj.optString("savedAt", "")
                )
            )
        }
        return list
    }

    fun addBookmark(title: String, url: String) {
        val current = getBookmarks().filterNot { it.url == url }.toMutableList()
        val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
        current.add(0, LessonBookmark(title.ifBlank { "Irodori Lesson" }, url, stamp))
        saveBookmarks(current)
    }

    fun removeBookmark(url: String) {
        val updated = getBookmarks().filterNot { it.url == url }
        saveBookmarks(updated)
    }

    private fun saveBookmarks(list: List<LessonBookmark>) {
        val array = JSONArray()
        list.forEach { b ->
            val obj = JSONObject()
            obj.put("title", b.title)
            obj.put("url", b.url)
            obj.put("savedAt", b.savedAt)
            array.put(obj)
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    fun recordStudyMinute() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDate = prefs.getString(KEY_TODAY_DATE, "")
        val todayMinutes = if (savedDate == today) prefs.getInt(KEY_TODAY_MINUTES, 0) + 1 else 1

        val lastDay = prefs.getString(KEY_LAST_STUDY_DAY, "")
        var streak = prefs.getInt(KEY_STREAK, 0)
        if (lastDay != today) {
            streak = if (isYesterday(lastDay)) streak + 1 else 1
        }

        prefs.edit()
            .putString(KEY_TODAY_DATE, today)
            .putInt(KEY_TODAY_MINUTES, todayMinutes)
            .putString(KEY_LAST_STUDY_DAY, today)
            .putInt(KEY_STREAK, streak)
            .apply()
    }

    fun getTodayMinutes(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return if (prefs.getString(KEY_TODAY_DATE, "") == today) {
            prefs.getInt(KEY_TODAY_MINUTES, 0)
        } else {
            0
        }
    }

    fun getStreakDays(): Int = prefs.getInt(KEY_STREAK, 0)

    private fun isYesterday(dateStr: String?): Boolean {
        if (dateStr.isNullOrBlank()) return false
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val prev = fmt.parse(dateStr) ?: return false
            val diffDays = (Date().time - prev.time) / (1000 * 60 * 60 * 24)
            diffDays == 1L
        } catch (_: Exception) {
            false
        }
    }
}

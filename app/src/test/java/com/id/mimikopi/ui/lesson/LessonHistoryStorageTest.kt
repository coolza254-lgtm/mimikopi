package com.id.mimikopi.ui.lesson

import android.app.Application
import androidx.room.Room
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.AttemptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LessonHistoryStorageTest {
    @Test fun storedHistoryReopensAndExactLessonPrefixDoesNotLeakOtherLessons() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "lesson-history-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val db = open()
        try {
            val dao = db.learningDao()
            suspend fun save(exercise: String, at: Long) = dao.insertAttempt(AttemptEntity(sessionId = null,
                exerciseId = exercise, paramsJson = "{}", startedAtMillis = at, hand = "RIGHT", tempoBpm = null,
                noteAccuracy = .8, timingWithinTol = null, stumbles = null, clean = null))
            save("1.5:E_NOTEID:0", 1000)
            save("1.50:E_NOTEID:0", 2000)
            save("1.5:E_FINGER_PATTERN:1", 3000)
            val rows = withTimeout(10_000) { dao.lessonAttempts("1.5").first() }
            assertEquals(listOf(3000L, 1000L), rows.map { it.startedAtMillis })
        } finally { db.close() }
        val reopened = open()
        try {
            val rows = withTimeout(10_000) { reopened.learningDao().lessonAttempts("1.5").first() }
            assertEquals(2, rows.size)
            assertEquals(listOf(1, 0), rows.map { LessonAttempt(it).drillIndex })
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}

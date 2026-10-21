package com.id.mimikopi.desktop.data

import com.id.mimikopi.core.backup.AttemptEventRow
import com.id.mimikopi.core.backup.AttemptRow
import com.id.mimikopi.core.backup.BackupData
import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.backup.SessionRow
import com.id.mimikopi.core.backup.SettingRow
import com.id.mimikopi.core.sync.HistoryMerge
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * The program's data on this computer: one SQLite file with exactly the tables (and columns) of the Android app's
 * Room database v1, so a phone backup is read row for row and a computer backup restores on the phone.
 *
 * SQLite in WAL mode with full sync: a write returns only when it is on disk, and closing the program in the middle
 * of a save leaves either the old or the new data, never a mix. One connection, used under a lock.
 */
class Database private constructor(private val conn: Connection, val file: File?) : AutoCloseable {
    private val lock = Any()

    companion object {
        /** The Room schema version these tables match. */
        const val VERSION = 1

        fun open(file: File): Database {
            file.parentFile?.mkdirs()
            return create(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"), file)
        }

        /** Tests: a database that lives in memory only. */
        fun inMemory(): Database = create(DriverManager.getConnection("jdbc:sqlite::memory:"), null)

        private fun create(conn: Connection, file: File?): Database {
            conn.createStatement().use { s ->
                if (file != null) {
                    s.execute("PRAGMA journal_mode=WAL")
                    s.execute("PRAGMA synchronous=FULL")
                }
                s.execute("PRAGMA foreign_keys=ON")
                val version = s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
                require(version <= VERSION) { "ไฟล์ข้อมูลมาจากโปรแกรมรุ่นใหม่กว่า (v$version)" }
                SCHEMA.forEach(s::execute)
                s.execute("PRAGMA user_version=$VERSION")
            }
            return Database(conn, file)
        }

        // Room's createSql for schema v1 (app/schemas/.../1.json), table names filled in.
        private val SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `practice_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER, `planned_minutes` INTEGER NOT NULL, `active_seconds` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER, `exercise_id` TEXT NOT NULL, `params_json` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `hand` TEXT, `tempo_bpm` REAL, `note_accuracy` REAL, `timing_within_tol` REAL, `stumbles` INTEGER, `clean` INTEGER, `helper_uses` INTEGER NOT NULL, FOREIGN KEY(`session_id`) REFERENCES `practice_sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_attempts_session_id` ON `attempts` (`session_id`)",
            "CREATE INDEX IF NOT EXISTS `index_attempts_exercise_id` ON `attempts` (`exercise_id`)",
            "CREATE TABLE IF NOT EXISTS `attempt_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `attempt_id` INTEGER NOT NULL, `expected_pitch` INTEGER, `actual_pitch` INTEGER, `expected_onset_ms` INTEGER, `actual_onset_ms` INTEGER, `offset_ms` INTEGER, `velocity` INTEGER, FOREIGN KEY(`attempt_id`) REFERENCES `attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_attempt_events_attempt_id` ON `attempt_events` (`attempt_id`)",
            "CREATE TABLE IF NOT EXISTS `item_stats` (`item_id` TEXT NOT NULL, `box` INTEGER NOT NULL, `due_at` INTEGER NOT NULL, `last_results` TEXT NOT NULL, `median_response_ms` INTEGER, PRIMARY KEY(`item_id`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }

    // ---------------- settings ----------------

    fun get(key: String): String? = synchronized(lock) {
        query("SELECT value FROM settings WHERE `key` = ?", key) { it.getString(1) }.firstOrNull()
    }

    fun put(key: String, value: String) = synchronized(lock) {
        update("INSERT OR REPLACE INTO settings(`key`, value) VALUES (?, ?)", key, value)
    }

    fun delete(key: String) = synchronized(lock) { update("DELETE FROM settings WHERE `key` = ?", key) }

    fun withPrefix(prefix: String): List<SettingRow> = synchronized(lock) {
        query("SELECT `key`, value FROM settings WHERE substr(`key`, 1, ?) = ? ORDER BY `key`", prefix.length, prefix) {
            SettingRow(it.getString(1), it.getString(2))
        }
    }

    fun settings(): Map<String, String> = synchronized(lock) {
        query("SELECT `key`, value FROM settings") { it.getString(1) to it.getString(2) }.toMap()
    }

    /** Writes several rows in one transaction. */
    fun putAll(rows: Map<String, String>) = transaction {
        rows.forEach { (k, v) -> update("INSERT OR REPLACE INTO settings(`key`, value) VALUES (?, ?)", k, v) }
    }

    // ---------------- spaced repetition ----------------

    fun itemStats(): List<ItemStatRow> = synchronized(lock) {
        query("SELECT item_id, box, due_at, last_results, median_response_ms FROM item_stats ORDER BY item_id") { itemStat(it) }
    }

    fun putItemStat(row: ItemStatRow) = synchronized(lock) { insertItemStat(row) }

    fun putItemStats(rows: List<ItemStatRow>) = transaction { rows.forEach(::insertItemStat) }

    private fun insertItemStat(row: ItemStatRow) = update(
        "INSERT OR REPLACE INTO item_stats(item_id, box, due_at, last_results, median_response_ms) VALUES (?, ?, ?, ?, ?)",
        row.itemId, row.box, row.dueAt, row.lastResults, row.medianResponseMs,
    )

    // ---------------- attempts and sessions ----------------

    /** Saves an attempt and its events in one transaction; returns the attempt's id. */
    fun insertAttempt(row: AttemptRow, events: List<AttemptEventRow>): Long = transaction {
        val id = insertAttemptRow(row)
        events.forEach { insertEventRow(it.copy(attemptId = id)) }
        id
    }

    private fun insertAttemptRow(r: AttemptRow): Long = insert(
        "INSERT INTO attempts(session_id, exercise_id, params_json, started_at, hand, tempo_bpm, note_accuracy, timing_within_tol, stumbles, clean, helper_uses) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        r.sessionId, r.exerciseId, r.paramsJson, r.startedAt, r.hand, r.tempoBpm, r.noteAccuracy, r.timingWithinTol, r.stumbles,
        r.clean?.let { if (it) 1 else 0 }, r.helperUses,
    )

    private fun insertEventRow(e: AttemptEventRow): Long = insert(
        "INSERT INTO attempt_events(attempt_id, expected_pitch, actual_pitch, expected_onset_ms, actual_onset_ms, offset_ms, velocity) VALUES (?, ?, ?, ?, ?, ?, ?)",
        e.attemptId, e.expectedPitch, e.actualPitch, e.expectedOnsetMs, e.actualOnsetMs, e.offsetMs, e.velocity,
    )

    /** Every attempt of a lesson (exercise ids "<lesson>:<type>:<drill>"), newest first, like Android's lessonAttempts. */
    fun lessonAttempts(lessonId: String): List<AttemptRow> = synchronized(lock) {
        query("$ATTEMPT_SELECT WHERE substr(exercise_id, 1, ?) = ? ORDER BY started_at DESC, id DESC", lessonId.length + 1, "$lessonId:") { attempt(it) }
    }

    fun attemptsSince(since: Long): List<AttemptRow> = synchronized(lock) {
        query("$ATTEMPT_SELECT WHERE started_at >= ? ORDER BY started_at", since) { attempt(it) }
    }

    fun attemptsWithPrefix(prefix: String): List<AttemptRow> = synchronized(lock) {
        query("$ATTEMPT_SELECT WHERE substr(exercise_id, 1, ?) = ? ORDER BY started_at DESC, id DESC", prefix.length, prefix) { attempt(it) }
    }

    fun events(attemptId: Long): List<AttemptEventRow> = synchronized(lock) {
        query("$EVENT_SELECT WHERE attempt_id = ? ORDER BY id", attemptId) { event(it) }
    }

    fun insertSession(startedAt: Long, plannedMinutes: Int): Long = synchronized(lock) {
        insert("INSERT INTO practice_sessions(started_at, ended_at, planned_minutes, active_seconds) VALUES (?, NULL, ?, 0)", startedAt, plannedMinutes)
    }

    fun finishSession(id: Long, endedAt: Long, activeSeconds: Long) = synchronized(lock) {
        update("UPDATE practice_sessions SET ended_at = ?, active_seconds = ? WHERE id = ?", endedAt, activeSeconds, id)
    }

    fun sessions(): List<SessionRow> = synchronized(lock) {
        query("SELECT id, started_at, ended_at, planned_minutes, active_seconds FROM practice_sessions ORDER BY started_at") { session(it) }
    }

    // ---------------- whole-database access (backups, restore, merge) ----------------

    /** Everything, as the Android backup's data block. */
    fun export(): BackupData = synchronized(lock) {
        BackupData(
            practiceSessions = query("SELECT id, started_at, ended_at, planned_minutes, active_seconds FROM practice_sessions ORDER BY id") { session(it) },
            attempts = query("$ATTEMPT_SELECT ORDER BY id") { attempt(it) },
            attemptEvents = query("$EVENT_SELECT ORDER BY id") { event(it) },
            itemStats = query("SELECT item_id, box, due_at, last_results, median_response_ms FROM item_stats ORDER BY item_id") { itemStat(it) },
            settings = query("SELECT `key`, value FROM settings ORDER BY `key`") { SettingRow(it.getString(1), it.getString(2)) },
        )
    }

    /** Replaces everything with [data] in one transaction, ids kept (a restore, like Android's BackupService). */
    fun replaceAll(data: BackupData) = transaction {
        listOf("attempt_events", "attempts", "practice_sessions", "item_stats", "settings").forEach { update("DELETE FROM $it") }
        data.practiceSessions.forEach {
            update("INSERT INTO practice_sessions(id, started_at, ended_at, planned_minutes, active_seconds) VALUES (?, ?, ?, ?, ?)",
                it.id, it.startedAt, it.endedAt, it.plannedMinutes, it.activeSeconds)
        }
        data.attempts.forEach { r ->
            update("INSERT INTO attempts(id, session_id, exercise_id, params_json, started_at, hand, tempo_bpm, note_accuracy, timing_within_tol, stumbles, clean, helper_uses) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id, r.sessionId, r.exerciseId, r.paramsJson, r.startedAt, r.hand, r.tempoBpm, r.noteAccuracy, r.timingWithinTol, r.stumbles,
                r.clean?.let { if (it) 1 else 0 }, r.helperUses)
        }
        data.attemptEvents.forEach { e ->
            update("INSERT INTO attempt_events(id, attempt_id, expected_pitch, actual_pitch, expected_onset_ms, actual_onset_ms, offset_ms, velocity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                e.id, e.attemptId, e.expectedPitch, e.actualPitch, e.expectedOnsetMs, e.actualOnsetMs, e.offsetMs, e.velocity)
        }
        data.itemStats.forEach(::insertItemStat)
        data.settings.forEach { update("INSERT INTO settings(`key`, value) VALUES (?, ?)", it.key, it.value) }
    }

    /**
     * Applies a [HistoryMerge.Plan] (another device's new sessions, attempts and events) with fresh local ids, in one
     * transaction. Returns the number of attempts added.
     */
    fun applyHistory(plan: HistoryMerge.Plan): Int = transaction {
        val sessionIds = HashMap(plan.sessionIds)
        for (s in plan.newSessions) {
            sessionIds[s.id] = insert("INSERT INTO practice_sessions(started_at, ended_at, planned_minutes, active_seconds) VALUES (?, ?, ?, ?)",
                s.startedAt, s.endedAt, s.plannedMinutes, s.activeSeconds)
        }
        for (s in plan.sessionUpdates) {
            update("UPDATE practice_sessions SET ended_at = ?, active_seconds = ? WHERE id = ?", s.endedAt, s.activeSeconds, s.id)
        }
        val byAttempt = plan.newEvents.groupBy { it.attemptId }
        for (a in plan.newAttempts) {
            val id = insertAttemptRow(a.copy(sessionId = a.sessionId?.let { sessionIds[it] }))
            byAttempt[a.id].orEmpty().forEach { insertEventRow(it.copy(attemptId = id)) }
        }
        plan.newAttempts.size
    }

    /** Runs [block] in one transaction: all of it is saved, or (on an exception) none of it. */
    fun <T> transaction(block: () -> T): T = synchronized(lock) {
        // Nested calls join the outer transaction, so a merge made of several writes stays one unit.
        if (depth > 0) {
            depth++
            try { return block() } finally { depth-- }
        }
        conn.autoCommit = false
        depth = 1
        try {
            val result = block()
            conn.commit()
            result
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            depth = 0
            conn.autoCommit = true
        }
    }

    private var depth = 0

    /** Copies the database to [target] as one consistent file (SQLite's online backup). */
    fun copyTo(target: File) = synchronized(lock) {
        target.parentFile?.mkdirs()
        target.delete()
        conn.createStatement().use { it.executeUpdate("VACUUM INTO '${target.absolutePath.replace("'", "''")}'") }
    }

    override fun close() = synchronized(lock) { conn.close() }

    // ---------------- JDBC helpers ----------------

    private val ATTEMPT_SELECT = "SELECT id, session_id, exercise_id, params_json, started_at, hand, tempo_bpm, note_accuracy, timing_within_tol, stumbles, clean, helper_uses FROM attempts"
    private val EVENT_SELECT = "SELECT id, attempt_id, expected_pitch, actual_pitch, expected_onset_ms, actual_onset_ms, offset_ms, velocity FROM attempt_events"

    private fun ResultSet.longOrNull(i: Int): Long? = getLong(i).let { if (wasNull()) null else it }
    private fun ResultSet.intOrNull(i: Int): Int? = getInt(i).let { if (wasNull()) null else it }
    private fun ResultSet.doubleOrNull(i: Int): Double? = getDouble(i).let { if (wasNull()) null else it }

    private fun session(r: ResultSet) = SessionRow(r.getLong(1), r.getLong(2), r.longOrNull(3), r.getInt(4), r.getLong(5))
    private fun attempt(r: ResultSet) = AttemptRow(
        id = r.getLong(1), sessionId = r.longOrNull(2), exerciseId = r.getString(3), paramsJson = r.getString(4), startedAt = r.getLong(5),
        hand = r.getString(6), tempoBpm = r.doubleOrNull(7), noteAccuracy = r.doubleOrNull(8), timingWithinTol = r.doubleOrNull(9),
        stumbles = r.intOrNull(10), clean = r.intOrNull(11)?.let { it != 0 }, helperUses = r.getInt(12),
    )
    private fun event(r: ResultSet) = AttemptEventRow(r.getLong(1), r.getLong(2), r.intOrNull(3), r.intOrNull(4), r.longOrNull(5), r.longOrNull(6), r.longOrNull(7), r.intOrNull(8))
    private fun itemStat(r: ResultSet) = ItemStatRow(r.getString(1), r.getInt(2), r.getLong(3), r.getString(4), r.longOrNull(5))

    private fun PreparedStatement.bind(args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> setNull(i + 1, Types.NULL)
                is String -> setString(i + 1, a)
                is Int -> setInt(i + 1, a)
                is Long -> setLong(i + 1, a)
                is Double -> setDouble(i + 1, a)
                is Boolean -> setInt(i + 1, if (a) 1 else 0)
                else -> error("unsupported ${a::class}")
            }
        }
    }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        conn.prepareStatement(sql).use { st ->
            st.bind(args)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }

    private fun update(sql: String, vararg args: Any?): Int = conn.prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeUpdate()
    }

    private fun insert(sql: String, vararg args: Any?): Long = conn.prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeUpdate()
        conn.createStatement().use { s -> s.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getLong(1) } }
    }
}

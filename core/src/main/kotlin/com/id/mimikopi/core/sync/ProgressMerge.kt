package com.id.mimikopi.core.sync

import com.id.mimikopi.core.backup.ItemStatRow
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.core.ear.ToneLevel
import com.id.mimikopi.core.ear.ToneRun
import com.id.mimikopi.core.ear.ToneStats
import com.id.mimikopi.core.exercise.ChordRun
import com.id.mimikopi.core.lesson.LessonRecord
import com.id.mimikopi.core.playalong.PieceProgress
import com.id.mimikopi.core.progress.BenchmarkResult
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.session.DayLog
import com.id.mimikopi.core.session.PracticeLog
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One device's progress as a sync exchanges it: the progress rows of the settings table (only the keys in
 * [ProgressMerge.KEYS]; latency, MIDI device, theme, sound and other device settings never travel) and the review
 * items. The same format goes over Wi-Fi and into a sync file.
 */
@Serializable
data class SyncPayload(
    val format: Int = FORMAT,
    /** Random id of the sending device (kept outside the settings, so a backup restore never copies it). */
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val settings: Map<String, String> = emptyMap(),
    val itemStats: List<ItemStatRow> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = 1
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null when the text is not a sync file of a format this app reads. */
        fun fromJson(text: String): SyncPayload? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.format <= FORMAT && it.deviceId.isNotBlank() }
    }
}

/** What applying a sync changes on this device: settings rows to write and review items to write. */
data class MergeResult(val settings: Map<String, String>, val itemStats: List<ItemStatRow>) {
    val changed: Boolean get() = settings.isNotEmpty() || itemStats.isNotEmpty()
}

/**
 * Merges another device's progress into this one's. Every rule is a union that never loses practice from either
 * device, gives the same result whichever side merges (ties are broken by content, not by side), and changes nothing
 * when the same data is merged again, so syncing twice, or A to B and B to A, is always safe.
 *
 * Practice minutes are the one count that would double up: each device keeps only its own log under [LOG] and copies
 * of the other devices' logs under [PEERS] (one per device id). A device's own log only grows, so two copies of it
 * merge by taking the larger value per day and kind.
 */
object ProgressMerge {
    const val LESSONS = "lesson.records"
    const val PIECES = "piece.progress"
    const val LOG = "practice.log"
    const val PEERS = "practice.log.peers"
    const val CHECKS = "skills.checks"
    const val EAR_STATS = "ear.tone.stats"
    const val EAR_LEVELS = "ear.tone.levels"
    /** Ids of the user's ear levels deleted on any device, so a sync does not bring them back. */
    const val EAR_DELETED = "ear.tone.deleted"
    const val CHORD_RUNS = "chords.runs"
    const val BENCHMARKS = "benchmarks"
    const val HELPER_COUNT = "helper.noteNames.count"
    const val EAR_COURSE = com.id.mimikopi.core.ear.EarProgress.KEY

    /** The settings rows that are progress and travel between devices; every other row stays where it is. */
    val KEYS = setOf(LESSONS, PIECES, LOG, PEERS, CHECKS, EAR_STATS, EAR_LEVELS, EAR_DELETED, CHORD_RUNS, BENCHMARKS, HELPER_COUNT, EAR_COURSE)

    private const val DAY_MS = 86_400_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val lessonsSer = MapSerializer(String.serializer(), LessonRecord.serializer())
    private val piecesSer = MapSerializer(String.serializer(), PieceProgress.serializer())
    private val peersSer = MapSerializer(String.serializer(), ListSerializer(DayLog.serializer()))
    private val checksSer = ListSerializer(CheckRecord.serializer())
    private val chordSer = ListSerializer(ChordRun.serializer())
    private val benchSer = ListSerializer(BenchmarkResult.serializer())
    private val deletedSer = SetSerializer(String.serializer())

    /** The progress rows of a settings table (only [KEYS]). */
    fun progressRows(all: Map<String, String>): Map<String, String> = all.filterKeys { it in KEYS }

    /**
     * Merges [remote] into [local] (both payloads, [local] of this device). [intervalDays] is this device's review
     * interval per box, used to tell which copy of a review item was reviewed last. Returns only what differs from
     * [local].
     */
    fun merge(local: SyncPayload, remote: SyncPayload, intervalDays: (Int) -> Int): MergeResult {
        val l = local.settings
        val r = remote.settings
        val out = LinkedHashMap<String, String>()
        fun put(key: String, value: String?) { if (value != null && value != l[key]) out[key] = value }

        put(LESSONS, mergeRows(l[LESSONS], r[LESSONS], lessonsSer, ::lessons))
        put(PIECES, mergeRows(l[PIECES], r[PIECES], piecesSer, ::pieces))
        put(CHECKS, mergeRows(l[CHECKS], r[CHECKS], checksSer) { a, b -> checks(a, b) })
        put(CHORD_RUNS, mergeRows(l[CHORD_RUNS], r[CHORD_RUNS], chordSer, ::chordRuns))
        put(BENCHMARKS, mergeRows(l[BENCHMARKS], r[BENCHMARKS], benchSer, ::benchmarks))
        put(EAR_COURSE, mergeRows(l[EAR_COURSE], r[EAR_COURSE], com.id.mimikopi.core.ear.EarState.serializer(), com.id.mimikopi.core.ear.EarProgress::merge))
        put(HELPER_COUNT, listOfNotNull(l[HELPER_COUNT]?.toIntOrNull(), r[HELPER_COUNT]?.toIntOrNull()).maxOrNull()?.toString())

        val deleted = mergeRows(l[EAR_DELETED], r[EAR_DELETED], deletedSer) { a, b -> a + b }
        put(EAR_DELETED, deleted)
        val gone = deleted?.let { parse(it, deletedSer) }.orEmpty()
        put(EAR_LEVELS, earLevels(l[EAR_LEVELS], r[EAR_LEVELS], gone))
        put(EAR_STATS, earStats(l[EAR_STATS], r[EAR_STATS], gone))

        put(PEERS, peers(local.deviceId, l[PEERS], remote.deviceId, r[LOG], r[PEERS]))

        val items = itemStats(local.itemStats, remote.itemStats, intervalDays)
        return MergeResult(out, items)
    }

    // ---- per-row rules (public for tests) ----

    /** Per step: every pass, helper, drill and tried day from both (pass days before a reopen dropped); attempts = max. */
    fun lessons(a: Map<String, LessonRecord>, b: Map<String, LessonRecord>): Map<String, LessonRecord> =
        (a.keys + b.keys).sorted().associateWith { id ->
            val x = a[id]
            val y = b[id]
            if (x == null || y == null) return@associateWith (x ?: y)!!
            val cutoff = listOfNotNull(x.reopenedOn, y.reopenedOn).maxOrNull()
            fun keep(days: Set<String>) = if (cutoff == null) days else days.filter { it >= cutoff }.toSet()
            val drills = (x.drillDays.keys + y.drillDays.keys).associateWith { i -> keep(x.drillDays[i].orEmpty() + y.drillDays[i].orEmpty()) }
                .filterValues { it.isNotEmpty() }
            LessonRecord(
                lessonId = id,
                passDays = keep(x.passDays + y.passDays),
                helperDays = x.helperDays + y.helperDays,
                attempts = maxOf(x.attempts, y.attempts),
                drillDays = drills,
                triedDays = x.triedDays + y.triedDays,
                reopenedOn = cutoff,
            )
        }

    /**
     * Per piece: pass days and each hand's pass days from both, runs and best clean tempo = max. Streaks, tempo ramps
     * and problem spots cannot be added up, so they come whole from the copy with more runs (equal: by content).
     */
    fun pieces(a: Map<String, PieceProgress>, b: Map<String, PieceProgress>): Map<String, PieceProgress> =
        (a.keys + b.keys).sorted().associateWith { id ->
            val x = a[id]
            val y = b[id]
            if (x == null || y == null) return@associateWith (x ?: y)!!
            val base = when {
                x.runs != y.runs -> if (x.runs > y.runs) x else y
                else -> if (json.encodeToString(PieceProgress.serializer(), x) >= json.encodeToString(PieceProgress.serializer(), y)) x else y
            }
            base.copy(
                runs = maxOf(x.runs, y.runs),
                passDays = (x.passDays + y.passDays).distinct().sorted(),
                handPassDays = (x.handPassDays.keys + y.handPassDays.keys).associateWith { h ->
                    (x.handPassDays[h].orEmpty() + y.handPassDays[h].orEmpty()).distinct().sorted()
                },
                bestCleanBpm = listOfNotNull(x.bestCleanBpm, y.bestCleanBpm).maxOrNull(),
            )
        }

    /**
     * Level tests: every record from both (the same record counted as often as the side that has it most), in date
     * order (then time of day where recorded), the last 600.
     */
    fun checks(a: List<CheckRecord>, b: List<CheckRecord>): List<CheckRecord> =
        multisetUnion(a, b).sortedWith(compareBy<CheckRecord>({ it.date }, { it.at ?: 0L })).takeLast(600)

    /** Chord-run history: both lists, newest first, the last 20. */
    fun chordRuns(a: List<ChordRun>, b: List<ChordRun>): List<ChordRun> =
        multisetUnion(a, b).sortedByDescending { it.day }.take(ChordRun.KEEP)

    /** Benchmarks: one sitting per date; on the same date each field from whichever side has it (both: the better). */
    fun benchmarks(a: List<BenchmarkResult>, b: List<BenchmarkResult>): List<BenchmarkResult> =
        (a + b).groupBy { it.date }.toSortedMap().map { (date, rs) ->
            rs.reduce { x, y ->
                val speed = listOfNotNull(x.takeIf { it.noteIdPerMinute != null }, y.takeIf { it.noteIdPerMinute != null })
                    .maxWithOrNull(compareBy<BenchmarkResult>({ it.noteIdPerMinute }, { it.noteIdAccuracy ?: 0.0 }))
                BenchmarkResult(
                    date = date,
                    noteIdPerMinute = speed?.noteIdPerMinute,
                    noteIdAccuracy = speed?.noteIdAccuracy ?: x.noteIdAccuracy ?: y.noteIdAccuracy,
                    sightReadingLevel = listOfNotNull(x.sightReadingLevel, y.sightReadingLevel).maxOrNull(),
                    pulseSdMs = listOfNotNull(x.pulseSdMs, y.pulseSdMs).minOrNull(),
                    earAccuracy = listOfNotNull(x.earAccuracy, y.earAccuracy).maxOrNull(),
                )
            }
        }

    /** The user's ear levels from both (same id on both: by content), without deleted ones. */
    fun earLevels(a: String?, b: String?, gone: Set<String>): String? {
        if (a == null && b == null) return null
        val x = FunctionalEar.parseLevels(a)
        val y = FunctionalEar.parseLevels(b)
        if (a != null && x.isEmpty() && a.trim() != "[]") return a // unreadable here: leave it
        val byId = LinkedHashMap<String, ToneLevel>()
        for (lv in x + y) {
            val old = byId[lv.id]
            byId[lv.id] = if (old == null) lv else maxOf(old, lv, compareBy { FunctionalEar.levelsJson(listOf(it)) })
        }
        return FunctionalEar.levelsJson(byId.values.filter { it.id !in gone }.sortedBy { it.id })
    }

    /** Ear runs per level: both lists, in day order, the last 20; deleted levels dropped. */
    fun earStats(a: String?, b: String?, gone: Set<String>): String? {
        if (a == null && b == null) return null
        val x = FunctionalEar.parseStats(a)
        val y = FunctionalEar.parseStats(b)
        if (a != null && x.isEmpty() && a.trim() != "{}") return a
        val ids = (x.keys + y.keys).filter { it !in gone }.sorted()
        return FunctionalEar.statsJson(ids.associateWith { id ->
            ToneStats(multisetUnion(x[id]?.runs.orEmpty(), y[id]?.runs.orEmpty()).sortedWith(compareBy<ToneRun>({ it.day }, { it.at ?: 0L })).takeLast(FunctionalEar.KEEP_RUNS))
        })
    }

    /**
     * The other devices' practice logs on this device: the remote's own log becomes (or extends) its copy here, and
     * the remote's copies of third devices merge with ours. This device's own log is never touched.
     */
    fun peers(localId: String, localPeers: String?, remoteId: String, remoteOwn: String?, remotePeers: String?): String? {
        val mine = localPeers?.let { parse(it, peersSer) } ?: emptyMap()
        val theirs = remotePeers?.let { parse(it, peersSer) } ?: emptyMap()
        val incoming = theirs.filterKeys { it != localId } + (remoteId to PracticeLog.parse(remoteOwn))
        if (remoteId == localId) return localPeers
        val ids = (mine.keys + incoming.keys).filter { it != localId }.sorted()
        if (ids.isEmpty()) return localPeers
        return json.encodeToString(peersSer, ids.associateWith { id -> maxLog(mine[id].orEmpty(), incoming[id].orEmpty()) })
    }

    /** Two copies of one device's log: per day and kind the larger (that log only ever grows). */
    fun maxLog(a: List<DayLog>, b: List<DayLog>): List<DayLog> =
        (a + b).groupBy { it.date }.toSortedMap().map { (date, days) ->
            DayLog(date, days.flatMap { it.seconds.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.max() }.toSortedMap())
        }.takeLast(PracticeLog.KEEP_DAYS)

    /** This device's log plus the other devices' copies: the minutes shown on Today and the progress page. */
    fun combinedLog(own: List<DayLog>, peers: Map<String, List<DayLog>>): List<DayLog> =
        (own + peers.values.flatten()).groupBy { it.date }.toSortedMap().map { (date, days) ->
            DayLog(date, days.flatMap { it.seconds.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() })
        }

    fun parsePeers(text: String?): Map<String, List<DayLog>> = text?.let { parse(it, peersSer) } ?: emptyMap()

    /** [EAR_DELETED] with [id] added (called when the user deletes one of their ear levels). */
    fun withDeleted(text: String?, id: String): String =
        json.encodeToString(deletedSer, (text?.let { parse(it, deletedSer) } ?: emptySet()) + id)

    /**
     * Review items: per item the copy reviewed last (its review time is its due time minus the box interval; an item
     * never reviewed loses), equal times broken by content. Returns the items to write here.
     */
    fun itemStats(local: List<ItemStatRow>, remote: List<ItemStatRow>, intervalDays: (Int) -> Int): List<ItemStatRow> {
        val mine = local.associateBy { it.itemId }
        fun reviewedAt(i: ItemStatRow): Long = if (i.lastResults.isEmpty()) Long.MIN_VALUE else i.dueAt - intervalDays(i.box) * DAY_MS
        val order = compareBy<ItemStatRow>({ reviewedAt(it) }, { it.lastResults.length }, { it.box }, { it.dueAt }, { it.lastResults }, { it.medianResponseMs ?: -1L })
        return remote.mapNotNull { r ->
            val l = mine[r.itemId]
            when {
                l == null -> r
                l == r -> null
                order.compare(r, l) > 0 -> r
                else -> null
            }
        }
    }

    // ---- helpers ----

    /** Each distinct element as many times as the side that has it most; [a]'s order first, then [b]'s extras. */
    fun <T> multisetUnion(a: List<T>, b: List<T>): List<T> {
        val left = a.groupingBy { it }.eachCount().toMutableMap()
        val extra = b.filter { e -> val n = left[e] ?: 0; if (n > 0) { left[e] = n - 1; false } else true }
        return a + extra
    }

    private fun <T> parse(text: String, ser: KSerializer<T>): T? = runCatching { json.decodeFromString(ser, text) }.getOrNull()

    /**
     * Merges one row: unreadable on this device keeps it as it is; missing here takes the other side's (when
     * readable); both readable merge by [rule].
     */
    private fun <T> mergeRows(a: String?, b: String?, ser: KSerializer<T>, rule: (T, T) -> T): String? {
        val x = a?.let { parse(it, ser) }
        val y = b?.let { parse(it, ser) }
        return when {
            a != null && x == null -> a
            x == null -> y?.let { json.encodeToString(ser, it) }
            y == null -> a
            else -> json.encodeToString(ser, rule(x, y))
        }
    }
}

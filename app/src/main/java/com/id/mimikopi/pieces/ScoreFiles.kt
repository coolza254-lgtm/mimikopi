package com.id.mimikopi.pieces

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.id.mimikopi.core.editor.Timeline
import com.id.mimikopi.core.interchange.ImportException
import com.id.mimikopi.core.interchange.MidiFile
import com.id.mimikopi.core.interchange.MusicXml
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score

/** Reading and writing score files through the system document picker (nothing leaves the device otherwise). */
class ScoreFiles(private val context: Context) {
    data class Imported(val piece: PieceFile, val skipped: Int)

    /** Reads MusicXML (.musicxml/.xml/.mxl) or MIDI (.mid) by content. Throws [ImportException] with a Thai message. */
    fun import(uri: Uri, id: String): Imported {
        val bytes = context.contentResolver.openInputStream(uri)?.use { s ->
            val data = s.readBytes()
            if (data.size > 20_000_000) throw ImportException("ไฟล์ใหญ่เกินไป")
            data
        } ?: throw ImportException("เปิดไฟล์ไม่ได้")
        val name = displayName(uri)
        val timeline: Timeline
        var skipped = 0
        if (bytes.size >= 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "MThd") {
            timeline = MidiFile.read(bytes, id)
        } else {
            timeline = MusicXml.read(bytes, id)
            skipped = MusicXml.lastWarnings
        }
        var piece = timeline.toPieceFile()
        if (piece.title.startsWith("เพลงที่นำเข้า") && name != null) piece = piece.copy(title = name.substringBeforeLast('.'))
        Score.from(piece) // must be readable by the app
        return Imported(piece.copy(source = "Imported by the user (personal use)"), skipped)
    }

    fun exportMusicXml(score: Score, uri: Uri) = write(uri) { it.write(MusicXml.write(score).toByteArray(Charsets.UTF_8)) }
    fun exportMidi(score: Score, uri: Uri) = write(uri) { it.write(MidiFile.write(score)) }
    fun exportPdf(score: Score, uri: Uri) = write(uri) { ScorePdf(context).write(score, it) }

    private fun write(uri: Uri, block: (java.io.OutputStream) -> Unit) {
        context.contentResolver.openOutputStream(uri, "wt")?.use(block) ?: error("cannot open $uri")
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        /** A file name for exports: the title without characters file systems dislike. */
        fun safeName(title: String) = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "score" }
    }
}

package com.id.mimikopi.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException

/**
 * Decodes a 16-bit stereo FLAC file to raw little-endian interleaved PCM using the platform
 * decoder (no bundled codec). Returns the number of frames written.
 */
object FlacDecoder {
    private const val TIMEOUT_US = 10_000L

    fun decode(input: File, output: File, expectedRate: Int): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(input.path)
            if (extractor.trackCount < 1) throw IOException("no audio track in ${input.name}")
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("no mime in ${input.name}")
            if (format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 2) throw IOException("${input.name} is not stereo")
            if (format.getInteger(MediaFormat.KEY_SAMPLE_RATE) != expectedRate) throw IOException("${input.name} has wrong sample rate")
            extractor.selectTrack(0)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var bytes = 0L
            var scratch = ByteArray(0)
            BufferedOutputStream(output.outputStream(), 1 shl 16).use { out ->
                while (true) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val buf = codec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            if (info.size > 0) {
                                val buf = codec.getOutputBuffer(outIndex)!!
                                buf.position(info.offset)
                                if (scratch.size < info.size) scratch = ByteArray(info.size)
                                buf.get(scratch, 0, info.size)
                                out.write(scratch, 0, info.size)
                                bytes += info.size
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            val encoding = if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            if (encoding != AudioFormat.ENCODING_PCM_16BIT) throw IOException("decoder output is not 16-bit PCM")
                        }
                    }
                }
            }
            if (bytes == 0L || bytes % 4 != 0L) throw IOException("bad decoded size for ${input.name}")
            return bytes / 4
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}

package com.example.recordingandtranscribe.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object AudioTrimmer {
    /**
     * Trims an audio file from startTimeUs to endTimeUs.
     * Note: This is a basic implementation using MediaMuxer.
     */
    fun trimAudio(inputFile: File, outputFile: File, startTimeUs: Long, endTimeUs: Long): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            val trackCount = extractor.trackCount
            if (trackCount <= 0) return false

            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            
            var muxerStarted = false
            val outputFormat = if (outputFile.name.endsWith(".ogg", ignoreCase = true)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }
            muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
            val trackIndex = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val bufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var sampleCount = 0
            val maxSamples = 10_000_000 // Increased safety limit to avoid cutting off long recordings (>30 mins)
            while (sampleCount < maxSamples) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                val sampleTime = extractor.sampleTime
                if (sampleTime > endTimeUs) {
                    break
                }
                bufferInfo.presentationTimeUs = sampleTime - startTimeUs
                if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
                
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                extractor.advance()
                sampleCount++
            }

            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.e("AudioTrimmer", "Error stopping muxer: ${e.message}")
                    outputFile.delete()
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioTrimmer", "Error trimming audio: ${e.message}")
            outputFile.delete()
            return false
        } finally {
            extractor?.release()
            try { muxer?.release() } catch (e: Exception) {}
        }
    }
}

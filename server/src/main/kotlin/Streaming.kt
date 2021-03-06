package com.piankalabs

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object Streaming {

    object VideoWriter: OutgoingContent.WriteChannelContent() {
        override val contentType =
            ContentType
                .parse("multipart/x-mixed-replace")
                .withParameter("boundary", "stream")

        override suspend fun writeTo(channel: ByteWriteChannel) {
            while (true) {
                val frame = Camera.currentFrame()
                val bytes = toBytes(frame)

                pushCurrentFrame(channel, bytes)
            }
        }
    }

    object AudioWriter: OutgoingContent.WriteChannelContent() {
        override val contentType =
            ContentType.parse("audio/wav")

        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.writeFully(waveHeader())
            Microphone.start(channel.toOutputStream())
            while (true) {}
        }
    }

    object SpeakerWaveformWriter: OutgoingContent.WriteChannelContent() {
        override val contentType =
            ContentType
                .parse("multipart/x-mixed-replace")
                .withParameter("boundary", "stream")

        override suspend fun writeTo(channel: ByteWriteChannel) {
            while (true) {
                val frame = Speaker.waveform().image()
                val bytes = toPngBytes(frame)

                pushCurrentFrame(channel, bytes)
            }
        }
    }

    object MicrophoneWaveformWriter: OutgoingContent.WriteChannelContent() {
        override val contentType =
            ContentType
                .parse("multipart/x-mixed-replace")
                .withParameter("boundary", "stream")

        override suspend fun writeTo(channel: ByteWriteChannel) {
            while (true) {
                val frame = Microphone.waveform().image()
                val bytes = toPngBytes(frame)

                pushCurrentFrame(channel, bytes)
            }
        }
    }

    /* video */
    private const val crlf = "\r\n"

    private suspend fun pushCurrentFrame(channel: ByteWriteChannel, frame: ByteArray) {
        val headers = headers(frame)
        val boundary = boundary()

        channel.writeFully(headers)
        channel.writeFully(frame)
        channel.writeFully(boundary)
    }

    private fun headers(bytes: ByteArray): ByteArray {
        val headers =
            header("Content-Type", "image/jpg") +
            header("Content-Length", bytes.size) + crlf

        return headers.toByteArray()
    }

    private fun <A>header(key: String, value: A): String {
        return key + ": " + value.toString() + crlf
    }

    private fun boundary(): ByteArray {
        return "$crlf--stream$crlf".toByteArray()
    }

    private fun toBytes(frame: Mat): ByteArray {
        return toJpegBytes(toBufferedImage(frame))
    }

    private fun toJpegBytes(image: BufferedImage): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", stream)

        return stream.toByteArray()
    }

    private fun toPngBytes(image: BufferedImage): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(image, "png", stream)

        return stream.toByteArray()
    }

    private fun toBufferedImage(frame: Mat): BufferedImage {
        val encoded = MatOfByte()
        Imgcodecs.imencode(".jpg", frame, encoded)
        val bytes = encoded.toArray()
        val stream = ByteArrayInputStream(bytes)

        return ImageIO.read(stream)
    }

    /* audio */
    //http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html
    private fun waveHeader(): ByteArray {
        return writeString("RIFF")        + // riff header
               writeInteger(Int.MAX_VALUE)      + // chunk size (max for stream)
               writeString("WAVE")        + // wave header
               writeString("fmt ")        + // format chunk
               writeInteger(16)           + // chunk size
               writeShort(1)              + // format code
               writeShort(2)              + // channels
               writeInteger(16000)        + // sample rate
               writeInteger(64000)        + // data rate
               writeShort(4)              + // data block size
               writeShort(16)             + // bits per sample
               writeString("data")        + // data chunk
               writeInteger(Int.MAX_VALUE)        // chunk size (max for stream)
    }

    private fun writeString(value: String): ByteArray {
        return value.toByteArray()
    }

    private fun writeShort(value: Short): ByteArray {
        val v0 = ((value.toInt() ushr 0) and 0xFF).toByte()
        val v1 = ((value.toInt() ushr 8) and 0xFF).toByte()

        return byteArrayOf(v0, v1)
    }

    private fun writeInteger(value: Int): ByteArray {
        val v0 = ((value ushr 0 ) and 0xFF).toByte()
        val v1 = ((value ushr 8 ) and 0xFF).toByte()
        val v2 = ((value ushr 16) and 0xFF).toByte()
        val v3 = ((value ushr 24) and 0xFF).toByte()

        return byteArrayOf(v0, v1, v2, v3)
    }
}
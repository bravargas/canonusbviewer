package com.brainer.canonusbviewer.ptp

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PtpClient(private val transport: UsbPtpTransport) {

    private var sessionId: Int = 1
    private var transactionId: Int = 1

    fun getDeviceInfo() {
        val (data, resp) = execCommandWithDataIn(PtpConstants.OP_GET_DEVICE_INFO, intArrayOf())
        ensureOk(resp)
        // data ignored in MVP
    }

    fun openSession() {
        val resp = execCommand(PtpConstants.OP_OPEN_SESSION, intArrayOf(sessionId))
        ensureOk(resp)
    }

    fun closeSession() {
        val resp = execCommand(PtpConstants.OP_CLOSE_SESSION, intArrayOf())
        ensureOk(resp)
    }

    fun getObjectInfo(handle: Int): ObjectInfo {
        val (data, resp) = execCommandWithDataIn(PtpConstants.OP_GET_OBJECT_INFO, intArrayOf(handle))
        ensureOk(resp)

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        bb.position(4)
        val objectFormat = bb.short.toInt() and 0xFFFF
        bb.position(8)
        val compressed = bb.int.toLong() and 0xFFFFFFFFL

        val filename = tryExtractFilename(data) ?: "unknown.jpg"
        return ObjectInfo(objectFormat, compressed, filename)
    }

    data class ObjectInfo(val objectFormat: Int, val compressedSize: Long, val filename: String)

    fun getObject(handle: Int): ByteArray {
        val cmd = buildCommand(PtpConstants.OP_GET_OBJECT, intArrayOf(handle))
        transport.writeBulkAll(cmd)

        val dataContainer = transport.readContainer()
        val data = parseDataContainerPayload(dataContainer)

        val resp = transport.readContainer()
        ensureOk(resp)

        return data
    }

    // ---------- Internal ----------

    private fun execCommand(opCode: Int, params: IntArray): ByteArray {
        val cmd = buildCommand(opCode, params)
        transport.writeBulkAll(cmd)
        return transport.readContainer()
    }

    private fun execCommandWithDataIn(opCode: Int, params: IntArray): Pair<ByteArray, ByteArray> {
        val cmd = buildCommand(opCode, params)
        transport.writeBulkAll(cmd)

        val dataContainer = transport.readContainer()
        val payload = parseDataContainerPayload(dataContainer)

        val resp = transport.readContainer()
        return payload to resp
    }

    private fun buildCommand(opCode: Int, params: IntArray): ByteArray {
        val length = 12 + (4 * params.size)
        val bb = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(length)
        bb.putShort(PtpConstants.CONTAINER_COMMAND.toShort())
        bb.putShort(opCode.toShort())
        bb.putInt(transactionId++)
        for (p in params) bb.putInt(p)
        return bb.array()
    }

    private fun ensureOk(responseContainer: ByteArray) {
        val bb = ByteBuffer.wrap(responseContainer).order(ByteOrder.LITTLE_ENDIAN)
        val length = bb.int
        val type = bb.short.toInt() and 0xFFFF
        val code = bb.short.toInt() and 0xFFFF

        if (length < 12 || type != PtpConstants.CONTAINER_RESPONSE) {
            throw IllegalStateException("Invalid response container (len=$length type=$type)")
        }
        if (code != PtpConstants.RC_OK) {
            throw IllegalStateException("PTP error response code=0x${code.toString(16)}")
        }
    }

    private fun parseDataContainerPayload(container: ByteArray): ByteArray {
        val bb = ByteBuffer.wrap(container).order(ByteOrder.LITTLE_ENDIAN)
        val length = bb.int
        val type = bb.short.toInt() and 0xFFFF
        if (length < 12 || type != PtpConstants.CONTAINER_DATA) {
            throw IllegalStateException("Expected DATA container (len=$length type=$type)")
        }
        return container.copyOfRange(12, length)
    }

    private fun tryExtractFilename(data: ByteArray): String? {
        for (i in 0 until data.size - 4) {
            val len = data[i].toInt() and 0xFF
            if (len in 5..80) {
                val byteLen = 1 + (len * 2)
                if (i + byteLen <= data.size) {
                    val strBytes = data.copyOfRange(i + 1, i + byteLen)
                    val s = runCatching { String(strBytes, Charsets.UTF_16LE) }.getOrNull() ?: continue
                    val trimmed = s.trim { it <= ' ' || it == '\u0000' }
                    if (trimmed.endsWith(".JPG", true) || trimmed.endsWith(".JPEG", true)) return trimmed
                }
            }
        }
        return null
    }
}

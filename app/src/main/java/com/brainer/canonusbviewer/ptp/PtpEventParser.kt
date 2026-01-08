package com.brainer.canonusbviewer.ptp

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PtpEvent(val eventCode: Int, val transactionId: Int, val params: List<Int>)

object PtpEventParser {

    /**
     * Attempts to parse a PTP Event container from raw bytes.
     * Note: some cameras send only the raw interrupt payload; we try both formats.
     */
    fun parse(raw: ByteArray): PtpEvent? {
        if (raw.size < 6) return null

        // If it looks like a full PTP container: [len(4)][type(2)][code(2)][tx(4)]...
        if (raw.size >= 12) {
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val len = bb.int
            val type = bb.short.toInt() and 0xFFFF
            if (len >= 12 && len <= raw.size && type == PtpConstants.CONTAINER_EVENT) {
                val code = bb.short.toInt() and 0xFFFF
                val tx = bb.int
                val params = mutableListOf<Int>()
                while (bb.remaining() >= 4) params.add(bb.int)
                return PtpEvent(code, tx, params)
            }
        }

        // Fallback: many devices send interrupt payload: [code(2)][tx(4)][params...]
        runCatching {
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val code = bb.short.toInt() and 0xFFFF
            val tx = bb.int
            val params = mutableListOf<Int>()
            while (bb.remaining() >= 4) params.add(bb.int)
            return PtpEvent(code, tx, params)
        }

        return null
    }
}

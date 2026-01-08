package com.brainer.canonusbviewer.ptp

import android.hardware.usb.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbPtpTransport private constructor(
    val connection: UsbDeviceConnection,
    val ptpInterface: UsbInterface,
    val bulkIn: UsbEndpoint,
    val bulkOut: UsbEndpoint,
    val interruptIn: UsbEndpoint?
) {
    val interfaceClass: Int get() = ptpInterface.interfaceClass
    val interfaceSubClass: Int get() = ptpInterface.interfaceSubclass
    val interfaceProtocol: Int get() = ptpInterface.interfaceProtocol

    fun claim(): Boolean = connection.claimInterface(ptpInterface, true)

    fun release() {
        try { connection.releaseInterface(ptpInterface) } catch (_: Exception) {}
        try { connection.close() } catch (_: Exception) {}
    }

    fun writeBulkAll(data: ByteArray, timeoutMs: Int = 15000) {
        var offset = 0
        while (offset < data.size) {
            val chunk = minOf(data.size - offset, bulkOut.maxPacketSize * 32)
            val sent = connection.bulkTransfer(bulkOut, data, offset, chunk, timeoutMs)
            if (sent <= 0) throw IllegalStateException("bulk OUT failed (sent=$sent)")
            offset += sent
        }
    }

    /**
     * Robust read: reads exactly [len] bytes using multiple bulkTransfer calls.
     * Handles partial reads; retries on -1 a few times (device can be slow).
     */
    private fun readExact(len: Int, timeoutMs: Int): ByteArray {
        val out = ByteArray(len)
        var offset = 0
        var retries = 0

        while (offset < len) {
            val toRead = len - offset
            val r = connection.bulkTransfer(bulkIn, out, offset, toRead, timeoutMs)
            if (r > 0) {
                offset += r
                retries = 0
                continue
            }

            // r == 0 or -1: retry a bit
            retries++
            if (retries >= 8) {
                throw IllegalStateException("bulk IN read failed (read=$r offset=$offset need=$len)")
            }
            Thread.sleep(80)
        }
        return out
    }

    /**
     * Reads one complete PTP container (Command/Data/Response/Event).
     */
    fun readContainer(timeoutMs: Int = 15000): ByteArray {
        val header = readExact(12, timeoutMs)
        val totalLen = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
        if (totalLen < 12) throw IllegalStateException("Invalid container length: $totalLen")

        val full = ByteArray(totalLen)
        System.arraycopy(header, 0, full, 0, 12)

        if (totalLen > 12) {
            val rest = readExact(totalLen - 12, timeoutMs)
            System.arraycopy(rest, 0, full, 12, rest.size)
        }
        return full
    }

    fun readInterruptEventBlocking(bufferSize: Int = 64): ByteArray {
        val ep = interruptIn ?: throw IllegalStateException("No interrupt IN endpoint found")
        val req = UsbRequest()
        if (!req.initialize(connection, ep)) {
            throw IllegalStateException("UsbRequest initialize failed for interrupt endpoint")
        }

        val bb = ByteBuffer.allocate(maxOf(ep.maxPacketSize, bufferSize))
        if (!req.queue(bb, bb.capacity())) {
            throw IllegalStateException("UsbRequest queue failed")
        }

        val result = connection.requestWait() ?: throw IllegalStateException("requestWait returned null")
        if (result != req) throw IllegalStateException("Unexpected UsbRequest returned")

        val size = bb.position().coerceAtLeast(0)
        val arr = ByteArray(size)
        bb.flip()
        bb.get(arr)
        req.close()
        return arr
    }

    companion object {
        fun openBest(device: UsbDevice, manager: UsbManager): UsbPtpTransport {
            // Prefer Still Image (PTP) interface class=6
            val intf = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }
                ?: throw IllegalStateException("No Still Image (PTP) interface found")

            val conn = manager.openDevice(device) ?: throw IllegalStateException("openDevice returned null")

            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            var intrIn: UsbEndpoint? = null

            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                        if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep
                        if (ep.direction == UsbConstants.USB_DIR_OUT) bulkOut = ep
                    }
                    UsbConstants.USB_ENDPOINT_XFER_INT -> {
                        if (ep.direction == UsbConstants.USB_DIR_IN) intrIn = ep
                    }
                }
            }

            if (bulkIn == null || bulkOut == null) {
                try { conn.close() } catch (_: Exception) {}
                throw IllegalStateException("Bulk endpoints not found on PTP interface")
            }

            return UsbPtpTransport(conn, intf, bulkIn!!, bulkOut!!, intrIn)
        }
    }
}

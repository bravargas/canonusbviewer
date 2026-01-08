package com.brainer.canonusbviewer.ptp

object PtpConstants {
    // Container types
    const val CONTAINER_COMMAND: Int = 1
    const val CONTAINER_DATA: Int = 2
    const val CONTAINER_RESPONSE: Int = 3
    const val CONTAINER_EVENT: Int = 4

    // Standard operations (PTP)
    const val OP_GET_DEVICE_INFO: Int = 0x1001
    const val OP_OPEN_SESSION: Int = 0x1002
    const val OP_CLOSE_SESSION: Int = 0x1003
    const val OP_GET_STORAGE_IDS: Int = 0x1004
    const val OP_GET_OBJECT_HANDLES: Int = 0x1007
    const val OP_GET_OBJECT_INFO: Int = 0x1008
    const val OP_GET_OBJECT: Int = 0x1009

    // Standard response codes
    const val RC_OK: Int = 0x2001

    // Standard event codes
    const val EVT_OBJECT_ADDED: Int = 0x4002

    // Object formats (partial)
    const val OF_JPEG: Int = 0x3801
}

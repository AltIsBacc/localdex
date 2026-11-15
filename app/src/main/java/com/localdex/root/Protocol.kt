package com.localdex.root

import android.os.IBinder

object Protocol {
    const val TX_POINTER = IBinder.FIRST_CALL_TRANSACTION
    const val TX_SCROLL = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TX_KEY = IBinder.FIRST_CALL_TRANSACTION + 2

    const val POINTER_HOVER = 0
    const val POINTER_MOVE = 1
    const val POINTER_PRESS = 2
    const val POINTER_RELEASE = 3
}

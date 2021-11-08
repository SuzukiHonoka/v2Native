package org.starx_software_lab.v2native.util.exec

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.Reader

class StreamReader(private val reader: Reader, private val onData: (String) -> Unit) {
    companion object {
        const val TAG = "StreamReader"
    }

    var enable = true

    fun read() {
        Thread {
            val bfr = BufferedReader(reader)
            try {
                while (enable) {
                    val t = bfr.readLine()
                    onData(t)
                }
            } catch (e: IOException) {
                Log.d(TAG, "read: exception")
                e.printStackTrace()
                bfr.close()
            }
        }.start()
    }
}
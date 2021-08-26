package org.starx_software_lab.v2native.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.util.Utils
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived

@SuppressLint("InflateParams")
class ConditionUpdateDialog constructor(
    context: Context,
    title: String,
    cmd: String,
    key: Array<String>?,
    terminal: Exec?
) {
    private val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
    private var logger: TextView
    private var cancelAble = false
    private var exec = terminal
    private var outside = true

    init {
        if (exec == null) {
            exec = Exec()
            outside = false
        }
        if (key == null) {
            cancelAble = true
        }
        val view = LayoutInflater.from(context).inflate(R.layout.log_view, null)
        logger = view.findViewById(R.id.log_msg)
        Handler(context.mainLooper).post {
            dialog
                .setTitle(title)
                .setView(view)
                .setOnCancelListener {
                    if (cancelAble) {
                        it.cancel()
                        if (!outside) {
                            exec?.exit()
                        }
                    }
                }
                .create()
                .show()
        }
        Utils.updateText(logger, "### 命令准备执行")
    }

    init {
        exec!!.setListener(object : IDataReceived {
            override fun onFailed() {
                android.os.Handler(context.mainLooper).post { Utils.updateText(logger, "命令执行失败") }
                cancelAble = true
            }

            override fun onData(data: String) {
                android.os.Handler(context.mainLooper).post { Utils.updateText(logger, data) }
                key?.forEach {
                    if (data.contains(it)) {
                        exec!!.exit()
                        android.os.Handler(context.mainLooper)
                            .post { Utils.updateText(logger, "### 命令执行成功!!") }
                        cancelAble = true
                    }
                }
            }
        })
        exec!!.exec(cmd)
    }
}
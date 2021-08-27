package org.starx_software_lab.v2native.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.starx_software_lab.v2native.util.Utils

class OnBootCompleted : BroadcastReceiver() {
    override fun onReceive(v: Context, p1: Intent) {
        if (p1.action != Intent.ACTION_BOOT_COMPLETED || !Utils.getPerfBool(
                v,
                "autoStart",
                false
            ) || !Utils.checkConfig(v)
        ) return
        Utils.getServerIP(v).also {
            if (it.isNullOrEmpty()) {
                Toast.makeText(v, "读取远程服务器IP失败", Toast.LENGTH_SHORT).show()
                return
            }
            Intent().apply {
                putExtra("ip", it)
            }.also { intent ->
                v.startForegroundService(intent)
            }
        }
    }
}
package org.starx_software_lab.v2native.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.starx_software_lab.v2native.util.Config
import org.starx_software_lab.v2native.util.Utils

class OnBootCompleted : BroadcastReceiver() {
    override fun onReceive(v: Context, p1: Intent) {
        if (p1.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Utils.getPerfBool(
                v,
                "autoStart",
                false
            ) || !Config.updateConfigPath(v) || !Config.checkConfig()
        ) return
        if (!Utils.serviceAgent(v)) {
            Toast.makeText(v, "无法启动服务", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(v, "自启动服务成功", Toast.LENGTH_SHORT).show()
    }
}
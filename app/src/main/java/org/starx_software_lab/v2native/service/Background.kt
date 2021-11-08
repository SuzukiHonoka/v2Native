package org.starx_software_lab.v2native.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.util.Iptables
import org.starx_software_lab.v2native.util.Utils
import org.starx_software_lab.v2native.util.exec.StreamReader
import java.io.File
import java.io.InputStreamReader

class Background : Service() {
    companion object {
        const val TAG = "Background"
        const val id = "service"
    }

    private lateinit var terminal: Process
    private lateinit var iptables: Iptables
    private lateinit var receiver: Receiver
    private lateinit var serverIP: String
    private lateinit var main: Thread

    private var readers = MutableList<StreamReader?>(2) { null }
    private var running = false

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        regNotifyChannel()
        regReceiver()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        running = true
        main = Thread {
            try {
                Utils.cleanLogs()
                intent.getStringExtra("ip").also {
                    if (it == null) {
                        stopSelf()
                        return@Thread
                    }
                    serverIP = it
                }
                val filesPath = applicationContext.filesDir.absolutePath
                Utils.extract(filesPath, applicationContext.assets)
                val builder = ProcessBuilder(
                    "./libv2ray.so", "-config",
                    "$filesPath/config.json"
                ).apply {
                    directory(File(applicationInfo.nativeLibraryDir))
                    environment()["V2RAY_LOCATION_ASSET"] = application.filesDir.path
                }
                terminal = builder.start()
                Utils.updateLogs("Native Lib Path: ${applicationInfo.nativeLibraryDir}")
                Utils.updateLogs("V2ray 已启动")
                // reader threads
                readers[0] = StreamReader(InputStreamReader(terminal.inputStream)) {
                    Utils.updateLogs(it)
                }.apply { read() }

                readers[1] = StreamReader(InputStreamReader(terminal.errorStream)) {
                    Utils.updateLogs(it)
                }.apply { read() }

                iptables = Iptables(false, serverIP, this)
                iptables.setup()
            } catch (e: InterruptedException) {
                return@Thread
            }
        }.apply {
            start()
        }
        updateStatus()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        running = false
        unregisterReceiver(receiver)
        Thread {
            cleanUP(false)
            if (main.isAlive) main.interrupt()
            updateStatus()
        }.start()
        super.onDestroy()
    }

    private fun cleanUP(hard: Boolean) {
        readers.forEach {
            it!!.enable = false
        }
        iptables.clean(hard)
        terminal.destroyForcibly()
        Utils.cancel(this)
    }

    private fun regNotifyChannel() {
        // Create the NotificationChannel
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(id, name, importance)
        mChannel.description = descriptionText
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
        Notification()
        startForeground(1, Utils.notify(this, "服务正在启动", "初始化中.."))
    }

    private fun regReceiver() {
        receiver = Receiver()
        val filter = IntentFilter("org.starx_software_lab.v2native.service.receiver")
        registerReceiver(receiver, filter)
    }

    private fun updateStatus() {
        Intent().also {
            it.action = "org.starx_software_lab.v2native.ui.home"
            it.putExtra("running", running)
            applicationContext.sendBroadcast(it)
        }
    }

    inner class Receiver : android.content.BroadcastReceiver() {

        override fun onReceive(p0: Context, intent: Intent) {
            Log.d(TAG, "onReceive: ping")
            if (!intent.getBooleanExtra("clean", false)) {
                updateStatus()
                return
            }
            Thread {
                cleanUP(true)
                stopSelf()
                updateStatus()
                Log.d(TAG, "onReceive: sent kill")
            }.start()
        }
    }
}
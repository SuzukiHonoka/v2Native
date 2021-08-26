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
import org.starx_software_lab.v2native.ui.slideshow.SlideshowFragment
import org.starx_software_lab.v2native.util.Utils
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived

class Background : Service() {
    companion object {
        const val TAG = "Background"
        const val id = "service"
    }

    private val terminals = Array<Exec?>(2) { null }
    private lateinit var iptables: Utils.Iptables
    private lateinit var receiver: Receiver
    private lateinit var serverIP: String
    private lateinit var main: Thread
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
        main = Thread {
            running = true
            SlideshowFragment.logs = ""
            serverIP = intent.getStringExtra("ip")!!
            val filesPath = applicationContext.filesDir.absolutePath
            Utils.extract(filesPath, applicationContext.assets)
            terminals[0] = Exec().also {
                it.setListener(object : IDataReceived {
                    override fun onFailed() {
                        SlideshowFragment.logs += "Failed"
                    }

                    override fun onData(data: String) {
                        SlideshowFragment.logs += "$data\n"
                    }
                })
                it.exec("$filesPath/v2ray -config $filesPath/config.json")
                SlideshowFragment.logs += "V2ray 已启动\n"
            }
            terminals[1] = Exec().also {
                it.setListener(object : IDataReceived {
                    override fun onFailed() {
                        Log.d(TAG, "onFailed: terminal")
                    }

                    override fun onData(data: String?) {
                        SlideshowFragment.logs += "$data\n"
                        if (it.lastCmd == "nproc") {
                            it.exec("${filesPath}/ipt2socks -s 0.0.0.0 -p 10808 -b \"0.0.0.0\" -l \"12345\" -R -j $data")
                            SlideshowFragment.logs += "ipt2socks $data 线程 已启动\n"
                        }
                    }
                })
                it.exec("nproc")
            }
            iptables = Utils.Iptables(serverIP, this).apply {
                setup()
            }
        }
        main.start()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        Thread {
            unregisterReceiver(receiver)
            cleanUP(false)
            main.interrupt()
        }.start()
        super.onDestroy()
    }

    private fun cleanUP(hard: Boolean) {
        running = false
        iptables.clean(hard)
        terminals.forEach {
            it?.exit()
        }
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
        receiver.setContext(this)
        val filter = IntentFilter("org.starx_software_lab.v2native.service.receiver")
        registerReceiver(receiver, filter)
    }

    class Receiver : android.content.BroadcastReceiver() {
        private lateinit var b: Background
        fun setContext(b: Background) {
            this.b = b
        }

        override fun onReceive(p0: Context, intent: Intent) {
            Log.d(TAG, "onReceive: ping")
            Thread {
                if (intent.getBooleanExtra("clean", false)) {
                    b.cleanUP(true)
                    Intent().also {
                        it.action = "org.starx_software_lab.v2native.ui.home"
                        it.putExtra("kill", true)
                        p0.sendBroadcast(it)
                        Log.d(TAG, "onReceive: sent kill")
                    }
                    Thread.sleep(1000)
                    b.stopSelf()
                } else {
                    Intent().also {
                        it.action = "org.starx_software_lab.v2native.ui.home"
                        p0.sendBroadcast(it)
                    }
                }
            }.start()
        }
    }
}
package org.starx_software_lab.v2native.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.starx_software_lab.v2native.MainActivity
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.service.Background
import org.starx_software_lab.v2native.util.Config.Companion.getServerIP
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate


class Utils {
    companion object {
        private const val TAG = "Util"
        private val preloads = arrayOf(
            "v2ray",
            "v2ctl",
            "geosite.dat",
            "geoip.dat",
            "cn.zone"
        )
        private val exp = LocalDate.parse("2022-09-15")
        fun checkEXP() = LocalDate.now() >= exp

        fun checkRoot(): Boolean {
            var status: Boolean? = null
            val t = Exec().apply {
                setListener(object : IDataReceived {
                    override fun onFailed() {
                        status = false
                        Log.d("CheckRoot", "failed")
                        return
                    }

                    override fun onData(data: String) {
                        status = data.contains("root")
                        Log.d("CheckRoot", "$status")
                    }

                })
            }
            while (t.state) {
                if (status == null) {
                    try {
                        t.exec("whoami")
                    } catch (e: Exception) {
                        return false
                    }
                    Thread.sleep(500)
                    continue
                }
                if (status == true) {
                    t.exit()
                    return true
                }
                return false
            }
            return false
        }

        fun extract(path: String, assetManager: AssetManager): Boolean {
            println("path: $path")
            preloads.forEach {
                val p = "$path/$it"
                val f = File(p)
                if (!f.exists()) {
                    val data = assetManager.open(it)
                    val out = FileOutputStream(File(p))
                    out.use { dst ->
                        data.copyTo(dst)
                    }
                    f.setExecutable(true)
                }
                Log.d("util", "extract: $p installed!")
            }
            return true
        }

        fun notify(context: Context, title: String, msg: String): Notification {
            val notifyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val notifyPendingIntent = PendingIntent.getActivity(
                context,
                0,
                notifyIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, Background.id).apply {
                setSilent(true)
                setSmallIcon(R.drawable.ic_server)
                setContentTitle(title)
                setOngoing(true)
                setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                priority = NotificationCompat.PRIORITY_MAX
                setContentIntent(notifyPendingIntent)
            }.build()

            with(NotificationManagerCompat.from(context)) {
                notify(1, builder)
            }
            return builder
        }

        fun notifyProgress(
            context: Context,
            title: String,
            msg: String,
            total: Int,
            current: Int
        ): Notification {
            val builder = NotificationCompat.Builder(context, Background.id).apply {
                setSilent(true)
                setSmallIcon(R.drawable.ic_server)
                setContentTitle(title)
                setOngoing(true)
                setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                setProgress(total, current, false)
                priority = NotificationCompat.PRIORITY_MAX
            }.build()
            with(NotificationManagerCompat.from(context)) {
                notify(1, builder)
            }
            return builder
        }

        fun cancel(context: Context) =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(1)

//        public fun generateServerBlock(uuid: String, server: String, port: String, host: String,
//                                       path: String, sni: String){
//            return
//        }

        fun cleanLogs() {
            Iptables.logs = ""
        }

        fun updateLogs(msg: String) {
            Iptables.logs += "$msg\n"
        }


        fun getPerfStr(context: Context, key: String, def: String): String =
            PreferenceManager.getDefaultSharedPreferences(context).getString(key, def)!!

        fun getPerfBool(context: Context, key: String, def: Boolean): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, def)

        fun getPerfInt(context: Context, key: String, def: Int): Int =
            getPerfStr(context, key, def.toString()).toInt()

        fun serviceAgent(context: Context): Boolean {
            val intent = Intent(context, Background::class.java)
            getServerIP().also {
                if (it.isNullOrEmpty()) {
                    return false
                }
                intent.putExtra("ip", it)
            }
            context.startForegroundService(intent)
            return true
        }

        fun prettifyJson(s: String): String {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val je = JsonParser.parseString(s)
            return gson.toJson(je)
        }

        fun retrieveContent(url: String): String {
            try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2 * 1000
                    readTimeout = 2 * 1000
                    requestMethod = "GET"
                }.also { r ->
                    if (r.responseCode == 204) {
                        return "204"
                    }
                    if (r.responseCode != 200) {
                        return ""
                    }
                    return r.inputStream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                return ""
            }
        }

        fun isTproxyEnabled(v: Context): Boolean {
            return getPerfStr(v, "type", "REDIRECT") != "REDIRECT"
        }
    }


//    class Iptables(private val ip: String, private val context: Context) {
//
//        companion object {
//            const val TAG = "IPTABLES"
//            const val chain = "STARX"
//            val reserved = setOf(
//                "0.0.0.0/8",
//                "10.0.0.0/8",
//                "100.64.0.0/10",
//                "127.0.0.0/8",
//                "169.254.0.0/16",
//                "172.16.0.0/12",
//                "192.0.0.0/24",
//                "192.0.2.0/24",
//                "192.88.99.0/24",
//                "192.168.0.0/16",
//                "198.18.0.0/15",
//                "198.51.100.0/24",
//                "203.0.113.0/24",
//                "224.0.0.0/4",
//                "233.252.0.0/24",
//                "240.0.0.0/4",
//                "255.255.255.255/32"
//            )
//            var logs = ""
//            private val tproxyCMD = setOf(
//                "iptables -t mangle -A $chain -p tcp -j TPROXY --on-ip 127.0.0.1 --on-port 12345 --tproxy-mark 1",
//                "iptables -t mangle -A $chain -p udp -j TPROXY --on-ip 127.0.0.1 --on-port 12345 --tproxy-mark 1"
//            )
//            private val setupRuleCMD = arrayOf(
//                "ip rule add fwmark 1 table 100",
//                "ip route add local 0.0.0.0/0 dev lo table 100"
//            )
//            private const val checkRuleCMD = "ip rule list|grep \"lookup 100\"||echo \"fail\"\n"
//            private const val setupTproxyCMD =
//                "iptables -t mangle -A OUTPUT -p tcp -j MARK --set-mark 1"
//        }
//
//        private var terminal: Exec = Exec()
//        private var initialized: Boolean = false
//        private var done: Boolean = false
//        private var initing = false
//
//        // Cmd
//        private val tproxy = isTproxyEnabled(context)
//        private val table = if (tproxy) "mangle" else "nat"
//        private val listCMD = "iptables -t $table -L $chain 1"
//        private val outputCMD = "iptables -t $table -L OUTPUT"
//        private var initCMD = arrayOf(
//            "iptables -t $table -N $chain",
//            "iptables -t $table -A $chain -d $ip -j RETURN"
//            //"iptables -t $table -A $chain -p tcp -j RETURN -m mark --mark 0xff"
//        )
//        private val bypassDNS = setOf(
//            "iptables -t $table -I $chain 2 -p tcp --dport 53 -j RETURN",
//            "iptables -t $table -I $chain 2 -p udp --dport 53 -j RETURN"
//        )
//        private val applyCMD = arrayOf(
//            "iptables -t $table -A OUTPUT -p tcp -j $chain",
//            "iptables -t $table -A PREROUTING -p tcp -j $chain"
//        )
//        private val cleanHard = arrayOf(
//            "iptables -t $table -F $chain",
//            "iptables -t $table -X $chain",
//            "killall v2ray",
//            "killall ipt2socks",
//            //"killall iptables"
//        )
//
//        fun setup() {
//            terminal.setListener(object : IDataReceived {
//                override fun onFailed() {
//                    Log.d(TAG, "onFailed: setup")
//                }
//
//                override fun onData(data: String) {
//                    Log.d(TAG, "${terminal.lastCmd} -> $data")
//                    updateLogs(data)
//                    if (data.contains("lock")) {
//                        val cmd = terminal.lastCmd
//                        terminal.exec("killall iptables")
//                        if (!sleep(300)) return
//                        terminal.exec(cmd)
//                        return
//                    }
//                    when (terminal.lastCmd) {
//                        checkRuleCMD -> {
//                            if (data.contains("fail")) {
//                                setupRuleCMD.forEach {
//                                    terminal.exec(it)
//                                }
//                            }
//                        }
//                        listCMD -> {
//                            if (data.contains("No")) {
//                                Log.d(TAG, "onData: need init")
//                                initialized = false
//                                init()
//                                return
//                            }
//                            terminal.exec("iptables -t $table -D $chain 1")
//                            terminal.exec("iptables -t $table -I $chain 1 -d $ip -j RETURN")
//                            initialized = true
//                        }
//                        outputCMD -> {
//                            if (!data.contains(chain)) {
//                                Log.d(TAG, "onData: need apply")
//                                if (tproxy) {
//                                    terminal.exec(setupTproxyCMD)
//                                }
//                                if (getPerfBool(context, "allowOther", false)) {
//                                    if (!tproxy) {
//                                        applyCMD.forEach {
//                                            terminal.exec(it)
//                                        }
//                                    } else {
//                                        terminal.exec(applyCMD[1])
//                                    }
//                                } else {
//                                    if (tproxy) {
//                                        terminal.exec(applyCMD[1])
//                                        //TODO("RETURN SOURCE IN RESERVE")
//                                    } else {
//                                        terminal.exec(applyCMD[0])
//                                    }
//                                }
//                                done = true
//                                updateLogs("iptables 分流规则应用完成")
//                            }
//                        }
//                    }
//                }
//            })
//            clean(false)
//            if (!sleep(500)) return
//            terminal.exec(listCMD)
//            var wait = 0
//            while (!initialized) {
//                Log.d(TAG, "setup: init waiting")
//                if (wait > 600) {
//                    Log.d(TAG, "setup: init failed")
//                    notify(context, "服务正在运行", "初始化失败")
//                    break
//                }
//                if (!sleep(1000)) return
//                wait++
//            }
//            terminal.exec(outputCMD)
//            notify(context, "服务正在运行", "等待分流链被应用..")
//            var timeout = 30
//            getPerfInt(context, "timeout", 30).also {
//                it.also { t ->
//                    if (t >= 30) {
//                        timeout = t
//                    }
//                }
//            }
//            wait = 0
//            while (!done) {
//                if (wait > timeout) {
//                    Log.d(TAG, "setup: force apply")
//                    applyCMD.forEach {
//                        terminal.exec(it)
//                    }
//                    break
//                }
//                Log.d(TAG, "setup: wait done")
//                if (!sleep(1000)) return
//                wait++
//            }
//            notify(context, "服务正在运行", "系统启动正常~")
//        }
//
//        fun init() {
//            initing = true
//            initCMD.forEach {
//                terminal.exec(it)
//            }
//            if (getPerfBool(context, "dns", true)) {
//                bypassDNS.forEach {
//                    terminal.exec(it)
//                }
//            }
//            reserved.forEach {
//                terminal.exec("iptables -t $table -A $chain -d $it -j RETURN")
//            }
//            getPerfStr(context, "addition", "").split(",").forEach {
//                if (it.isNotEmpty()) terminal.exec("iptables -t nat -A $chain -d $it -j RETURN")
//            }
//            val start = System.currentTimeMillis()
//            File(context.filesDir.absolutePath, "cn.zone").readLines().also {
//                it.forEachIndexed { index, s ->
//                    val i = index + 1
//                    terminal.exec("iptables -t $table -A $chain -d $s -j RETURN")
//                    if (i % 50 == 0 || i == it.count()) {
//                        notifyProgress(
//                            context,
//                            "正在应用国内分流规则",
//                            "应用中.. ($i/${it.count()})",
//                            it.count(),
//                            i
//                        )
//                        if (i == it.count()) {
//                            notifyProgress(context, "应用国内分流规则完成", "应用完成", it.count(), index)
//                            updateLogs("inserting china rule sets took: ${(System.currentTimeMillis() - start) / 1000}s")
//                        }
//                    }
//                }
//            }
//            if (tproxy) {
//                tproxyCMD.forEach {
//                    terminal.exec(it)
//                }
//                if (!sleep(500)) return
//                terminal.exec(checkRuleCMD)
//            } else {
//                terminal.exec("iptables -t nat -A $chain -p tcp -j REDIRECT --to-ports 12345")
//            }
//            initialized = true
//            Log.d(TAG, "init: done")
//        }
//
//        fun clean(hard: Boolean) {
//            Log.d(TAG, "clean: clean")
//            applyCMD.forEach {
//                terminal.exec(it.replace("-A", "-D"))
//            }
//            terminal.exec(setupTproxyCMD.replace("-A", "-D"))
//            if (hard) cleanHard.forEach {
//                terminal.exec(it)
//            }
//        }
//    }
}
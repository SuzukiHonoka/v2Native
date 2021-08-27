package org.starx_software_lab.v2native.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.util.Log
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.starx_software_lab.v2native.MainActivity
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.service.Background
import org.starx_software_lab.v2native.ui.home.HomeFragment
import org.starx_software_lab.v2native.ui.slideshow.SlideshowFragment
import org.starx_software_lab.v2native.util.Utils.Iptables.Companion.chain
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate


class Utils {
    companion object {
        val TAG = "Util"
        private val preloads = arrayOf(
            "v2ray",
            "v2ctl",
            "geosite.dat",
            "geoip.dat",
            "ipt2socks",
            "cn.zone"
        )

        val dns = arrayOf(
            "8.8.8.8",
            "8.8.4.4",
            "1.1.1.1"
        )

        private val exp = LocalDate.parse("2021-09-15")

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

        fun updateText(textView: TextView, msg: String) =
            "${textView.text}\n$msg".also { textView.text = it }

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
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1)

        fun writeConfig(context: Context, config: String) =
            File(context.filesDir.absolutePath + "/config.json").writeText(config)

        fun reWriteConfig(v: Context, s: String): Boolean {
            val obj = JsonParser.parseString(s).asJsonObject.apply {
                remove("inbounds")
                remove("dns")
                remove("policy")
                remove("routing")
            }.also { els ->
                getServerBlock(els).also { el ->
                    if (el == null) {
                        Log.d(TAG, "reWriteConfig: 未找到可用出站配置")
                        return false
                    }
                    els.remove("outbounds")
                    els.add("outbounds", JsonArray().apply {
                        add(el)
                    })
                }
            }
            val socks = JsonArray().apply {
                val setting = JsonObject().apply {
                    addProperty(
                        "listen",
                        if (getPerfBool(v, "export", false)) "0.0.0.0" else "127.0.0.1"
                    )
                    addProperty("port", 10808)
                    addProperty("protocol", "socks")
                    addProperty("tag", "socks")
                    val sniffing = JsonObject().apply {
                        val protocols = JsonArray().apply {
                            add("tls")
                            add("http")
                        }
                        add("destOverride", protocols)
                        addProperty("enabled", true)
                    }
                    add("sniffing", sniffing)
                }
                add(setting)
            }
            val dns = JsonObject().apply {
                val servers = JsonArray().apply {
                    dns.forEach { s ->
                        add(s)
                    }
                }
                add("servers", servers)
            }
            val routing = JsonObject().apply {
                addProperty("domainStrategy", "IPIfNonMatch")
                addProperty("domainMatcher", "mph")
                val rules = JsonArray().apply {
                    val proxyDNS = JsonObject().apply {
                        val dnsIP = JsonArray().apply {
                            Utils.dns.forEach { s ->
                                add(s)
                            }
                        }
                        add("ip", dnsIP)
                        addProperty("outboundTag", "proxy")
                        addProperty("port", 53)
                        addProperty("type", "field")
                    }
                    add(proxyDNS)
                }
                add("rules", rules)
            }
            obj.add("dns", dns)
            obj.add("routing", routing)
            obj.add("inbounds", socks)
            Log.d(HomeFragment.TAG, "formattedJson: $obj")
            writeConfig(v, obj.toString())
            return true
        }

        fun checkConfig(context: Context) =
            File(context.filesDir.absolutePath + "/config.json").exists()

        fun getServerIP(context: Context): String? {
            val obj =
                JsonParser.parseString(File(context.filesDir.absolutePath + "/config.json").readText()).asJsonObject
            obj.getAsJsonArray("outbounds").forEach { els ->
                if (els.asJsonObject.get("tag").asString == "proxy") {
                    Log.d(HomeFragment.TAG, "parser: proxy outbound found!")
                    val serverIP =
                        els.asJsonObject.get("settings").asJsonObject.get("vnext").asJsonArray.get(0).asJsonObject.get(
                            "address"
                        ).asString
                    Log.d(HomeFragment.TAG, "parser: address -> $serverIP")
                    return serverIP
                }
            }
            return null
        }

        fun getServerBlock(json: JsonObject): JsonElement? {
            json.getAsJsonArray("outbounds").forEach { els ->
                if (els.asJsonObject.get("tag").asString == "proxy") {
                    Log.d(HomeFragment.TAG, "parser: proxy outbound found!")
                    return els
                }
            }
            return null
        }

        fun cleanLogs() {
            SlideshowFragment.logs = ""
        }

        fun updateLogs(msg: String) {
            SlideshowFragment.logs += "$msg\n"
        }

        fun sleep(t: Long): Boolean {
            try {
                Thread.sleep(t)
            } catch (e: InterruptedException) {
                Log.d(Iptables.TAG, "sleep: thread killed")
                Thread.currentThread().interrupt()
                return false
            }
            return true
        }

        fun getPerfStr(context: Context, key: String, def: String) =
            PreferenceManager.getDefaultSharedPreferences(context).getString(key, def)

        fun getPerfBool(context: Context, key: String, def: Boolean) =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, def)

        fun getPerfInt(context: Context, key: String, def: Int) =
            getPerfStr(context, key, def.toString())
    }

    class Commands {
        companion object {
            const val list = "iptables -t nat -L $chain 1"
            const val output = "iptables -t nat -L OUTPUT"
        }
    }

    class Iptables(private val ip: String, private val context: Context) {

        companion object {
            const val TAG = "IPTABLES"
            const val chain = "STARX"
            val reserved = arrayOf(
                "0.0.0.0/8",
                "10.0.0.0/8",
                "100.64.0.0/10",
                "127.0.0.0/8",
                "169.254.0.0/16",
                "172.16.0.0/12",
                "192.0.0.0/24",
                "192.0.2.0/24",
                "192.88.99.0/24",
                "192.168.0.0/16",
                "198.18.0.0/15",
                "198.51.100.0/24",
                "203.0.113.0/24",
                "224.0.0.0/4",
                "233.252.0.0/24",
                "240.0.0.0/4",
                "255.255.255.255/32"
            )
            val bypassDNS = arrayOf(
                "iptables -t nat -I STARX 2 -p tcp --dport 53 -j RETURN",
                "iptables -t nat -I STARX 2 -p udp --dport 53 -j RETURN"
            )
            val applyCMD = arrayOf(
                "iptables -t nat -A PREROUTING -p tcp -j $chain",
                "iptables -t nat -A OUTPUT -p tcp -j $chain"
            )
            val cleanCMD = arrayOf(
                "iptables -t nat -D PREROUTING -p tcp -j $chain",
                "iptables -t nat -D OUTPUT -p tcp -j $chain"
            )
            val cleanHard = arrayOf(
                "iptables -t nat -F $chain",
                "iptables -t nat -X $chain",
                "killall v2ray",
                "killall ipt2socks",
                "killall iptables"
            )
            lateinit var terminal: Exec
        }

        private var inited: Boolean? = null
        private var done: Boolean = false
        private var initing = false
        private var cleaned = false

        init {
            terminal = Exec()
        }

        fun setup() {
            terminal.setListener(object : IDataReceived {
                override fun onFailed() {
                    Log.d(TAG, "onFailed: setup")
                }

                override fun onData(data: String) {
                    Log.d(TAG, "${terminal.lastCmd} -> $data")
                    updateLogs(data)
                    if (data.contains("lock")) {
                        val cmd = terminal.lastCmd
                        terminal.exec("killall iptables")
                        if (!sleep(300)) return
                        terminal.exec(cmd)
                        return
                    }
                    when (terminal.lastCmd) {
                        Commands.list -> {
                            if (data.contains("No")) {
                                Log.d(TAG, "onData: need init")
                                inited = false
                                init()
                                return
                            }
                            terminal.exec("iptables -t nat -D $chain 1")
                            terminal.exec("iptables -t nat -I $chain 1 -d $ip -j RETURN")
                            inited = true
                        }
                        Commands.output -> {
                            if (!data.contains(chain) && !done) {
                                Log.d(TAG, "onData: need apply")
                                applyCMD.forEach {
                                    terminal.exec(it)
                                }
                                done = true
                                updateLogs("iptables 分流规则应用完成")
                            }
                        }
                    }
                }
            })
            done = false
            initing = false
            clean(false)
            if (!sleep(1000)) return
            cleaned = false
            terminal.exec(Commands.list)
            var wait = 0
            while (inited == null) {
                if (wait > 600) {
                    Log.d(TAG, "setup: init failed")
                    notify(context, "服务正在运行", "初始化失败")
                    break
                }
                if (!sleep(1000)) return
                wait++
            }
            while (inited == false) {
                Log.d(TAG, "setup: init waiting")
                if (!sleep(1000)) return
            }
            terminal.exec(Commands.output)
            notify(context, "服务正在运行", "等待分流链被应用..")
            var timeout = 30
            getPerfStr(context, "timeout", "30").also {
                it!!.toInt().also { t ->
                    if (t >= 30) {
                        timeout = t
                    }
                }
            }
            wait = 0
            while (!done) {
                if (wait > timeout) {
                    Log.d(TAG, "setup: force apply")
                    applyCMD.forEach {
                        terminal.exec(it)
                    }
                    break
                }
                Log.d(TAG, "setup: wait done")
                if (!sleep(1000)) return
                wait++
            }
            notify(context, "服务正在运行", "享受极速科学上网的快乐吧~")
        }

        fun init() {
            initing = true
            terminal.exec("iptables -t nat -N $chain")
            terminal.exec("iptables -t nat -A $chain -d $ip -j RETURN")
            bypassDNS.forEach {
                terminal.exec(it)
            }
            reserved.forEach {
                terminal.exec("iptables -t nat -A $chain -d $it -j RETURN")
            }
            getPerfStr(context, "addition", "")!!.split(",").forEach {
                terminal.exec("iptables -t nat -A $chain -d $it -j RETURN")
            }
            val start = System.currentTimeMillis()
            File(context.filesDir.absolutePath, "cn.zone").readLines().also {
                it.forEachIndexed { index, s ->
                    val i = index + 1
                    notifyProgress(
                        context,
                        "正在应用国内分流规则",
                        "应用中.. ($i/${it.count()})",
                        it.count(),
                        i
                    )
                    terminal.exec("iptables -t nat -A $chain -d $s -j RETURN")
                    if (i == it.count()) {
                        notifyProgress(context, "应用国内分流规则完成", "应用完成", it.count(), index)
                        updateLogs("init china took: ${(System.currentTimeMillis() - start) / 1000}s")
                    }
                }
            }
            terminal.exec("iptables -t nat -A $chain -p tcp -j REDIRECT --to-ports 12345")
            inited = true
            Log.d(TAG, "init: done")
        }

        fun clean(hard: Boolean) {
            Log.d(TAG, "clean: clean")
            inited = false
            cleanCMD.forEach {
                terminal.exec(it)
                if (!sleep(1000)) return
            }
            cleaned = true
            if (hard) cleanHard.forEach {
                terminal.exec(it)
            }
        }
    }
}
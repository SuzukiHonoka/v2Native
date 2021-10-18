package org.starx_software_lab.v2native.util

import android.content.Context
import android.util.Log
import org.starx_software_lab.v2native.util.CIDR.Companion.reserved
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived
import java.io.File

class Iptables(
    private val useTproxy: Boolean,
    private val ip: String,
    private val context: Context
) {

    private var initialized = false
    private var done = false
    private val terminal: Exec = Exec()
    val table = if (useTproxy) "mangle" else "nat"

    // cmd
    private var init = arrayOf(
        "iptables -t $table -N $chain",
        "iptables -t $table -A $chain -d $ip -j RETURN"
        //"iptables -t $table -A $chain -p tcp -j RETURN -m mark --mark 0xff"
    )
    private val clean = arrayOf(
        "iptables -t $table -F $chain > /dev/null 2>&1",
        "iptables -t $table -X $chain > /dev/null 2>&1",
        "killall v2ray",
        //"killall iptables"
    )
    private val bypassDNS = setOf(
        "iptables -t $table -I $chain 2 -p tcp --dport 53 -j RETURN",
        "iptables -t $table -I $chain 2 -p udp --dport 53 -j RETURN"
    )
    private val list = "iptables -t $table -L $chain 1"
    private val apply = arrayOf(
        "iptables -t nat -A OUTPUT${if (useTproxy) " " else " -p tcp "}-j $chain",
        "iptables -t nat -A PREROUTING${if (useTproxy) " " else " -p tcp "}-j $chain" // for other devices
    )
    private val checkApply = "iptables -t $table -L OUTPUT"

    companion object {
        const val chain = "STARX"
        private const val TAG = "IPTABLES"
        var logs = ""
    }

    fun clean(hard: Boolean) {
        Log.d(TAG, "clean: normal")
        if (useTproxy) {
            terminal.exec(Tproxy.setupTproxyMark.replace("-A", "-D") + " > /dev/null 2>&1")
        } else {
            apply.forEach {
                terminal.exec(it.replace("-A", "-D") + " > /dev/null 2>&1")
            }
        }
        if (hard) {
            Log.d(TAG, "clean: hard")
            clean.forEach {
                terminal.exec(it)
            }
        }
    }

    private fun apply() {
        if (Utils.getPerfBool(context, "allowOther", false)) {
            // allow other
            apply.forEach {
                terminal.exec(it)
            }
        } else {
            // not allow
            if (useTproxy) {
                apply.forEach {
                    terminal.exec(it)
                }
                //TODO("RETURN SOURCE IN RESERVE")
            } else {
                terminal.exec(apply[0])
            }
        }
    }

    init {
        terminal.setListener(object : IDataReceived {
            override fun onFailed() {
                Log.d(TAG, "onFailed: iptables")
            }

            override fun onData(data: String) {
                Log.d(TAG, "${terminal.lastCmd} -> $data")
                Utils.updateLogs(data)
                if (data.contains("lock")) {
                    val cmd = terminal.lastCmd
                    terminal.exec("killall iptables")
                    terminal.exec(cmd)
                    return
                }
                when (terminal.lastCmd) {
                    Tproxy.checkSetup -> {
                        if (data.contains("fail")) {
                            Tproxy.setup.forEach {
                                terminal.exec(it)
                            }
                        }
                    }
                    list -> {
                        // check if chain exist
                        if (data.contains("No")) {
                            Log.d(TAG, "onData: need init")
                            initialized = false
                            init()
                            return
                        }
                        // delete and insert new server ip
                        terminal.exec("iptables -t $table -D $chain 1")
                        terminal.exec("iptables -t $table -I $chain 1 -d $ip -j RETURN")
                        // set flag
                        initialized = true
                    }
                    checkApply -> {
                        // check if chain applied
                        if (!data.contains(chain)) {
                            Log.d(TAG, "onData: need apply")
                            // maybe stuck sometime so double check
                            if (!done) {
                                apply()
                                done = true
                            }
                            Utils.updateLogs("iptables 分流规则应用完成")
                        }
                    }
                }
            }
        })
    }

    fun setup() {
        // clean
        this.clean(false)
        // check if already created the chain
        terminal.exec(list)
        var count = 0
        // wait init
        while (!initialized) {
            // timeout
            if (count > 300) {
                Log.d(TAG, "setup: init failed")
                Utils.notify(context, "IPTABLES", "初始化失败")
                return
            }
            // wait for init chain
            Log.d(TAG, "setup: init waiting")
            Thread.sleep(1000)
            count++
        }
        Utils.notify(context, "服务正在运行", "等待分流链被应用..")
        if (!useTproxy) {
            // Redirect
            terminal.exec(checkApply)
        }
        // wait done
        // get perf timeout
        var timeout = Utils.getPerfInt(context, "timeout", 30)
        // set to at least 30
        if (timeout < 30) timeout = 30
        // reuse counter
        count = 0
        while (!done) {
            if (count >= timeout) {
                Log.d(TAG, "setup: force apply")
                apply()
                done = true
                break
            }
            Log.d(TAG, "setup: wait done")
            Thread.sleep(1000)
            count++
        }
        Utils.notify(context, "服务正在运行", "Enjoy faster browser experience~")
    }

    fun init() {
        // create chain
        init.forEach {
            terminal.exec(it)
        }
        // bypass dns
        if (Utils.getPerfBool(context, "dns", true)) {
            bypassDNS.forEach {
                terminal.exec(it)
            }
        }
        // bypass reserved
        reserved.forEach {
            terminal.exec("iptables -t $table -A $chain -d $it -j RETURN")
        }
        // bypass addition
        Utils.getPerfStr(context, "addition", "").split(",").forEach {
            if (it.isNotEmpty()) terminal.exec("iptables -t nat -A $chain -d $it -j RETURN")
        }
        val start = System.currentTimeMillis()
        // bypass china ips
        File(context.filesDir.absolutePath, "cn.zone").readLines().also {
            it.forEachIndexed { index, s ->
                // real index
                val i = index + 1
                terminal.exec("iptables -t $table -A $chain -d $s -j RETURN")
                Log.d(TAG, "index: $i")
                // refresh progress every 50 line or at the end
                if (i % 50 == 0 || i == it.count()) {
                    Utils.notifyProgress(
                        context,
                        "正在应用国内分流规则",
                        "应用中.. ($i/${it.count()})",
                        it.count(),
                        i
                    )
                    // notify if reached the end
                    if (i == it.count()) {
                        Utils.notifyProgress(context, "应用国内分流规则完成", "应用完成", it.count(), index)
                        Utils.updateLogs("inserting china rule sets took: ${(System.currentTimeMillis() - start) / 1000}s")
                    }
                }
            }
        }
        // process part
        if (useTproxy) {
            // tproxy init
            Tproxy.tproxy.forEach {
                terminal.exec(it)
            }
            terminal.exec(Tproxy.checkSetup)
        } else {
            // redirect init
            terminal.exec("iptables -t nat -A $chain -p tcp -j REDIRECT --to-ports 12345")
        }
        initialized = true
        Log.d(TAG, "init: done")
    }

    class Tproxy {
        companion object {

            val tproxy = setOf(
                "iptables -t mangle -A $chain -p tcp -j TPROXY --on-ip 127.0.0.1 --on-port 12345 --tproxy-mark 1",
                "iptables -t mangle -A $chain -p udp -j TPROXY --on-ip 127.0.0.1 --on-port 12345 --tproxy-mark 1"
            )
            val setup = arrayOf(
                "ip rule add fwmark 1 table 100",
                "ip route add local 0.0.0.0/0 dev lo table 100"
            )
            const val checkSetup = "ip rule list|grep \"lookup 100\"||echo \"fail\"\n"
            const val setupTproxyMark = "iptables -t mangle -A OUTPUT -p tcp -j MARK --set-mark 1"
        }
    }
}
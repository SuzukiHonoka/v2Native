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
}
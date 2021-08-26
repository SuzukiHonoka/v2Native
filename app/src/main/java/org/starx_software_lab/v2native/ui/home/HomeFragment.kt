package org.starx_software_lab.v2native.ui.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.service.Background
import org.starx_software_lab.v2native.util.Utils
import java.io.InputStreamReader

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    companion object {
        const val TAG = "Home"
    }

    private lateinit var receiver: Receiver
    private lateinit var button: Button
    private lateinit var v: View
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private var allow: Boolean? = null

    var running = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (Utils.checkEXP()) {
            Toast.makeText(requireContext(), "已过期", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
        v = inflater.inflate(R.layout.fragment_home, container, false).also {
            it.findViewById<FloatingActionButton>(R.id.tab).setOnClickListener(this)
            it.findViewById<Button>(R.id.load).setOnClickListener(this)
            button = it.findViewById(R.id.start)
            button.setOnClickListener(this)
            button.setOnLongClickListener(this)
        }
        resultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val contentResolver = v.context.contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(result.data!!.data!!, takeFlags)
                    Thread {
                        result.data.also {
                            val ips = contentResolver.openInputStream(it!!.data!!)
                            val reader = InputStreamReader(ips)
                            val s = reader.readText()
                            reader.close()
                            val obj = JsonParser.parseString(s).asJsonObject
                            obj.remove("inbounds")
                            val socks = JsonArray().apply {
                                val setting = JsonObject().apply {
                                    addProperty("listen", "127.0.0.1")
                                    addProperty("port", 10808)
                                    addProperty("protocol", "socks")
                                    addProperty("tag", "socks")
                                }
                                add(setting)
                            }
                            obj.add("inbounds", socks)
                            Log.d(TAG, "formattedJson: $obj")
                            Utils.writeConfig(v.context, obj.toString())
                            Handler(v.context.mainLooper).post {
                                Snackbar.make(v, "应用成功!", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                }
            }
        regBroadcastReceiver()
        checkLife(false)
        Thread {
            allow = Utils.checkRoot()
        }.start()
        return v
    }

    private fun regBroadcastReceiver() {
        receiver = Receiver()
        receiver.setContext(this)
        v.context.registerReceiver(
            receiver,
            IntentFilter("org.starx_software_lab.v2native.ui.home")
        )
    }

    override fun onDestroy() {
        v.context.unregisterReceiver(receiver)
        super.onDestroy()
    }

    class Receiver : BroadcastReceiver() {
        lateinit var ui: HomeFragment

        fun setContext(ui: HomeFragment){
            this.ui = ui
        }

        override fun onReceive(p0: Context?, p1: Intent) {
            if (p1.getBooleanExtra("kill", false)) {
                ui.running = false
                ui.button.text = ui.getString(R.string.stop)
                ui.updateStatus()
                Toast.makeText(ui.context, "Service Stopped", Toast.LENGTH_SHORT).show()
                return
            }
            Log.d("UI", "onReceive: pong!!")
            ui.running = true
            Toast.makeText(ui.context, "Service Running", Toast.LENGTH_SHORT).show()
            ui.updateStatus()
        }
    }

    private fun updateStatus() {
        if (running) {
            button.text = getString(R.string.stop)
            return
        }
        button.text = getString(R.string.start)
    }

    override fun onClick(p0: View) {
        when (p0.id) {
            R.id.start -> {
                if (allow == null || allow == false ) {
                    Snackbar.make(v, "请检查超级用户权限", Snackbar.LENGTH_SHORT).show()
                    return
                }
                val intent = Intent(context, Background::class.java)
                if (running) {
                    Thread {
                        v.context.applicationContext.apply {
                            stopService(intent)
                        }
                    }.start()
                    running = false
                    button.text = getString(R.string.start)
                    Snackbar.make(v, "已停止服务", Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (!Utils.checkConfig(p0.context)) {
                    Snackbar.make(v, "请传入配置文件后启动", Snackbar.LENGTH_SHORT).show()
                    return
                }
                v.context.applicationContext.apply {
                    Utils.getServerIP(v.context).also {
                        if (it.isNullOrEmpty()) {
                            Snackbar.make(v, "读取远程服务器IP失败", Snackbar.LENGTH_SHORT).show()
                            return
                        }
                        intent.putExtra("ip", it)
                    }
                    startForegroundService(intent)
                }
                running = true
                button.text = getString(R.string.stop)
                Snackbar.make(v, "已启动服务", Snackbar.LENGTH_SHORT).show()
            }
            R.id.tab -> {
                checkLife(false)
            }
            R.id.load -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                resultLauncher.launch(intent)
            }
        }
    }


    private fun checkLife(stop: Boolean) {
        Intent().also {
            it.action = "org.starx_software_lab.v2native.service.receiver"
            if (stop) {
                it.putExtra("clean", true)
            }
            v.context.sendBroadcast(it)
            Log.d("TAG", "onCreateView: send broadcast")
        }
    }

    override fun onResume() {
        checkLife(false)
        super.onResume()
    }

    override fun onLongClick(p0: View): Boolean {
        return when (p0.id) {
            R.id.start -> {
                checkLife(true)
                true
            }
            else -> false
        }
    }
}
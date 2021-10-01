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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.service.Background
import org.starx_software_lab.v2native.util.Utils
import java.io.File
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
    private var autoCheck = true

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
        val homeViewModel: HomeViewModel by activityViewModels()
        homeViewModel.getAllow().observe(viewLifecycleOwner, {
            allow = it
        })
        v = inflater.inflate(R.layout.fragment_home, container, false).also {
            it.findViewById<FloatingActionButton>(R.id.tab).setOnClickListener(this)
            setOf(R.id.load, R.id.check).forEach { b ->
                it.findViewById<Button>(b).setOnClickListener(this)
            }
            button = it.findViewById(R.id.start)
            button.setOnClickListener(this)
            button.setOnLongClickListener(this)
        }
        resultLauncher = regResultLauncher()
        regBroadcastReceiver()
        checkLife(false)
        return v
    }

    private fun regResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                        if (Utils.reWriteConfig(requireContext(), s)) {
                            Handler(v.context.mainLooper).post {
                                Snackbar.make(v, "应用成功", Snackbar.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                        Handler(v.context.mainLooper).post {
                            Snackbar.make(v, "配置错误", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        }
    }

    private fun regBroadcastReceiver() {
        receiver = Receiver()
        v.context.registerReceiver(
            receiver,
            IntentFilter("org.starx_software_lab.v2native.ui.home")
        )
    }

    override fun onDestroy() {
        v.context.unregisterReceiver(receiver)
        super.onDestroy()
    }

    inner class Receiver : BroadcastReceiver() {

        override fun onReceive(p0: Context?, p1: Intent) {
            Log.d("UI", "onReceive: pong!!")
            running = p1.getBooleanExtra("running", false)
            button.text = if (running) getString(R.string.stop) else getString(R.string.start)
            button.isEnabled = true
            autoCheck = true
            if (running) {
                Toast.makeText(context, "Service Running", Toast.LENGTH_SHORT).show()
                return
            }
            Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onClick(p0: View) {
        when (p0.id) {
            R.id.start -> {
                if (allow == null || allow == false) {
                    Snackbar.make(v, "请检查超级用户权限", Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (!Utils.checkConfig()) {
                    Snackbar.make(v, "请传入配置文件后启动", Snackbar.LENGTH_SHORT).show()
                    return
                }
                autoCheck = false
                button.isEnabled = false
                Thread {
                    if (running) {
                        v.context.applicationContext.apply {
                            stopService(Intent(context, Background::class.java))
                        }
                        return@Thread
                    }
                    v.context.applicationContext.also {
                        if (!Utils.serviceAgent(v.context)) {
                            Snackbar.make(v, "无法启动服务", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
            R.id.tab -> {
                if (!autoCheck) {
                    Toast.makeText(v.context, "等待服务进程结束", Toast.LENGTH_SHORT).show()
                    return
                }
                checkLife(false)
            }
            R.id.load -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                resultLauncher.launch(intent)
            }
            R.id.check -> {
                if (!Utils.checkConfig()) {
                    Snackbar.make(p0, "配置文件错误或不存在", Snackbar.LENGTH_SHORT).show()
                    return
                }
                val view = LayoutInflater.from(context).inflate(R.layout.log_view, null)
                view.findViewById<TextView>(R.id.log_msg).text =
                    Utils.prettifyJson(File(Utils.configPath).readText())
                AlertDialog.Builder(requireContext()).apply {
                    setPositiveButton("OK", null)
                    setView(view)
                    create()
                    show()
                }
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
        if (autoCheck) checkLife(false)
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
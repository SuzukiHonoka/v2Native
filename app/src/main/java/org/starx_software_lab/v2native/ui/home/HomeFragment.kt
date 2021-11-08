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
import org.starx_software_lab.v2native.util.Config
import org.starx_software_lab.v2native.util.Utils
import org.starx_software_lab.v2native.widget.CustomizedCard
import java.io.File
import java.io.InputStreamReader

class HomeFragment : Fragment() {

    companion object {
        const val TAG = "Home"
    }

    private lateinit var receiver: Receiver
    private val blocks: Array<CustomizedCard?> = Array(2) { null }
    private lateinit var v: View
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    // flags
    private var hasRoot = false
    private var hasConfig = Config.checkConfig()


    private var running = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (Utils.checkEXP()) {
            Toast.makeText(requireContext(), "已过期", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
        boundVM()
        v = boundWidgets(inflater, container!!)
        resultLauncher = regResultLauncher()
        regBroadcastReceiver()
        checkLife(false)
        return v
    }

    private fun boundVM() {
        val homeViewModel: HomeViewModel by activityViewModels()
        homeViewModel.getRootStatus().observe(viewLifecycleOwner, {
            hasRoot = it
        })
    }

    private fun switchServiceStatus(status: Boolean) {
        if (status) {
            blocks[0]!!.apply {
                setIcon(requireContext().getDrawable(R.drawable.ic_md_check_circle)!!)
                setBackgroundColor(requireContext().getColor(R.color.green))
                setBody(
                    getString(R.string.service_currently_running),
                    requireContext().getColor(R.color.white)
                )
            }
            return
        }
        blocks[0]!!.apply {
            setIcon(requireContext().getDrawable(R.drawable.ic_md_warning)!!)
            setBackgroundColor(requireContext().getColor(R.color.orange))
            setBody(
                getString(R.string.service_currently_not_running),
                requireContext().getColor(R.color.white)
            )
        }
    }

    fun switchConfigStatus(status: Boolean) {
        if (status) {
            blocks[1]!!.apply {
                setBody(
                    getString(R.string.config_currently_imported),
                    requireContext().getColor(R.color.green)
                )
            }
            return
        }
        blocks[1]!!.apply {
            setBody(
                getString(R.string.config_not_currently_imported),
                requireContext().getColor(R.color.red)
            )
        }
    }

    private fun boundWidgets(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.fragment_home, container, false).also {
            it.findViewById<FloatingActionButton>(R.id.tab).setOnClickListener {
                checkLife(false)
            }
            blocks[0] = it.findViewById(R.id.switch_service)
            blocks[0]!!.apply {
                switchServiceStatus(false)
                setCaption(
                    getString(R.string.v2native_switch),
                    requireContext().getColor(R.color.white)
                )
                setOnClickListener { switchService() }
                setOnLongClickListener {
                    checkLife(true)
                    return@setOnLongClickListener true
                }
            }
            blocks[1] = it.findViewById(R.id.import_config)
            blocks[1]!!.apply {
                setIcon(requireContext().getDrawable(R.drawable.ic_md_build)!!)
                setCaption(getString(R.string.import_config), null)
                switchConfigStatus(hasConfig)
                setOnClickListener { startConfigChooser() }
                setOnLongClickListener {
                    showConfigToAlertDialog()
                    return@setOnLongClickListener true
                }
            }
        }
    }

    private fun switchService() {
        if (!hasRoot) {
            Snackbar.make(v, "请检查超级用户权限", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (!Config.checkConfig()) {
            Snackbar.make(v, "请传入配置文件后启动", Snackbar.LENGTH_SHORT).show()
            return
        }
        Thread {
            if (running) {
                v.context.applicationContext.apply {
                    stopService(Intent(context, Background::class.java))
                    running = false
                }
                return@Thread
            }
            v.context.applicationContext.also {
                if (!Utils.serviceAgent(v.context)) {
                    Snackbar.make(v, "无法启动服务", Snackbar.LENGTH_SHORT).show()
                    return@Thread
                }
                running = true
            }
        }.start()
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
                        if (Config.reWriteConfig(requireContext(), s)) {
                            Handler(v.context.mainLooper).post {
                                hasConfig = true
                                switchConfigStatus(true)
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

    private fun startConfigChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        resultLauncher.launch(intent)
    }

    private fun showConfigToAlertDialog() {
        if (!hasConfig) return
        val view = LayoutInflater.from(context).inflate(R.layout.log_view, null)
        view.findViewById<TextView>(R.id.log_msg).text =
            Utils.prettifyJson(File(Config.configPath).readText())
        AlertDialog.Builder(requireContext()).apply {
            setPositiveButton("OK", null)
            setView(view)
            create()
            show()
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
            switchServiceStatus(running)
            if (running) {
                switchServiceStatus(true)
                Toast.makeText(context, "Service Running", Toast.LENGTH_SHORT).show()
                return
            }
            switchServiceStatus(false)
            Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT).show()
        }
    }


    private fun checkLife(stop: Boolean) {
        Intent().also {
            it.action = "org.starx_software_lab.v2native.service.receiver"
            if (stop) {
                it.putExtra("clean", true)
            }
            v.context.sendBroadcast(it)
            Log.d(TAG, "checkLife: send broadcast")
        }
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        if (running) checkLife(false)
        super.onResume()
    }
}
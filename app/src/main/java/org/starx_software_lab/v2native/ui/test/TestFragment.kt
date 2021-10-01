package org.starx_software_lab.v2native.ui.test

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.util.Utils
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived

class TestFragment : Fragment(), View.OnClickListener {

    private lateinit var root: View
    private lateinit var testViewModel: TestViewModel
    private lateinit var terminal: Exec
    private lateinit var reply: EditText
    private lateinit var ip: TextView
    private lateinit var delay: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        root = inflater.inflate(R.layout.fragment_test, container, false)
        init()
        return root
    }

    private fun init() {
        val m: TestViewModel by activityViewModels()
        testViewModel = m
        reply = root.findViewById(R.id.terminal)
        ip = root.findViewById(R.id.network_ip)
        delay = root.findViewById(R.id.network_delay)
        testViewModel.getText().observe(viewLifecycleOwner, {
            reply.setText(it)
        })
        testViewModel.getTerminal().observe(viewLifecycleOwner, {
            terminal = it
            it.apply {
                setListener(object : IDataReceived {
                    override fun onFailed() {
                        reply.setText(getString(R.string.failed))
                    }

                    override fun onData(data: String) {
                        if (view == null) return
                        Handler(view!!.context.mainLooper).post {
                            testViewModel.updateText(data)
                            reply.setText(testViewModel.getText().value)
                        }
                    }
                })
            }
        })
        setOf(R.id.execute, R.id.clear, R.id.test).forEach {
            root.findViewById<Button>(it).setOnClickListener(this)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.execute -> {
                if (!terminal.state) {
                    Toast.makeText(context, "终端不可用", Toast.LENGTH_SHORT).show()
                }
                terminal.exec(reply.text.toString())
                reply.setText("")
                testViewModel.setText("")
            }
            R.id.clear -> {
                testViewModel.setText("")
                reply.setText("")
            }
            R.id.test -> {
                Thread {
                    var tmpIP = ""
                    var tmpDelay: Long = 0
                    Utils.retrieveContent("https://ip.starx.ink/").also {
                        if (it.isEmpty()) {
                            Handler(Looper.getMainLooper()).post {
                                Snackbar.make(v, "IP API ERROR", Snackbar.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                        tmpIP = it
                    }
                    val start = System.currentTimeMillis()
                    Utils.retrieveContent("https://www.google.com/generate_204").also {
                        if (it.isEmpty() || it != "204") {
                            Handler(Looper.getMainLooper()).post {
                                Snackbar.make(v, "GOOGLE API ERROR", Snackbar.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                        tmpDelay = System.currentTimeMillis().minus(start)
                    }
                    Handler(Looper.getMainLooper()).post {
                        ip.text = tmpIP
                        delay.text = "${tmpDelay}ms"
                        Snackbar.make(v, "测试结束", Snackbar.LENGTH_SHORT).show()
                    }
                }.start()
                Snackbar.make(v, "已启动测试线程", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
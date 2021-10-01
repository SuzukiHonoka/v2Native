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

class TestFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        lateinit var terminal: Exec
        val testViewModel: TestViewModel by activityViewModels()
        val root = inflater.inflate(R.layout.fragment_test, container, false)
        val textView: EditText = root.findViewById(R.id.terminal)

        testViewModel.getText().observe(viewLifecycleOwner, {
            textView.setText(it)
        })
        testViewModel.getTerminal().observe(viewLifecycleOwner, {
            terminal = it
            it.apply {
                setListener(object : IDataReceived {
                    override fun onFailed() {
                        textView.setText(getString(R.string.failed))
                    }

                    override fun onData(data: String) {
                        if (view == null) return
                        Handler(view!!.context.mainLooper).post {
                            testViewModel.updateText(data)
                            textView.setText(testViewModel.getText().value)
                        }
                    }
                })
            }
        })
        val ip = root.findViewById<TextView>(R.id.network_ip)
        val delay = root.findViewById<TextView>(R.id.network_delay)
        root.findViewById<Button>(R.id.test).setOnClickListener { v ->
            Snackbar.make(v, "已启动测试线程..", Snackbar.LENGTH_SHORT).show()
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
                }
            }.start()
        }
        root.findViewById<Button>(R.id.execute).setOnClickListener {
            if (!terminal.state) {
                Toast.makeText(context, "终端不可用", Toast.LENGTH_SHORT).show()
            }
            terminal.exec(textView.text.toString())
            textView.setText("")
            testViewModel.setText("")
        }
        root.findViewById<Button>(R.id.clear).setOnClickListener {
            textView.setText("")
            testViewModel.setText("")
        }
        return root
    }
}
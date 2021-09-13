package org.starx_software_lab.v2native.ui.test

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.starx_software_lab.v2native.R
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
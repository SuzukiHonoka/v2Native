package org.starx_software_lab.v2native.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.util.Iptables

class LogFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_log, container, false)
        val textView: TextView = root.findViewById(R.id.log)
        textView.text = Iptables.logs
        root.findViewById<Button>(R.id.refresh).setOnClickListener {
            val lines = Iptables.logs.split("\n")
            val start = if (lines.size > 300) lines.size - 300 else 0
            val reduced = lines.subList(start, lines.size)
            textView.text = reduced.joinToString("\n")
        }
        root.findViewById<Button>(R.id.clear).setOnClickListener {
            textView.text = ""
            Iptables.logs = ""
        }
        return root
    }
}
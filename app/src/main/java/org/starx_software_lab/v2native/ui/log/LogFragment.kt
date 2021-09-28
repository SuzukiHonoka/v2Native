package org.starx_software_lab.v2native.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.util.Utils

class LogFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_log, container, false)
        val textView: TextView = root.findViewById(R.id.log)
        textView.text = Utils.Iptables.logs
        root.findViewById<Button>(R.id.refresh).setOnClickListener {
            textView.text = Utils.Iptables.logs
        }
        root.findViewById<Button>(R.id.clear).setOnClickListener {
            textView.text = ""
            Utils.Iptables.logs = ""
        }
        return root
    }
}
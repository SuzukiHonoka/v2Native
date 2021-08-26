package org.starx_software_lab.v2native.ui.gallery

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.starx_software_lab.v2native.R
import org.starx_software_lab.v2native.util.exec.Exec
import org.starx_software_lab.v2native.util.exec.IDataReceived

class GalleryFragment : Fragment() {

    private lateinit var galleryViewModel: GalleryViewModel
    private var terminal: Exec? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        galleryViewModel =
            ViewModelProvider(this).get(GalleryViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_gallery, container, false)
        val textView: EditText = root.findViewById(R.id.terminal)

        galleryViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.setText(it)
        })
        galleryViewModel.terminal.observe(viewLifecycleOwner, Observer {
            terminal = it
        })

        if (terminal == null) terminal = galleryViewModel.terminal.value

        terminal!!.setListener(object : IDataReceived {
            override fun onFailed() {
                textView.setText("failed")
            }

            override fun onData(data: String) {
                Handler(view!!.context.mainLooper).post {
                    textView.setText(textView.text.toString() + data)
                }
            }

        })

        root.findViewById<Button>(R.id.execute).setOnClickListener {
            terminal!!.exec(textView.text.toString())
            textView.setText("")
        }
        root.findViewById<Button>(R.id.clear).setOnClickListener {
            textView.setText("")
        }
        return root
    }
}
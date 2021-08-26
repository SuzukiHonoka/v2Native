package org.starx_software_lab.v2native.ui.gallery

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.starx_software_lab.v2native.util.exec.Exec

class GalleryViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = ""
    }
    private val _terminal = MutableLiveData<Exec>().apply {
        Log.d("TAG", "model: init")
        value = Exec()
    }
    val text: LiveData<String> = _text
    val terminal: LiveData<Exec> = _terminal
}
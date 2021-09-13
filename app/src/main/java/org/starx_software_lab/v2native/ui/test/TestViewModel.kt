package org.starx_software_lab.v2native.ui.test

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.starx_software_lab.v2native.util.exec.Exec

class TestViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = ""
    }

    private val _terminal = MutableLiveData<Exec>().apply {
        value = Exec()
    }

    fun getTerminal(): MutableLiveData<Exec> {
        return _terminal
    }

    fun getText(): MutableLiveData<String> {
        return _text
    }

    fun setText(msg: String) {
        _text.value = msg
    }

    fun updateText(msg: String) {
        _text.value += "$msg\n"
    }
}
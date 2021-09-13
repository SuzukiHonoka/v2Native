package org.starx_software_lab.v2native.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.starx_software_lab.v2native.util.Utils

class HomeViewModel : ViewModel() {

    private val _allow = MutableLiveData<Boolean>().apply {
        value = Utils.checkRoot()
    }

    fun getAllow(): MutableLiveData<Boolean> {
        return _allow
    }
}
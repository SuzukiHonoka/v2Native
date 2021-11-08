package org.starx_software_lab.v2native.util

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.starx_software_lab.v2native.R

class Fragment(private val main: FragmentActivity) {

    fun getCurrentFragment(): Fragment {
        return main.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!.childFragmentManager.fragments[0]
    }
}
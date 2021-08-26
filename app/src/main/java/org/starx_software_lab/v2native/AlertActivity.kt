package org.starx_software_lab.v2native

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class AlertActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(savedInstanceState!!.getString("title"))
            .setMessage(savedInstanceState.getString("msg"))
            .setPositiveButton("OK", null)
            .setOnCancelListener {
                it.cancel()
                finish()
            }
    }
}
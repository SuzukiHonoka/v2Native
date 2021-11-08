package org.starx_software_lab.v2native

import android.annotation.SuppressLint
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonParser
import org.starx_software_lab.v2native.service.Background
import org.starx_software_lab.v2native.ui.home.HomeFragment
import org.starx_software_lab.v2native.ui.settings.SettingsActivity
import org.starx_software_lab.v2native.util.Config
import org.starx_software_lab.v2native.util.Fragment
import org.starx_software_lab.v2native.util.Utils


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "Main"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var back: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.updateConfigPath(applicationContext)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_test, R.id.nav_logs
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_quick -> {
                doQuickImport()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_disable_battery_optimization -> {
                disableBatteryOptimization()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_exit -> {
                finish()
                true
            }
            else -> false
        }
    }

    private fun doQuickImport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || !clipboard.primaryClipDescription!!.hasMimeType(
                MIMETYPE_TEXT_PLAIN
            )
        ) {
            Toast.makeText(applicationContext, "剪贴板数据无效", Toast.LENGTH_SHORT).show()
            return
        }
        val data = clipboard.primaryClip!!.getItemAt(0).text.toString().trim()
        try {
            JsonParser.parseString(data)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "不是有效的JSON格式!!", Toast.LENGTH_SHORT).show()
            return
        }
        if (Config.reWriteConfig(applicationContext, data)) {
            Toast.makeText(applicationContext, "导入成功!", Toast.LENGTH_SHORT).show()
        }
        // refresh ui
        val current = Fragment(this).getCurrentFragment()
        if (current is HomeFragment) {
            current.switchConfigStatus(true)
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage("A tool made by @starx")
            .create()
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun disableBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent().also { intent ->
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            return
        }
        Snackbar.make(
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!.childFragmentManager.fragments[0].requireView(),
            "当前已优化",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        Fragment(this).getCurrentFragment()
            .also {
                if (it is HomeFragment) {
                    val now = System.currentTimeMillis()
                    if (back != null && now - back!! <= 3000) {
                        super.onBackPressed()
                    }
                    back = now
                    Snackbar.make(it.requireView(), "再按一次退出~", Snackbar.LENGTH_SHORT).show()
                    return
                }
            }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (Utils.getPerfBool(this, "autoStop", false)) {
            stopService(Intent(this, Background::class.java))
            Log.d(TAG, "onDestroy: Background stopped")
        }
        super.onDestroy()
    }
}
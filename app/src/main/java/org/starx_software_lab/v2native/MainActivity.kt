package org.starx_software_lab.v2native

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import org.starx_software_lab.v2native.service.Background
import org.starx_software_lab.v2native.ui.home.HomeFragment
import org.starx_software_lab.v2native.ui.settings.SettingsActivity
import org.starx_software_lab.v2native.util.Utils

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG = "Main"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var back: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

//        val fab: FloatingActionButton = findViewById(R.id.tab)
//        fab.setOnClickListener { view ->
//            Snackbar.make(view, Utils.preloads.toString(), Snackbar.LENGTH_LONG)
//                    .setAction("Action", null).show()
//        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
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
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("About")
                    .setMessage("A tool made by @starx")
                    .create()
                    .show()
                true
            }
            else -> false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {

        supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.childFragmentManager?.fragments?.get(
            0
        )
            .also {
                Log.d("onBackPressed", "onBackPressed: " + it?.javaClass?.name)
                if (it is HomeFragment) {
                    val now = System.currentTimeMillis()
                    if (back != null && now - back!! <= 3000) {
                        super.onBackPressed()
                    }
                    back = now
                    Snackbar.make(window.decorView, "再按一次退出~", Snackbar.LENGTH_SHORT).show()
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
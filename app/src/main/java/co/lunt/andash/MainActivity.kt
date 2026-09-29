package co.lunt.andash

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import co.lunt.andash.ui.AndashTheme
import co.lunt.andash.ui.DashboardRoot

class MainActivity : ComponentActivity() {
    private val vm: DashboardViewModel by viewModels()

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }

    private val timeChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.READ_CALENDAR)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            AndashTheme {
                DashboardRoot(vm) { permission.launch(Manifest.permission.READ_CALENDAR) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        vm.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        unregisterReceiver(timeChanged)
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

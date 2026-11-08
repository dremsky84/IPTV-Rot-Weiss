package de.remsky.rotweiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import de.remsky.rotweiss.ui.AppColors
import de.remsky.rotweiss.ui.AppViewModel
import de.remsky.rotweiss.ui.LoginScreen
import de.remsky.rotweiss.ui.MainScreen
import de.remsky.rotweiss.ui.TvMode
import de.remsky.rotweiss.ui.TvTheme

class MainActivity : ComponentActivity() {

    private lateinit var vm: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvMode.detect(this)
        vm = ViewModelProvider(this)[AppViewModel::class.java]
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                TvTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (vm.loggedIn) MainScreen(vm) else LoginScreen(vm)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fortschritt aus dem Player übernehmen ("Weiterschauen")
        if (::vm.isInitialized) vm.refreshHistory()
    }
}

package me.smartproxy.ui

import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.smartproxy.core.LocalVpnService
import me.smartproxy.core.ProxyConfig
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.databinding.ActivityMainBinding
import org.koin.java.KoinJavaComponent.get

open class MainActivity : RequestVpnPermissionActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PAC_CONFIG_URL_KEY = "PAC_CONFIG_URL_KEY"
    }

    private val vpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    private val viewBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        lifecycleScope.launch {
            vpnViewModel.vpnPermissionRequestResult.collectLatest {
                if (it == RESULT_OK) {
                    val configUrl = readConfigUrl()

                    viewBinding.textViewLog.text = ""

                    ProxyConfig.ConfigUrl = configUrl

                    startService(Intent(applicationContext, LocalVpnService::class.java))
                } else {
                    Toast.makeText(this@MainActivity, "你拒绝了VPN开启", Toast.LENGTH_SHORT).show()
                    viewBinding.proxySwitch.isChecked = false
                }
            }
        }

        viewBinding.textViewLog.text = ""
        viewBinding.scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN)

        viewBinding.proxySwitch.isChecked = vpnViewModel.isRunning()
        viewBinding.proxySwitch.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (vpnViewModel.isRunning()) {
                if (isChecked) {
                    // do nothing
                } else {
                    stopService(Intent(this, LocalVpnService::class.java))
                }
            } else {
                if (isChecked) {
                    requestVpnPermission()
                } else {
                    // do nothing
                }
            }
        }
    }

    private fun readConfigUrl(): String {
        val preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE)
        return preferences.getString(PAC_CONFIG_URL_KEY, "") ?: ""
    }

    private fun setConfigUrl(configUrl: String) {
        val preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString(PAC_CONFIG_URL_KEY, configUrl)
        editor.apply()
    }
}

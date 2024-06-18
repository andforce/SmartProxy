package me.smartproxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.smartproxy.core.LocalVpnService
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.databinding.ActivityMainBinding
import me.smartproxy.ui.utils.Logger
import org.koin.java.KoinJavaComponent.get

open class MainActivity : RequestVpnPermissionActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val vpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN)


        lifecycleScope.launch {
            vpnViewModel.vpnPermissionRequestResult.collectLatest {
                if (it == RESULT_OK) {
                    binding.textViewLog.text = "权限获取成功"
                    startService(Intent(applicationContext, LocalVpnService::class.java))
                } else {
                    Toast.makeText(this@MainActivity, "你拒绝了VPN开启", Toast.LENGTH_SHORT).show()
                    binding.proxySwitch.isChecked = false
                }
            }
        }

        lifecycleScope.launch {
            vpnViewModel.vpnStatusStateFlow.collectLatest {
                Logger.d(TAG, "vpnStatusStateFlow: $it")
                withContext(Dispatchers.Main) {
                    binding.proxySwitch.isChecked = it == 1
                }
            }
        }

        lifecycleScope.launch {
            vpnViewModel.vpnStatusSharedFlow.collectLatest {
                Logger.d(TAG, "vpnStatusSharedFlow: $it")
                withContext(Dispatchers.Main) {
                    binding.textViewLog.text = "${binding.textViewLog.text}\r\n$it"
                }
            }
        }

        binding.proxySwitch.setOnClickListener {
            val checkbox: CompoundButton = it as CompoundButton
            val isChecked = checkbox.isChecked
            Logger.d(TAG, "proxySwitch, setOnClickListener: ${checkbox.isChecked}, vpnViewModel.isRunning(): ${vpnViewModel.isRunning()}")

            if (isChecked) {
                if (!vpnViewModel.isRunning()) {
                    requestVpnPermission()
                }
            } else {
                if (vpnViewModel.isRunning()) {
                    vpnViewModel.tryStop()
                }
            }
        }
    }
}

package me.smartproxy.ui

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import org.koin.java.KoinJavaComponent

open class RequestVpnPermissionActivity : AppCompatActivity() {
    private val vpnViewModel =
        KoinJavaComponent.get<LocalVpnViewModel>(LocalVpnViewModel::class.java)


    private var launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vpnViewModel.updateRequestResult(it.resultCode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        launcher?.unregister()
    }

    fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // 需要重新申请权限
            launcher?.launch(intent)
        } else {
            // 已经有权限
            vpnViewModel.updateRequestResult(Activity.RESULT_OK)
        }
    }
}
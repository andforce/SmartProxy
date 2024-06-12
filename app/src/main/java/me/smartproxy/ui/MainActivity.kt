package me.smartproxy.ui

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import me.smartproxy.R
import me.smartproxy.core.LocalVpnService
import me.smartproxy.core.ProxyConfig
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.databinding.ActivityMainBinding
import me.smartproxy.ui.utils.UrlUtils.isValidUrl
import org.koin.java.KoinJavaComponent.get

open class MainActivity : AppCompatActivity() {
    private val localVpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    private val viewBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        viewBinding.configUrlLayout.setOnClickListener { v: View? ->
            if (viewBinding.proxySwitch.isChecked) {
                return@setOnClickListener
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.config_url)
                .setItems(
                    arrayOf<CharSequence>(
                        getString(R.string.config_url_manual)
                    )
                ) { _: DialogInterface?, i: Int ->
                    if (i == 0) {
                        showConfigUrlInputDialog()
                    }
                }
                .show()
        }

        val configUrl = readConfigUrl()
        if (TextUtils.isEmpty(configUrl)) {
            viewBinding.textViewConfigUrl.text = getString(R.string.config_not_set_value)
        } else {
            viewBinding.textViewConfigUrl.text = configUrl
        }

        viewBinding.textViewLog.text = GL_HISTORY_LOGS
        viewBinding.scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN)

        viewBinding.proxySwitch.isChecked = localVpnViewModel.isRunning()
        viewBinding.proxySwitch.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (localVpnViewModel.isRunning() != isChecked) {
                viewBinding.proxySwitch.isEnabled = false
                if (isChecked) {
                    val intent: Intent? = VpnService.prepare(this@MainActivity)
                    if (intent == null) {
                        startVPNService()
                    } else {
                        startActivityForResult(intent, START_VPN_SERVICE_REQUEST_CODE)
                    }
                }
            }
        }
    }

    fun readConfigUrl(): String? {
        val preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE)
        return preferences.getString(CONFIG_URL_KEY, "")
    }

    fun setConfigUrl(configUrl: String?) {
        val preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString(CONFIG_URL_KEY, configUrl)
        editor.apply()
    }


    private fun showConfigUrlInputDialog() {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
        editText.hint = getString(R.string.config_url_hint)
        editText.setText(readConfigUrl())

        AlertDialog.Builder(this)
            .setTitle(R.string.config_url)
            .setView(editText)
            .setPositiveButton(R.string.btn_ok) { dialog: DialogInterface?, which: Int ->
                if (editText.text == null) {
                    return@setPositiveButton
                }
                val configUrl = editText.text.toString().trim { it <= ' ' }
                if (isValidUrl(configUrl)) {
                    setConfigUrl(configUrl)
                    viewBinding.textViewConfigUrl.text = configUrl
                } else {
                    Toast.makeText(this@MainActivity, R.string.err_invalid_url, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }


    private fun startVPNService() {
        val configUrl = readConfigUrl()
        if (!isValidUrl(configUrl)) {
            Toast.makeText(this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show()
            viewBinding.proxySwitch.post {
                viewBinding.proxySwitch.isChecked = false
                viewBinding.proxySwitch.isEnabled = true
            }
            return
        }

        viewBinding.textViewLog.text = ""
        GL_HISTORY_LOGS = null
        ProxyConfig.ConfigUrl = configUrl
        startService(Intent(this, LocalVpnService::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == START_VPN_SERVICE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVPNService()
            } else {
                viewBinding.proxySwitch.post {
                    viewBinding.proxySwitch.isChecked = false
                    viewBinding.proxySwitch.isEnabled = true
                }
            }
        }
    }

    companion object {
        private var GL_HISTORY_LOGS: String? = null

        private val TAG: String = MainActivity::class.java.simpleName

        private const val CONFIG_URL_KEY = "CONFIG_URL_KEY"

        private const val START_VPN_SERVICE_REQUEST_CODE = 1985
    }
}

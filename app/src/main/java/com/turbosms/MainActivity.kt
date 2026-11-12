package com.turbosms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turbosms.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceAdapter
    private val devices = mutableListOf<DeviceInfo>()

    /** switch-এর listener ট্রিগার না করে প্রোগ্রাম থেকে চেক বদলানোর জন্য */
    private var syncingSwitch = false

    // সার্ভার চালাতে আবশ্যক (নোটিফিকেশন ঐচ্ছিক — না দিলেও সার্ভার চলে)
    private val essentialPermissions: List<String> by lazy {
        buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(Manifest.permission.READ_PHONE_NUMBERS)
        }
    }

    private val optionalPermissions: List<String> by lazy {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val deniedEssential = essentialPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (deniedEssential.isEmpty()) {
            Toast.makeText(this, "Permissions granted — dual SIM ready", Toast.LENGTH_SHORT).show()
            checkBatteryOptimization()
        } else {
            val names = deniedEssential.joinToString("\n") { "• " + it.substringAfterLast('.') }
            MaterialAlertDialogBuilder(this)
                .setTitle("Permissions required")
                .setMessage("These permissions were denied:\n$names\n\nSMS sending and SIM selection need them.")
                .setPositiveButton("Open settings") { _, _ -> openAppSettings() }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SmsServerService.ACTION_STATUS_UPDATE -> {
                    val running = intent.getBooleanExtra("isRunning", false)
                    val port = intent.getIntExtra("port", 9000)
                    intent.getStringExtra("error")?.let {
                        Toast.makeText(this@MainActivity, "Server error: $it", Toast.LENGTH_LONG).show()
                    }
                    updateServerStatus(running, port)
                }
                SmsServerService.ACTION_DEVICE_UPDATE -> {
                    val list = intent.getParcelableArrayListExtra<DeviceInfo>("devices")
                    list?.let { updateDeviceList(it) }
                }
                SmsServerService.ACTION_SMS_STATS_UPDATE -> {
                    updateSmsStats(
                        intent.getIntExtra("sent", 0),
                        intent.getIntExtra("failed", 0),
                        intent.getIntExtra("pending", 0)
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        registerReceivers()
        requestMissingPermissions(showRationale = true)
        refreshFromService()
    }

    override fun onResume() {
        super.onResume()
        refreshFromService()
    }

    private fun prefs() = getSharedPreferences("TurboSMS", MODE_PRIVATE)

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        deviceAdapter = DeviceAdapter(devices)
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }

        binding.etPort.setText(prefs().getInt("port", 9000).toString())

        binding.switchServer.setOnCheckedChangeListener { _, isChecked ->
            if (syncingSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                if (hasEssentialPermissions()) {
                    if (!startServer()) setSwitch(false)
                } else {
                    setSwitch(false)
                    Toast.makeText(this, "Please grant the required permissions first", Toast.LENGTH_SHORT).show()
                    requestMissingPermissions(showRationale = true)
                }
            } else {
                stopServer()
            }
        }

        // PIN
        binding.tvPin.text = PinStore.get(this)
        binding.btnCopyPin.setOnClickListener { copyToClipboard("Turbo SMS PIN", PinStore.get(this)) }
        binding.btnNewPin.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Generate a new PIN?")
                .setMessage("All browsers that are logged in will be signed out.")
                .setPositiveButton("Generate") { _, _ ->
                    binding.tvPin.text = PinStore.regenerate(this)
                    SmsServerService.instance?.onPinChanged()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // URL
        binding.btnCopyUrl.setOnClickListener { copyToClipboard("Turbo SMS URL", binding.tvServerUrl.text.toString()) }
        binding.btnOpenUrl.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(binding.tvServerUrl.text.toString())))
            } catch (e: Exception) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_permissions -> { requestMissingPermissions(showRationale = false, force = true); true }
                R.id.action_battery -> { checkBatteryOptimization(force = true); true }
                R.id.action_about -> { showAbout(); true }
                else -> false
            }
        }
        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, R.color.white))
    }

    private fun setSwitch(checked: Boolean) {
        if (binding.switchServer.isChecked == checked) return
        syncingSwitch = true
        binding.switchServer.isChecked = checked
        syncingSwitch = false
    }

    private fun refreshFromService() {
        val running = SmsServerService.isRunning
        updateServerStatus(running, if (running) prefs().getInt("port", 9000) else 0)
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    // ── Permissions ──────────────────────────────────────────────────────

    private fun hasEssentialPermissions() = essentialPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMissingPermissions(showRationale: Boolean, force: Boolean = false) {
        val missing = (essentialPermissions + optionalPermissions).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            if (force) Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
            checkBatteryOptimization()
            return
        }
        if (!showRationale) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions required")
            .setMessage(
                "• SMS — to send text messages\n" +
                "• Phone state — to detect and choose between SIM cards\n" +
                "• Notifications — to keep the server visible in the background"
            )
            .setPositiveButton("Grant") { _, _ -> permissionLauncher.launch(missing.toTypedArray()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun checkBatteryOptimization(force: Boolean = false) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            if (force) Toast.makeText(this, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
            return
        }
        if (!force && prefs().getBoolean("battery_asked", false)) return
        prefs().edit().putBoolean("battery_asked", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("Keep the server alive")
            .setMessage("Disable battery optimization so Android doesn't stop the server in the background.")
            .setPositiveButton("Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME)
            .setMessage(
                getString(R.string.developer_name) + "\n" +
                getString(R.string.developer_address) + "\n" +
                getString(R.string.developer_email) + "\n" +
                getString(R.string.developer_phone)
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Server ───────────────────────────────────────────────────────────

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(SmsServerService.ACTION_STATUS_UPDATE)
            addAction(SmsServerService.ACTION_DEVICE_UPDATE)
            addAction(SmsServerService.ACTION_SMS_STATS_UPDATE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, filter)
    }

    private fun startServer(): Boolean {
        val port = binding.etPort.text.toString().trim().toIntOrNull() ?: 9000
        if (port !in 1024..65535) {
            binding.etPort.error = "Use 1024 – 65535"
            return false
        }
        binding.etPort.error = null
        prefs().edit().putInt("port", port).apply()

        val intent = Intent(this, SmsServerService::class.java).apply {
            action = SmsServerService.ACTION_START
            putExtra("port", port)
        }
        ContextCompat.startForegroundService(this, intent)
        return true
    }

    private fun stopServer() {
        startService(Intent(this, SmsServerService::class.java).apply {
            action = SmsServerService.ACTION_STOP
        })
        updateServerStatus(false, 0)
    }

    private fun updateServerStatus(isRunning: Boolean, port: Int) {
        setSwitch(isRunning)
        binding.apply {
            val okColor = ContextCompat.getColor(this@MainActivity, R.color.server_on)
            val offColor = ContextCompat.getColor(this@MainActivity, R.color.server_off)
            if (isRunning) {
                tvServerStatus.text = "Running on port $port"
                tvServerStatus.setTextColor(okColor)
                tvHeaderStatus.text = "ONLINE"
                viewStatusDot.backgroundTintList = ColorStateList.valueOf(0xFF4ADE80.toInt())
                layoutServerUrl.visibility = View.VISIBLE
                tvUrlHint.visibility = View.VISIBLE
                tvServerUrl.text = "http://${getLocalIpAddress()}:$port"
                etPort.isEnabled = false
            } else {
                tvServerStatus.text = getString(R.string.server_stopped)
                tvServerStatus.setTextColor(offColor)
                tvHeaderStatus.text = "OFFLINE"
                viewStatusDot.backgroundTintList = ColorStateList.valueOf(0xFFCBD5E1.toInt())
                layoutServerUrl.visibility = View.GONE
                tvUrlHint.visibility = View.GONE
                etPort.isEnabled = true
            }
        }
    }

    private fun updateDeviceList(deviceList: List<DeviceInfo>) {
        devices.clear()
        devices.addAll(deviceList.sortedByDescending { it.lastAccess })
        deviceAdapter.notifyDataSetChanged()

        binding.tvDeviceCount.text = devices.size.toString()
        binding.tvNoDevices.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateSmsStats(sent: Int, failed: Int, pending: Int) {
        binding.tvTotalSent.text = sent.toString()
        binding.tvTotalFailed.text = failed.toString()
        binding.tvTotalPending.text = pending.toString()
    }

    /** ওয়াই-ফাই/হটস্পট/ইথারনেট ইন্টারফেসকে অগ্রাধিকার; মোবাইল ডেটা (rmnet, ccmni) শেষে */
    private fun getLocalIpAddress(): String {
        try {
            val candidates = mutableListOf<Pair<Int, String>>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "0.0.0.0"
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                val score = when {
                    name.startsWith("wlan") || name.startsWith("swlan") -> 0
                    name.startsWith("ap") || name.startsWith("softap") -> 1
                    name.startsWith("eth") -> 2
                    name.startsWith("rndis") -> 3
                    else -> 9
                }
                for (addr in ni.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        candidates.add(score to (addr.hostAddress ?: continue))
                    }
                }
            }
            return candidates.minByOrNull { it.first }?.second ?: "0.0.0.0"
        } catch (e: Exception) {
            return "0.0.0.0"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }
}

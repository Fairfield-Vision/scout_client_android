/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.DataSetObserver
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityHomeBtBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.vnc.VncClient
import java.util.SortedMap
import java.util.TreeMap

/**
 * Primary activity of the app.
 *
 * It Provides access to saved and discovered servers.
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBtBinding

    var tvStatus: TextView? = null
    var lvDevices: ListView? = null

    val BT_PERMISSION_REQUEST = 10

    var devices: SortedMap<String, BluetoothDevice> = TreeMap()

    var adapter: DevAdapter? = null

    class ScanThread() : Thread() {
        private var done = false

        var tvStatus: TextView? = null

        var bluetooth: BluetoothAdapter? = null

        fun stop_scanning(){
            this.done = true
        }

        @SuppressLint("MissingPermission")
        override fun run(){
            while (! this.done){
                Log.i("ScanThread", "Scanning")
                this.tvStatus?.post{ this.tvStatus?.setText(R.string.status_scanning) }
                Thread.sleep(3000)
            }
        }
    }

    class DevAdapter() : ListAdapter {

        public var context: Context? = null
        public var devices: SortedMap<String, BluetoothDevice>? = null
        private var observers: HashSet<DataSetObserver> = HashSet()

        override fun registerDataSetObserver(o: DataSetObserver?) {
            if (o!=null) {
                observers.add(o)
            }
        }

        override fun unregisterDataSetObserver(o: DataSetObserver?) {
            observers.remove(o)
        }

        fun update(){
            observers.forEach({o->o.onChanged()})
        }

        override fun getCount(): Int {
            if (devices != null){
                return devices!!.size
            }
            return 0
        }

        override fun getItem(i: Int): BluetoothDevice? {
            val dev_id = devices?.keys?.elementAt(i)
            val dev = devices?.get(dev_id)
            return dev
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun hasStableIds(): Boolean { return false }

        @SuppressLint("MissingPermission")
        override fun getView(i: Int, oldview: View?, parent: ViewGroup?): View {
            val dev_id = devices?.keys?.elementAt(i)
            val dev = devices?.get(dev_id)
            val tv = TextView(context)
            val name = dev?.name
            val addr = dev?.address
            tv.setText("$name ($addr)")
            //tv.height *= 3
            // TODO this is a hack
            // this should inherit colours from parent views somehow
            tv.setTextColor(Color.argb(255,255,255,255))
            tv.setBackgroundColor(0)
            tv.setPadding(1,20,1,20)
            return tv
        }

        override fun getItemViewType(p0: Int): Int { return 0 }
        override fun getViewTypeCount(): Int { return 1 }
        override fun isEmpty(): Boolean { return this.count == 0 }
        override fun areAllItemsEnabled(): Boolean { return true }
        override fun isEnabled(p0: Int): Boolean { return true }

    }

    private val scanThread = ScanThread()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.App_Theme)
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        //View Inflation
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home_bt)
        binding.lifecycleOwner = this

        tvStatus = findViewById(R.id.tvStatus)
        lvDevices = findViewById(R.id.lvDevices)
        adapter = DevAdapter()
        adapter?.context = applicationContext
        adapter?.devices = devices
        lvDevices!!.adapter = adapter
        lvDevices!!.setOnItemClickListener { adapterView, view, i, l ->
            val addr = devices.keys.elementAt(i)
            val dev = devices[addr]
            Log.d("Scout", "Connect to device: ${dev?.name}, $addr")
            connect_to_scout(addr)
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
    }

    private fun connect_to_scout(addr: String) {

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BT_PERMISSION_REQUEST

            -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                     grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d("Scout", "Bluetooth permission has been granted")
                    val bluetoothManager: BluetoothManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        getSystemService(BluetoothManager::class.java)
                    } else {
                        this.tvStatus?.setText(R.string.status_no_bluetooth)
                        TODO("API level too low, no android 5 support for this bluetooth API???")
                    }
                    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.getAdapter()
                    if (bluetoothAdapter == null) {
                        // Device doesn't support Bluetooth
                        this.tvStatus?.setText(R.string.status_no_bluetooth)
                    }
                    else {
                        this.tvStatus?.setText(R.string.status_waiting)
                        scan_bt_devices(bluetoothAdapter)
                    }
                } else {
                    Log.d("Scout", "Bluetooth permission has been denied")
                    this.tvStatus?.setText(R.string.status_no_bt_permission)
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val bluetoothManager: BluetoothManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(BluetoothManager::class.java)
        } else {
            this.tvStatus?.setText(R.string.status_no_bluetooth)
            Log.d("Scout", "API level is less than 23. No support for this bluetooth API")
            TODO("API level too low, no android 5 support for this bluetooth API???")
        }
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.d("Scout", "Adapter is null, likely the device does not have bluetooth hardware")
            this.tvStatus?.setText(R.string.status_no_bluetooth)
        } else {
            if (bluetoothAdapter?.isEnabled == false) {
                Log.d("Scout", "Bluetooth is disabled")
                this.tvStatus?.setText(R.string.status_bluetooth_disabled)
            } else {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Log.d("Scout", "API level >= 31, get bluetooth BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions")
                    if (
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        this.tvStatus?.setText(R.string.status_no_bt_permission)
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ), BT_PERMISSION_REQUEST
                        )
                    }
                    else{
                        this.tvStatus?.setText(R.string.status_waiting)
                        scan_bt_devices(bluetoothAdapter)
                    }
                } else {
                    Log.d("Scout", "API level < 31, get BLUETOOTH and BLUETOOTH_ADMIN permissions")
                    if (
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH
                        ) != PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_ADMIN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        this.tvStatus?.setText(R.string.status_no_bt_permission)
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                            ), BT_PERMISSION_REQUEST
                        )
                    }
                    else{
                        this.tvStatus?.setText(R.string.status_waiting)
                        scan_bt_devices(bluetoothAdapter)
                    }
                }
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun acknowledge_device(dev: BluetoothDevice){
        Log.d("Scout", "Acknowledge device ${dev.name} (${dev.address})")
        val addr = dev.address
        // TODO for some reason the UUID for the serial port protocol doesn't show up
        /*dev.uuids?.forEach { uuid ->
            Log.d("Scout", "UUID: $uuid")
            if (uuid.equals(ParcelUuid.fromString("00001101-0000-1000-8000-00805f9b34fb"))) {
                devices[addr] = dev
                Log.d("Scout", "This devices supports SPP, could be the scout")
            }
        }
         */
        // for now just assume every devices is a scout, to avoid missing any
        devices[addr] = dev
        adapter!!.update()
        this.tvStatus?.post {
            this.tvStatus?.setText(R.string.status_scanning_ready)
        }
    }


    @SuppressLint("MissingPermission")
    private fun scan_bt_devices(bluetoothAdapter: BluetoothAdapter) {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        if (pairedDevices?.size == 0) {
            Log.d("Scout", "No existing paired devices found")
        } else {
            Log.d("Scout", "Paired devices...")
        }
        pairedDevices?.forEach { device ->
            acknowledge_device(device)
        }
        if (bluetoothAdapter.startDiscovery()) {
            this.tvStatus?.setText(R.string.status_scanning)
        }
        else{
            this.tvStatus?.setText(R.string.status_scan_error)
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action.toString()
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        device.fetchUuidsWithSdp()
                        acknowledge_device(device)
                    }
                }
                BluetoothDevice.ACTION_UUID -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        acknowledge_device(device)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        scanThread.stop_scanning()
    }

    private fun startNewConnection(profile: ServerProfile) {
        if (checkNativeLib())
            startVncActivity(this, profile)
    }

    /**
     * Warns about missing native library.
     * This can happen if AVNC is installed by copying APK from a device with different architecture.
     */
    private fun checkNativeLib(): Boolean {
        return runCatching {
            VncClient.loadLibrary()
        }.onFailure {
            val msg = "This is a bug in the application. Please contact support."
            MsgDialog.show(supportFragmentManager, "Native library is missing!", msg)
        }.isSuccess
    }
}

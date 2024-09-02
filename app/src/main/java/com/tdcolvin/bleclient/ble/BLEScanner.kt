package com.tdcolvin.bleclient.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.pow

// These fields are marked as API >= 31 in the Manifest class, so we can't use those without warning.
// So we create our own, which prevents over-suppression of the Linter
const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

data class DeviceInfo(
    val device: BluetoothDevice,
    val distance: Double
)

class BLEScanner(context: Context) {

    private val bluetooth = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager
        ?: throw Exception("Bluetooth is not supported by this device")

    val isScanning = MutableStateFlow(false)

    val foundDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())

    private val scanner: BluetoothLeScanner
        get() = bluetooth.adapter.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result ?: return

            val deviceName = result.device.name
            // 이름이 없는 디바이스는 리스트에 추가하지 않음
            if (deviceName != null && deviceName.isNotEmpty()) {
                val distance = calculateDistance(result.rssi, result.txPower)
                val deviceInfo = DeviceInfo(result.device, distance)
                if (!foundDevices.value.any { it.device.address == result.device.address }) {
                    foundDevices.update { it + deviceInfo }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results ?: return

            val newDevices = results.mapNotNull { result ->
                val deviceName = result.device.name
                // 이름이 없는 디바이스는 제외
                if (deviceName != null && deviceName.isNotEmpty()) {
                    val distance = calculateDistance(result.rssi, result.txPower)
                    DeviceInfo(result.device, distance)
                } else {
                    null
                }
            }
                .filterNot { foundDevices.value.any { info -> info.device.address == it.device.address } }

            if (newDevices.isNotEmpty()) {
                foundDevices.update { it + newDevices }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning.value = false
        }
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_SCAN)
    fun startScanning() {
        scanner.startScan(scanCallback)
        isScanning.value = true
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_SCAN)
    fun stopScanning() {
        scanner.stopScan(scanCallback)
        isScanning.value = false
    }

    // RSSI와 txPower를 이용하여 대략적인 거리를 계산하는 함수
    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (txPower == Integer.MIN_VALUE) {
            // txPower 값이 없는 경우 RSSI만으로는 정확한 계산이 불가능하여 일단 기본 거리를 반환
            return -1.0
        }
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            (0.89976 * ratio.pow(7.7095) + 0.111).pow(10.0)
        }
    }
}

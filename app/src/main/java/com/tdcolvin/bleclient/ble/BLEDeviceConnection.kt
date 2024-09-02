package com.tdcolvin.bleclient.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.tdcolvin.bleclient.UwbControllerCommunicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.util.UUID

val CTF_SERVICE_UUID: UUID = UUID.fromString("8c380000-10bd-4fdb-ba21-1922d6cf860d")
val PASSWORD_CHARACTERISTIC_UUID: UUID = UUID.fromString("8c380001-10bd-4fdb-ba21-1922d6cf860d")
val NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("8c380002-10bd-4fdb-ba21-1922d6cf860d")

@Suppress("DEPRECATION")
class BLEDeviceConnection @RequiresPermission("PERMISSION_BLUETOOTH_CONNECT") constructor(
    private val context: Context,
    private val bluetoothDevice: DeviceInfo
) {
    val isConnected = MutableStateFlow(false)
    val controleeRead = MutableStateFlow<String?>(null)
    val successfulNameWrites = MutableStateFlow(0)
    val services = MutableStateFlow<List<BluetoothGattService>>(emptyList())

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val connected = newState == BluetoothGatt.STATE_CONNECTED
            if (connected) {
                //read the list of services
                services.value = gatt.services
            }
            isConnected.value = connected
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            services.value = gatt.services
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (characteristic.uuid == PASSWORD_CHARACTERISTIC_UUID) {
                controleeRead.value = String(characteristic.value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (characteristic.uuid == NAME_CHARACTERISTIC_UUID) {
                successfulNameWrites.update { it + 1 }
            }
        }
    }

    private var gatt: BluetoothGatt? = null
    private val uwbCommunicator = UwbControllerCommunicator(context)

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun connect() {
        gatt = bluetoothDevice.device.connectGatt(context, false, callback)
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun discoverServices() {
        gatt?.discoverServices()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun readPassword() {
        val service = gatt?.getService(CTF_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(PASSWORD_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            val success = gatt?.readCharacteristic(characteristic)
            Log.v("bluetooth", "Read status: $success")
        }
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun writeName() {
        val service = gatt?.getService(CTF_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(NAME_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            // UWB Controller의 주소와 채널 정보를 가져옵니다.
            val uwbAddress = uwbCommunicator.getUwbAddress()
            val uwbChannel = uwbCommunicator.getUwbChannel()

            // 주소와 채널 정보를 합쳐 characteristic.value에 설정합니다.
            val dataToSend = "$uwbAddress/$uwbChannel"
            characteristic.value = dataToSend.toByteArray()

            val success = gatt?.writeCharacteristic(characteristic)
            Log.v("bluetooth", "Write status: $success")

            val latestControleeRead = controleeRead.value
            Log.d("uwb", "controlee: $latestControleeRead")
            if (latestControleeRead != null) {
                uwbCommunicator.startCommunication(latestControleeRead)
            } else {
                Log.d("uwb", "controleeRead is null, cannot start communication")
            }
        }
    }
}

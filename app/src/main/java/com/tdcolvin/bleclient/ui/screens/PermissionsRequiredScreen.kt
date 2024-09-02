package com.tdcolvin.bleclient.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

val ALL_BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.UWB_RANGING
    )
} else {
    arrayOf(
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}

fun haveAllPermissions(context: Context): Boolean {
    // UWB_RANGING permission check only for API 31+
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ALL_BLE_PERMISSIONS
    } else {
        ALL_BLE_PERMISSIONS.filter { it != Manifest.permission.UWB_RANGING }.toTypedArray()
    }

    return permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}

@Composable
fun PermissionsRequiredScreen(onPermissionGranted: () -> Unit) {
    Box {
        Column(
            modifier = Modifier.align(Alignment.Center)
        ) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { granted ->
                if (granted.values.all { it }) {
                    onPermissionGranted()
                }
            }

            Button(onClick = {
                // UWB_RANGING permission request only for API 31+
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ALL_BLE_PERMISSIONS
                } else {
                    ALL_BLE_PERMISSIONS.filter { it != Manifest.permission.UWB_RANGING }
                        .toTypedArray()
                }
                launcher.launch(permissions)
            }) {
                Text("Grant Permission")
            }
        }
    }
}

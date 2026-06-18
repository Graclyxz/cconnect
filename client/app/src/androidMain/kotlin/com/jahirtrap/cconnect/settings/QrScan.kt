package com.jahirtrap.cconnect.settings

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.jahirtrap.cconnect.appContext

actual object QrScan {
    actual fun isAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    actual fun scan(onResult: (String?) -> Unit) {
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(appContext, options).startScan()
                .addOnSuccessListener { onResult(it.rawValue) }
                .addOnFailureListener { onResult(null) }
                .addOnCanceledListener { onResult(null) }
        }.onFailure { onResult(null) }
    }
}

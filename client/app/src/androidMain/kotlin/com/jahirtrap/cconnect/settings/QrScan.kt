package com.jahirtrap.cconnect.settings

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.jahirtrap.cconnect.appContext
import com.jahirtrap.cconnect.currentActivity

actual object QrScan {
    actual fun isAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    actual fun scan(onResult: (String?) -> Unit) {
        val activity = currentActivity ?: run { onResult(null); return }
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = GmsBarcodeScanning.getClient(activity, options)
            fun launch() {
                scanner.startScan()
                    .addOnSuccessListener { onResult(it.rawValue) }
                    .addOnCanceledListener { onResult(null) }
                    .addOnFailureListener { onResult(null) }
            }
            val moduleInstall = ModuleInstall.getClient(activity)
            moduleInstall.areModulesAvailable(scanner)
                .addOnSuccessListener { resp ->
                    if (resp.areModulesAvailable()) {
                        launch()
                    } else {
                        moduleInstall.installModules(
                            ModuleInstallRequest.newBuilder().addApi(scanner).build()
                        )
                            .addOnSuccessListener { launch() }
                            .addOnFailureListener { onResult(null) }
                    }
                }
                .addOnFailureListener { launch() }
        }.onFailure { onResult(null) }
    }
}

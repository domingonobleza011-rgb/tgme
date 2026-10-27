package com.tgme.timekeeping.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tgme.timekeeping.TgmeApp
import com.tgme.timekeeping.logic.recordScan
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.Executors

@Composable
fun ScannerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    var message by remember { mutableStateOf("Point the camera at an employee QR code.") }
    var ok by remember { mutableStateOf(true) }
    val scans by TgmeApp.db.attendance().scansForDate(LocalDate.now().toString()).collectAsState(emptyList())

    // Debounce: ignore the same code for 3 seconds.
    var lastToken by remember { mutableStateOf("") }
    var lastAt by remember { mutableStateOf(0L) }

    Column(Modifier.padding(12.dp)) {
        if (!granted) {
            Text("Camera permission is needed to scan QR codes.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera access") }
        } else {
            CameraPreview(Modifier.fillMaxWidth().height(280.dp)) { token ->
                val now = System.currentTimeMillis()
                if (token == lastToken && now - lastAt < 3000) return@CameraPreview
                lastToken = token; lastAt = now
                scope.launch {
                    val r = recordScan(TgmeApp.db, token)
                    ok = r.ok; message = r.message
                }
            }
        }
        Text(message, Modifier.padding(vertical = 10.dp),
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium)
        Text("Today's scans", style = MaterialTheme.typography.titleSmall)
        val events = scans.flatMap { s ->
            buildList {
                s.timeIn?.let { add(Triple("${s.lastName}, ${s.firstName}", "Time In", it)) }
                s.timeOut?.let { add(Triple("${s.lastName}, ${s.firstName}", "Time Out", it)) }
            }
        }.sortedByDescending { it.third }.take(20)
        LazyColumn {
            items(events) { (n, type, t) -> Text("${timeLabel(t)}  •  $type  •  $n", Modifier.padding(vertical = 3.dp)) }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraPreview(modifier: Modifier, onToken: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

    AndroidView(modifier = modifier, factory = { c ->
        val view = PreviewView(c)
        val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(img)
                    .addOnSuccessListener { codes ->
                        codes.firstOrNull()?.rawValue?.let { v ->
                            ContextCompat.getMainExecutor(c).execute { onToken(v) }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(c))
        view
    })
}

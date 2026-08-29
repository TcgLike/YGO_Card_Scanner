package com.ygocardscanner.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ygocardscanner.data.scanner.ScanCandidate
import com.ygocardscanner.data.scanner.ScanMatchResult
import com.ygocardscanner.data.scanner.ScannerMode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CardScannerScreen(viewModel: ScannerViewModel, onBack: () -> Unit, onManualAdd: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var capture by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.events.collect { if (it is ScannerEvent.AddedSingleCard) onAdded() } }
    LaunchedEffect(granted) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (state.isBulkPhotoMode) "Bulk photo" else "Live scan") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }, actions = { TextButton(onClick = { viewModel.setMode(if (state.isBulkPhotoMode) ScannerMode.LIVE else ScannerMode.BULK_PHOTO) }) { Text(if (state.isBulkPhotoMode) "Live" else "Bulk photo") } }) }) { padding ->
        if (!granted) { Permission(onRequest = { launcher.launch(Manifest.permission.CAMERA) }, onManualAdd); return@Scaffold }
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraPreview(!state.isBulkPhotoMode, viewModel::onRecognizedText, viewModel::onBulkPhotoRecognized) { capture = it }
            Status(state, { capture?.invoke() }, viewModel::selectCandidate, viewModel::confirmSelected, viewModel::dismissMatch, viewModel::undoLastBulkAdd)
        }
    }
}
@Composable private fun Permission(onRequest: () -> Unit, onManual: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("Camera access is used only for local OCR."); Button(onClick = onRequest) { Text("Allow camera") }; TextButton(onClick = onManual) { Text("Add manually instead") } } }
@android.annotation.SuppressLint("UnsafeOptInUsageError")
@Composable private fun CameraPreview(live: Boolean, onText: (String) -> Unit, onBlocks: (List<com.ygocardscanner.data.scanner.OcrTextBlock>) -> Unit, onReady: ((() -> Unit) -> Unit)) {
 val context=LocalContext.current; val owner=LocalLifecycleOwner.current; val view=remember { PreviewView(context) }; DisposableEffect(owner,view,live) { val recognizer=MlKitLatinTextRecognizer(); val executor=Executors.newSingleThreadExecutor(); val busy=AtomicBoolean(false); val future=ProcessCameraProvider.getInstance(context); future.addListener({ val provider=future.get(); val preview=Preview.Builder().build().also { it.surfaceProvider=view.surfaceProvider }; val capture=ImageCapture.Builder().build(); val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { a -> a.setAnalyzer(executor) { proxy -> if(!live) proxy.close() else analyzeImageProxy(proxy,recognizer,busy,onText) } }; provider.unbindAll(); if(live) provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis,capture) else provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture); onReady { capture.takePicture(executor, object: ImageCapture.OnImageCapturedCallback(){ override fun onCaptureSuccess(proxy: androidx.camera.core.ImageProxy){ val image=proxy.image; if(image==null) proxy.close() else recognizer.recognizeBlocks(image,proxy.imageInfo.rotationDegrees,onBlocks){ proxy.close() } } }) } },ContextCompat.getMainExecutor(context)); onDispose { runCatching { future.get().unbindAll() }; recognizer.close(); executor.shutdown() } }; AndroidView({view},Modifier.fillMaxSize()) }
@ExperimentalGetImage private fun analyzeImageProxy(proxy: androidx.camera.core.ImageProxy, recognizer: MlKitLatinTextRecognizer, busy: AtomicBoolean, onText:(String)->Unit){ val image=proxy.image; if(image==null||!busy.compareAndSet(false,true)) proxy.close() else recognizer.recognize(image,proxy.imageInfo.rotationDegrees,onText,{}, {busy.set(false);proxy.close()}) }
@Composable private fun BoxScope.Status(state: ScannerUiState, capture:()->Unit, select:(ScanCandidate)->Unit, confirm:()->Unit, dismiss:()->Unit, undo:()->Unit){ Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface.copy(alpha=.94f)).padding(16.dp)){ if(state.isBulkPhotoMode){ Text("Photo queue: ${state.queueRemaining} remaining; ${state.acceptedCount} added"); Button(onClick=capture,enabled=!state.isProcessingPhoto&&!state.isSaving){Text(if(state.isProcessingPhoto)"Reading photo..." else "Take bulk photo")}; state.lastAcceptedEntryId?.let { TextButton(onClick=undo){Text("Undo last add")} } }; Text(state.message); state.errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}; state.match?.let{ Review(it,state.selectedCandidate,select,confirm,dismiss,state.isSaving,state.isBulkPhotoMode) } } }
@Composable private fun Review(match:ScanMatchResult.Candidates, selected:ScanCandidate?, select:(ScanCandidate)->Unit, confirm:()->Unit, dismiss:()->Unit, saving:Boolean, bulk:Boolean){ Card(Modifier.fillMaxWidth().padding(top=8.dp)){Column(Modifier.padding(12.dp)){Text(if(match.isAmbiguous)"Choose a match" else "Local match"); match.candidates.forEach{c->OutlinedButton(onClick={select(c)},modifier=Modifier.fillMaxWidth()){Text(c.printing.displayName+" · "+c.printing.setCode)}}; Row(Modifier.padding(top=8.dp)){Button(onClick=confirm,enabled=selected!=null&&!saving){Text(if(saving)"Adding..." else "Confirm and add")}; TextButton(onClick=dismiss,enabled=!saving){Text(if(bulk)"Skip" else "Scan again")}}}}}
private fun Context.hasCameraPermission()=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
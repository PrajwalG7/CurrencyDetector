package com.example.currencydetector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.currencydetector.ui.theme.CurrencydetectorTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setContent {
                CurrencydetectorTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            CameraPreview()
                            CameraButton()
                        }
                    }
                }
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        }
    }

    @Composable
    fun CameraPreview() {
        val context = remember { this }
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider, view)
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider, view: PreviewView) {
        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview
            )

            // Set flash mode if available
            val cameraControl = camera.cameraControl
            if (camera.cameraInfo.hasFlashUnit()) {
                cameraControl.enableTorch(true) // Set to true for auto flash; false to turn off
            }
        } catch (exc: Exception) {
            // Handle exception
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        CurrencydetectorTheme {
            CameraButton()
        }
    }

    @Composable
    fun CameraButton(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter // Align button to the bottom center
        ) {
            Button(
                onClick = { /* TODO: Handle camera click */ },
                modifier = Modifier
                    .size(120.dp) // Adjust size as needed
                    .padding(0.dp), // Padding for the button
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black // Green background color
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera), // Replace with your camera icon
                    contentDescription = "Camera Icon",
                    modifier = Modifier
                        .size(120.dp),
                    tint = Color.White // Change icon color if needed for camera icon
                )
                Spacer(modifier = Modifier.size(4.dp)) // Space between icon and text
//                Text(
//                    text = "",
//                    color = Color.Black // White text
//                )
            }
        }
    }
}

package com.example.currencydetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.currencydetector.ui.theme.CurrencydetectorTheme
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var outputDirectory: File
    // Define output buffer with the correct shape
    private val outputBuffer = Array(1) { FloatArray(6) } // 6 to match model output

    // State to hold the captured image
    private var capturedImageBitmap by mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        outputDirectory = getOutputDirectory()

        initTextToSpeech()
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
//                            capturedImageBitmap?.let {
//                                DisplayCapturedImage(it)
//                            }
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

        imageCapture = ImageCapture.Builder().build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (exc: Exception) {
            // Handle exception
        }
    }

    @Composable
    fun CameraButton(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = {
                    takePhoto() // Capture photo on button click
                },
                modifier = Modifier
                    .size(180.dp)
                    .padding(0.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera), // Replace with your camera icon
                    contentDescription = "Camera Icon",
                    modifier = Modifier.size(180.dp),
                    tint = Color.White
                )
            }
        }
    }

    private fun takePhoto() {
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    // Handle the error
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Here the image is saved, pass it to the TensorFlow Lite model for classification
                    classifyImage(photoFile)
                }
            }
        )
    }

    private fun classifyImage(photoFile: File) {
        // Decode the image file and ensure the bitmap is in ARGB_8888 format
        val bitmap = BitmapFactory.decodeFile(photoFile.path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })

        // Check if bitmap is null
        if (bitmap == null) {
            Log.d("ImageProcessing", "Failed to decode image")
            return
        }

        // Store the captured image bitmap for displaying
        capturedImageBitmap = bitmap

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true) // Resize for model input

        // Load the TensorFlow Lite model
        val tfliteModel = FileUtil.loadMappedFile(this, "model_unquant.tflite")
        val interpreter = Interpreter(tfliteModel)

        // Preprocess the image to get input data
        val input = preprocessImage(resizedBitmap)
        Log.d("input is", input.toString())

        // Run inference
        interpreter.run(input, outputBuffer)

        // Find the index of the max value in the output buffer
        val predictedIndex = outputBuffer[0].indices.maxByOrNull { outputBuffer[0][it] } ?: -1

        // Assuming you have a list of labels corresponding to your output
        val labels = applicationContext.assets.open("labels.txt").bufferedReader().readLines()

        // Check if the index is within the bounds of the labels list
        if (predictedIndex in labels.indices) {
            val predictedLabel = labels[predictedIndex]
            Log.d(predictedLabel, predictedLabel)
            Log.d("input is", input.toString())
            speak(predictedLabel)
        } else {
            speak("Unknown currency")
        }
    }

    @Composable
    fun DisplayCapturedImage(bitmap: Bitmap) {
        Image(
            bitmap = bitmap.asImageBitmap(), // Convert Bitmap to ImageBitmap
            contentDescription = "Captured Image",
            modifier = Modifier
                .fillMaxWidth() // Set to full width
                .height(500.dp) // Set height to half of the screen height
                .padding(0.dp) // Adjust padding as needed
                .rotate(90F)

        )
    }

    // Preprocess the image for TensorFlow Lite model input
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * 1 * 224 * 224 * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val pixelValue = intValues[pixel++]
                inputBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f) // Red
                inputBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)  // Green
                inputBuffer.putFloat((pixelValue and 0xFF) / 255.0f) // Blue
            }
        }
        return inputBuffer
    }

    // Text to speech initialization
    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}



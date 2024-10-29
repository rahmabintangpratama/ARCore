package com.uinsuka.arcore

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.Config
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.math.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var sceneView: ArSceneView
    private var currentModelNode: ArModelNode? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Mapping FAB IDs to model resources
    private val modelMap = mapOf(
        R.id.fab_pc to R.raw.pc_component,
        R.id.fab_cpu to R.raw.cpu_component,
        R.id.fab_gpu to R.raw.gpu_component,
        R.id.fab_ram to R.raw.ram_component,
        R.id.fab_ssd to R.raw.ssd_component,
        R.id.fab_motherboard to R.raw.motherboard_component,
        R.id.fab_cooler to R.raw.cooler_component,
        R.id.fab_psu to R.raw.psu_component
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sceneView = findViewById(R.id.sceneView)

        sceneView.apply {
            lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            planeRenderer.isEnabled = true
        }

        setupFabListeners()
        setupTapListener()
        setupCameraButton()
    }

    private fun setupFabListeners() {
        modelMap.keys.forEach { fabId ->
            findViewById<FloatingActionButton>(fabId).setOnClickListener {
                loadModel(modelMap[fabId] ?: return@setOnClickListener)
            }
        }
    }

    private fun loadModel(modelResourceId: Int) {
        // Remove current model if exists
        currentModelNode?.let { node ->
            if (sceneView.children.contains(node)) {
                sceneView.removeChild(node)
            }
            node.destroy()
        }

        // Create and load new model
        currentModelNode = ArModelNode(sceneView.engine).apply {
            coroutineScope.launch {
                try {
                    loadModelGlb(
                        context = this@MainActivity,
                        glbFileLocation = getRawResourcePath(modelResourceId),
                        autoAnimate = true,
                        scaleToUnits = 0.5f,
                        centerOrigin = Position(x = 0f, y = 0f, z = 0f)
                    )
                    Log.d("AR_DEBUG", "Model loaded successfully: $modelResourceId")
                } catch (e: Exception) {
                    Log.e("AR_DEBUG", "Error loading model: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getRawResourcePath(resourceId: Int): String {
        return "android.resource://${packageName}/${resourceId}"
    }

    private fun placeModel(node: ArModelNode, hitPosition: Position) {
        node.position = hitPosition
        if (!sceneView.children.contains(node)) {
            sceneView.addChild(node)
        }
        node.isVisible = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapListener() {
        // Custom ArSceneView with required constructors
        class CustomArSceneView : ArSceneView {
            constructor(context: Context) : super(context)
            constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
            constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }

        sceneView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sceneView.performClick()
                    handleTap(event.x, event.y)
                    true
                }
                else -> false
            }
        }

        sceneView.setOnClickListener {
            Log.d("AR_DEBUG", "Click detected on ArSceneView")
        }
    }

    private fun setupCameraButton() {
        findViewById<ImageButton>(R.id.btnCamera).setOnClickListener {
            if (checkPermissions()) {
                takeScreenshot()
            } else {
                requestPermissions()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takeScreenshot()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera diperlukan untuk mengambil gambar",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun takeScreenshot() {
        try {
            sceneView.getBitmap { bitmap ->
                if (bitmap != null) {
                    // Simpan bitmap
                    saveBitmap(bitmap)
                    // Tampilkan toast success
                    Toast.makeText(this, "Gambar berhasil disimpan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal mengambil gambar", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot: ${e.message}")
            Toast.makeText(this, "Gagal mengambil gambar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "AR_Screenshot_$timestamp.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageInQ(bitmap, filename)
        } else {
            saveImageInLegacy(bitmap, filename)
        }
    }

    @Suppress("NAME_SHADOWING")
    private fun saveImageInQ(bitmap: Bitmap, filename: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
    }

    private fun saveImageInLegacy(bitmap: Bitmap, filename: String) {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val image = File(imagesDir, filename)
        FileOutputStream(image).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
    }

    private fun handleTap(x: Float, y: Float): Boolean {
        return try {
            val hitResult = sceneView.hitTest(
                xPx = x,
                yPx = y,
                plane = true,
                depth = true,
                instant = true
            )

            hitResult?.let { hit ->
                currentModelNode?.let { node ->
                    val hitPosition = Position(
                        x = hit.createAnchor().pose.tx(),
                        y = hit.createAnchor().pose.ty(),
                        z = hit.createAnchor().pose.tz()
                    )
                    placeModel(node, hitPosition)
                    node.anchor()
                    Log.d("AR_DEBUG", "Model placed at position: $hitPosition")
                    true
                }
            } ?: false
        } catch (e: Exception) {
            Log.e("AR_DEBUG", "Error handling tap: ${e.message}")
            false
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            currentModelNode?.let { node ->
                if (!sceneView.children.contains(node)) {
                    sceneView.addChild(node)
                }
            }
        } catch (e: Exception) {
            Log.e("AR_DEBUG", "Error on resume: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            currentModelNode?.let { node ->
                if (sceneView.children.contains(node)) {
                    sceneView.removeChild(node)
                }
            }
        } catch (e: Exception) {
            Log.e("AR_DEBUG", "Error on pause: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            currentModelNode?.destroy()
        } catch (e: Exception) {
            Log.e("AR_DEBUG", "Error on destroy: ${e.message}")
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val TAG = "MainActivity"
    }
}
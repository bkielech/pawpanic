package com.pawpanic.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.pawpanic.app.databinding.ActivityCameraBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var isPhotoMode = true
    private var currentFrequency: Int = 0
    private var isSoundPlaying: Boolean = false

    companion object {
        const val EXTRA_FREQUENCY = "extra_frequency"
        const val EXTRA_SOUND_PLAYING = "extra_sound_playing"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permissions required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get frequency info from intent
        currentFrequency = intent.getIntExtra(EXTRA_FREQUENCY, 0)
        isSoundPlaying = intent.getBooleanExtra(EXTRA_SOUND_PLAYING, false)

        // Show sound indicator if playing
        if (isSoundPlaying && currentFrequency > 0) {
            binding.tvSoundPlaying.text = "🔊 Sound playing: %,d Hz".format(currentFrequency)
            binding.tvSoundPlaying.visibility = View.VISIBLE
        }

        setupUI()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        // Close button
        binding.btnClose.setOnClickListener {
            finish()
        }

        // Mode selection
        binding.chipPhoto.setOnClickListener {
            isPhotoMode = true
            binding.btnCapture.setIconResource(android.R.drawable.ic_menu_camera)
        }

        binding.chipVideo.setOnClickListener {
            isPhotoMode = false
            binding.btnCapture.setIconResource(android.R.drawable.presence_video_online)
        }

        // Capture button
        binding.btnCapture.setOnClickListener {
            if (isPhotoMode) {
                takePhoto()
            } else {
                toggleVideoRecording()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (!isPhotoMode || true) { // Always request audio for video mode
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startCamera()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Video capture
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "PawPanic_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PawPanic")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@CameraActivity, "📸 Photo saved!", Toast.LENGTH_SHORT).show()
                    
                    // Share the photo
                    output.savedUri?.let { uri ->
                        shareMedia(uri, isVideo = false)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Photo failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun toggleVideoRecording() {
        val videoCapture = videoCapture ?: return

        // If recording, stop it
        recording?.let {
            it.stop()
            recording = null
            return
        }

        // Start new recording
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "PawPanic_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PawPanic")
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutput)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        this@CameraActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.tvRecording.visibility = View.VISIBLE
                        binding.btnCapture.setIconResource(android.R.drawable.ic_media_pause)
                        binding.btnCapture.setBackgroundColor(getColor(R.color.button_stop))
                    }
                    is VideoRecordEvent.Finalize -> {
                        binding.tvRecording.visibility = View.GONE
                        binding.btnCapture.setIconResource(android.R.drawable.presence_video_online)
                        binding.btnCapture.setBackgroundColor(getColor(R.color.accent_primary))

                        if (!recordEvent.hasError()) {
                            Toast.makeText(this, "🎬 Video saved!", Toast.LENGTH_SHORT).show()
                            shareMedia(recordEvent.outputResults.outputUri, isVideo = true)
                        } else {
                            Toast.makeText(this, "Video failed", Toast.LENGTH_SHORT).show()
                        }
                        recording = null
                    }
                }
            }
    }

    private fun shareMedia(uri: android.net.Uri, isVideo: Boolean) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_caption))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_title)))
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.stop()
    }
}
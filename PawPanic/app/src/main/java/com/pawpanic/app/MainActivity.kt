package com.pawpanic.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pawpanic.app.databinding.ActivityMainBinding
import java.io.File
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var frequencyGenerator: FrequencyGenerator
    private lateinit var consentManager: ConsentManager

    private var currentFrequency: Int = 15000
    private var tempMediaUri: Uri? = null

    // Preset frequencies optimized for different pets
    private object Presets {
        const val DOG = 18000      // Dogs can hear up to ~65,000 Hz, 18kHz is attention-grabbing
        const val CAT = 20000      // Cats can hear up to ~85,000 Hz
        const val MOUSE = 22000    // Rodents respond to ultrasonic
        const val BIRD = 8000      // Birds typically 1-8 kHz range
        const val MAX = 22000      // Maximum for most speakers
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, but don't auto-launch camera
            // User will tap the button again
        } else {
            Toast.makeText(this, "Camera permission is needed to capture reactions", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo capture launcher
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempMediaUri != null) {
            shareMedia(tempMediaUri!!, isVideo = false)
        }
    }

    // Video capture launcher
    private val takeVideoLauncher = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && tempMediaUri != null) {
            shareMedia(tempMediaUri!!, isVideo = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        frequencyGenerator = FrequencyGenerator()
        consentManager = ConsentManager(this)

        setupUI()
        initializeAds()
    }

    private fun setupUI() {
        // Initialize frequency display
        updateFrequencyDisplay(currentFrequency)

        // Slider listener
        binding.sliderFrequency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequency = value.toInt()
                updateFrequencyDisplay(currentFrequency)

                // If already playing, update the frequency
                if (frequencyGenerator.isPlaying()) {
                    frequencyGenerator.play(currentFrequency)
                }
            }
        }

        // Preset chips
        setupPresetChip(binding.chipDog, Presets.DOG)
        setupPresetChip(binding.chipCat, Presets.CAT)
        setupPresetChip(binding.chipMouse, Presets.MOUSE)
        setupPresetChip(binding.chipBird, Presets.BIRD)
        setupPresetChip(binding.chipMax, Presets.MAX)

        // Play button
        binding.btnPlay.setOnClickListener {
            playSound()
        }

        // Stop button
        binding.btnStop.setOnClickListener {
            stopSound()
        }

        // Photo button
        binding.btnPhoto.setOnClickListener {
            if (hasCameraPermission()) {
                capturePhoto()
            } else {
                requestCameraPermission()
            }
        }

        // Video button
        binding.btnVideo.setOnClickListener {
            if (hasCameraPermission()) {
                captureVideo()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun setupPresetChip(chip: Chip, frequency: Int) {
        chip.setOnClickListener {
            currentFrequency = frequency
            binding.sliderFrequency.value = frequency.toFloat()
            updateFrequencyDisplay(frequency)

            // If already playing, update frequency immediately
            if (frequencyGenerator.isPlaying()) {
                frequencyGenerator.play(currentFrequency)
            }
        }
    }

    private fun updateFrequencyDisplay(frequency: Int) {
        val formattedFreq = NumberFormat.getNumberInstance().format(frequency)
        binding.tvFrequencyValue.text = getString(R.string.frequency_format, frequency)

        // Show warning for very high frequencies
        if (frequency > 17000) {
            binding.tvHumanRange.text = getString(R.string.warning_high_freq)
        } else {
            binding.tvHumanRange.text = getString(R.string.info_human_range)
        }
    }

    private fun playSound() {
        frequencyGenerator.play(currentFrequency)

        // Update UI
        binding.btnPlay.isEnabled = false
        binding.btnStop.isEnabled = true
        binding.tvSoundStatus.visibility = View.VISIBLE
        binding.tvSoundStatus.text = getString(R.string.warning_volume, currentFrequency)
    }

    private fun stopSound() {
        frequencyGenerator.stop()

        // Update UI
        binding.btnPlay.isEnabled = true
        binding.btnStop.isEnabled = false
        binding.tvSoundStatus.visibility = View.GONE
    }

    private fun initializeAds() {
        // Show consent dialog first (required for GDPR)
        consentManager.showConsentDialogIfNeeded(this) { personalizedAds ->
            // Configure ads based on consent
            consentManager.configureAds(personalizedAds)

            // Initialize Mobile Ads SDK
            MobileAds.initialize(this) {
                // SDK initialized, load the banner
                loadBannerAd(personalizedAds)
            }
        }
    }

    private fun loadBannerAd(personalizedAds: Boolean) {
        val adRequest = if (personalizedAds) {
            AdRequest.Builder().build()
        } else {
            // Non-personalized ads
            val extras = Bundle().apply {
                putString("npa", "1")
            }
            AdRequest.Builder()
                .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
                .build()
        }

        binding.adViewBanner.loadAd(adRequest)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Show explanation
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_camera_title)
                .setMessage(R.string.permission_camera_message)
                .setPositiveButton(R.string.permission_grant) { _, _ ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton(R.string.permission_cancel, null)
                .show()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun capturePhoto() {
        val photoFile = createTempMediaFile("jpg")
        tempMediaUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        takePhotoLauncher.launch(tempMediaUri)
    }

    private fun captureVideo() {
        val videoFile = createTempMediaFile("mp4")
        tempMediaUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            videoFile
        )
        takeVideoLauncher.launch(tempMediaUri)
    }

    private fun createTempMediaFile(extension: String): File {
        // Use cache directory - files are automatically cleaned up
        // No permanent storage of user data
        val fileName = "pawpanic_${System.currentTimeMillis()}.$extension"
        return File(cacheDir, fileName)
    }

    private fun shareMedia(uri: Uri, isVideo: Boolean) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_caption))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_title)))

        // Clean up temp file after a delay (give time for share to complete)
        binding.root.postDelayed({
            cleanupTempFiles()
        }, 60000) // 1 minute delay
    }

    private fun cleanupTempFiles() {
        cacheDir.listFiles()?.filter { it.name.startsWith("pawpanic_") }?.forEach {
            it.delete()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop sound when app goes to background
        if (frequencyGenerator.isPlaying()) {
            stopSound()
        }
        binding.adViewBanner.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.adViewBanner.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        frequencyGenerator.release()
        binding.adViewBanner.destroy()
        cleanupTempFiles()
    }
}

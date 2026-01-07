package com.pawpanic.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pawpanic.app.databinding.ActivityMainBinding
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var consentManager: ConsentManager

    private var currentFrequency: Int = 15000

    // Preset frequencies optimized for different pets
    private object Presets {
        const val DOG = 18000
        const val CAT = 20000
        const val MOUSE = 22000
        const val BIRD = 8000
        const val MAX = 22000
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is needed to capture reactions", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        consentManager = ConsentManager(this)

        setupUI()
        initializeAds()
    }

    private fun setupUI() {
        updateFrequencyDisplay(currentFrequency)

        binding.sliderFrequency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequency = value.toInt()
                updateFrequencyDisplay(currentFrequency)

                if (FrequencyGenerator.isPlaying()) {
                    FrequencyGenerator.play(currentFrequency)
                }
            }
        }

        setupPresetChip(binding.chipDog, Presets.DOG)
        setupPresetChip(binding.chipCat, Presets.CAT)
        setupPresetChip(binding.chipMouse, Presets.MOUSE)
        setupPresetChip(binding.chipBird, Presets.BIRD)
        setupPresetChip(binding.chipMax, Presets.MAX)

        binding.btnPlay.setOnClickListener {
            playSound()
        }

        binding.btnStop.setOnClickListener {
            stopSound()
        }

        // Both buttons now open CameraActivity
        binding.btnPhoto.setOnClickListener {
            checkPermissionsAndOpenCamera()
        }

        binding.btnVideo.setOnClickListener {
            checkPermissionsAndOpenCamera()
        }
    }

    private fun setupPresetChip(chip: Chip, frequency: Int) {
        chip.setOnClickListener {
            currentFrequency = frequency
            binding.sliderFrequency.value = frequency.toFloat()
            updateFrequencyDisplay(frequency)

            if (FrequencyGenerator.isPlaying()) {
                FrequencyGenerator.play(currentFrequency)
            }
        }
    }

    private fun updateFrequencyDisplay(frequency: Int) {
        binding.tvFrequencyValue.text = getString(R.string.frequency_format, frequency)

        if (frequency > 17000) {
            binding.tvHumanRange.text = getString(R.string.warning_high_freq)
        } else {
            binding.tvHumanRange.text = getString(R.string.info_human_range)
        }
    }

    private fun playSound() {
        FrequencyGenerator.play(currentFrequency)

        binding.btnPlay.isEnabled = false
        binding.btnStop.isEnabled = true
        binding.tvSoundStatus.visibility = View.VISIBLE
        binding.tvSoundStatus.text = getString(R.string.warning_volume, currentFrequency)
    }

    private fun stopSound() {
        FrequencyGenerator.stop()

        binding.btnPlay.isEnabled = true
        binding.btnStop.isEnabled = false
        binding.tvSoundStatus.visibility = View.GONE
    }

    private fun checkPermissionsAndOpenCamera() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            openCamera()
        } else if (notGranted.any { shouldShowRequestPermissionRationale(it) }) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_camera_title)
                .setMessage(R.string.permission_camera_message)
                .setPositiveButton(R.string.permission_grant) { _, _ ->
                    cameraPermissionLauncher.launch(permissions)
                }
                .setNegativeButton(R.string.permission_cancel, null)
                .show()
        } else {
            cameraPermissionLauncher.launch(permissions)
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(CameraActivity.EXTRA_FREQUENCY, currentFrequency)
            putExtra(CameraActivity.EXTRA_SOUND_PLAYING, FrequencyGenerator.isPlaying())
        }
        startActivity(intent)
    }

    private fun initializeAds() {
        consentManager.showConsentDialogIfNeeded(this) { personalizedAds ->
            consentManager.configureAds(personalizedAds)

            MobileAds.initialize(this) {
                loadBannerAd(personalizedAds)
            }
        }
    }

    private fun loadBannerAd(personalizedAds: Boolean) {
        val adRequest = if (personalizedAds) {
            AdRequest.Builder().build()
        } else {
            val extras = Bundle().apply {
                putString("npa", "1")
            }
            AdRequest.Builder()
                .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
                .build()
        }

        binding.adViewBanner.loadAd(adRequest)
    }

    override fun onPause() {
        super.onPause()
        // NIE zatrzymujemy dźwięku - gra dalej w CameraActivity!
        binding.adViewBanner.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.adViewBanner.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        FrequencyGenerator.release()
        binding.adViewBanner.destroy()
    }
}
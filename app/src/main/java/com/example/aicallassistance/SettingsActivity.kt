package com.example.aicallassistance

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aicallassistance.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish() // Close the activity after saving
        }
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val apiKey = sharedPreferences.getString("API_KEY", "")
        val musicUrl = sharedPreferences.getString("MUSIC_URL", "")
        val isRootMode = sharedPreferences.getBoolean("ROOT_MODE", false)

        binding.etApiKey.setText(apiKey)
        binding.etBackgroundMusicUrl.setText(musicUrl)
        binding.swRootMode.isChecked = isRootMode
    }

    private fun saveSettings() {
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("API_KEY", binding.etApiKey.text.toString())
            putString("MUSIC_URL", binding.etBackgroundMusicUrl.text.toString())
            putBoolean("ROOT_MODE", binding.swRootMode.isChecked)
            apply()
        }
    }
}

package com.example.aicallassistance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicallassistance.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recordingsAdapter: RecordingsAdapter
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        if (!hasPermissions()) {
            requestPermissions()
        }

        setupRecyclerView()

        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun setupRecyclerView() {
        recordingsAdapter = RecordingsAdapter(emptyList())
        binding.rvRecordings.apply {
            adapter = recordingsAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun loadRecordings() {
        val recordingsDir = File(getExternalFilesDir(null), "Recordings")
        if (recordingsDir.exists() && recordingsDir.isDirectory) {
            val recordingFiles = recordingsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyArray()
            if (recordingFiles.isEmpty()) {
                binding.tvNoRecordings.visibility = View.VISIBLE
                binding.rvRecordings.visibility = View.GONE
            } else {
                binding.tvNoRecordings.visibility = View.GONE
                binding.rvRecordings.visibility = View.VISIBLE
                recordingsAdapter.updateData(recordingFiles.toList())
            }
        } else {
            binding.tvNoRecordings.visibility = View.VISIBLE
            binding.rvRecordings.visibility = View.GONE
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT >= 29)
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (android.os.Build.VERSION.SDK_INT < 29) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("MainActivity", "All permissions granted")
                loadRecordings()
            } else {
                Log.w("MainActivity", "Some permissions were denied.")
                binding.tvNoRecordings.text = "Permissions required to show recordings."
                binding.tvNoRecordings.visibility = View.VISIBLE
            }
        }
    }

    private class RecordingsAdapter(private var recordings: List<File>) : RecyclerView.Adapter<RecordingsAdapter.RecordingViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_recording, parent, false)
            return RecordingViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
            val recording = recordings[position]
            holder.bind(recording)
        }

        override fun getItemCount(): Int = recordings.size

        fun updateData(newRecordings: List<File>) {
            this.recordings = newRecordings
            notifyDataSetChanged()
        }

        class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.tvRecordingName)
            private val dateTextView: TextView = itemView.findViewById(R.id.tvRecordingDate)

            fun bind(file: File) {
                nameTextView.text = file.name
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                dateTextView.text = sdf.format(Date(file.lastModified()))
            }
        }
    }
}

package com.example.watchlauncher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val emergency_number = "890"
private const val PERMISSION_REQUEST_CODE = 100

class MainActivity : AppCompatActivity() {

    private val emergencyContacts = mutableListOf(
        ContactInfo("Emergency", emergency_number),
        ContactInfo("Filippo", "3711421801"),
        ContactInfo("Casa Andrea", "042381677"),
        ContactInfo("Dionisia", "3497227733"),
        ContactInfo("Andrea", "3392132313")
    )

    private var currentContactIndex = 0
    private var isSequentialCalling = false
    private var lastScrollTime: Long = 0

    // UI elements
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var batteryIndicatorView: View
    private lateinit var contactPreviousTextView: TextView
    private lateinit var contactCurrentTextView: TextView
    private lateinit var contactNextTextView: TextView

    // Screen management
    private val idleTimeout: Long = 10000 // 10 seconds
    private val handler = Handler(Looper.getMainLooper())
    private val dimScreenRunnable = Runnable {
        // Allows the screen to turn off after inactivity
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the status bar for an immersive, full-screen experience
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        // Find UI elements with corrected IDs
        timeTextView = findViewById(R.id.clock_text)
        dateTextView = findViewById(R.id.date_text)
        batteryIndicatorView = findViewById(R.id.battery_indicator)
        contactPreviousTextView = findViewById(R.id.prev_contact)
        contactCurrentTextView = findViewById(R.id.current_contact)
        contactNextTextView = findViewById(R.id.next_contact)

        // Set up the back button callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isSequentialCalling = false
                Toast.makeText(this@MainActivity, "Emergency calling stopped.", Toast.LENGTH_SHORT).show()
            }
        })

        // Initialize UI and listeners
        updateUI()
        updateContactTextViews()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        resetDimTimer()
        requestPermissions()
    }

    override fun onStart() {
        super.onStart()
        // Ensure the screen is on when the app becomes visible
        resetDimTimer()
    }

    override fun onStop() {
        super.onStop()
        // Stop the screen dimming timer when the app is no longer visible
        handler.removeCallbacks(dimScreenRunnable)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            // Implement debouncing to handle multiple events per single step
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScrollTime < 400) {
                return true
            }
            lastScrollTime = currentTime

            // Handle rotary wheel scrolls
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta > 0) {
                // Scroll forward
                currentContactIndex = (currentContactIndex + 1) % emergencyContacts.size
            } else {
                // Scroll backward
                currentContactIndex = (currentContactIndex - 1 + emergencyContacts.size) % emergencyContacts.size
            }
            updateContactTextViews()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 131) {
            // Handle rotary wheel button press (KEY_131)
            val selectedContact = emergencyContacts[currentContactIndex]
            makeCall(selectedContact.phoneNumber)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied. The app may not function correctly.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI() {
        // Update time and date
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = Date()
        dateTextView.text = dateFormat.format(date)
        timeTextView.text = timeFormat.format(date)
        handler.postDelayed({ updateUI() }, 1000)
    }

    private fun updateContactTextViews() {
        val previousIndex = (currentContactIndex - 1 + emergencyContacts.size) % emergencyContacts.size
        val nextIndex = (currentContactIndex + 1) % emergencyContacts.size

        contactPreviousTextView.text = emergencyContacts[previousIndex].name
        contactCurrentTextView.text = emergencyContacts[currentContactIndex].name
        contactNextTextView.text = emergencyContacts[nextIndex].name

        // Ensure all three TextViews are always fully opaque (white)
        contactPreviousTextView.alpha = 1.0f
        contactCurrentTextView.alpha = 1.0f
        contactNextTextView.alpha = 1.0f
    }

    private fun makeCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Call permission is required to make a call.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = "tel:$phoneNumber".toUri()
            startActivity(callIntent)
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security exception when starting call", e)
            Toast.makeText(this, "Call permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetDimTimer()
    }

    private fun resetDimTimer() {
        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Reset the timer to turn the screen off
        handler.removeCallbacks(dimScreenRunnable)
        handler.postDelayed(dimScreenRunnable, idleTimeout)
    }

    private fun updateBatteryIndicator(level: Int) {
        val color = when {
            level > 30 -> ContextCompat.getColor(this, R.color.green_battery)
            level in 20..30 -> ContextCompat.getColor(this, R.color.yellow_battery)
            else -> ContextCompat.getColor(this, R.color.red_battery)
        }
        val drawable = batteryIndicatorView.background as? GradientDrawable
        drawable?.setColor(color)
    }

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            updateBatteryIndicator(level)
        }
    }

    data class ContactInfo(val name: String, val phoneNumber: String)
}
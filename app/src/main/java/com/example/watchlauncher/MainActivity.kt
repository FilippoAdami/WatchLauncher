package com.example.watchlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val emergencyContacts = mutableListOf(
        ContactInfo("Dr. Smith", "1234567890"),
        ContactInfo("Family", "0987654321"),
        ContactInfo("Friend Jane", "1122334455"),
        ContactInfo("Neighbor", "9988776655"),
        ContactInfo("Emergency", "112")
    )

    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var carouselLayoutManager: CarouselLayoutManager
    private val dimmableViews: MutableList<View> = mutableListOf()
    private val handler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val idleTimeout: Long = 10000 // 10 seconds
    private val CALL_PHONE_PERMISSION_REQUEST_CODE = 101
    // Manually define the rotary encoder constant for older devices
    private val SOURCE_ROTARY_ENCODER = 0x00001000
    private var isSnapping = false

    private val dimScreenRunnable = Runnable {
        dimmableViews.forEach { view ->
            view.alpha = 0.5f
        }
    }

    private val updateClockRunnable = object : Runnable {
        override fun run() {
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val currentDate = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
            findViewById<TextView>(R.id.clock_text).text = currentTime
            findViewById<TextView>(R.id.date_text).text = currentDate
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request CALL_PHONE permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PHONE_PERMISSION_REQUEST_CODE)
        }

        // Initialize UI components and dimmable views
        dimmableViews.add(findViewById(R.id.clock_text))
        dimmableViews.add(findViewById(R.id.date_text))
        dimmableViews.add(findViewById(R.id.contacts_recycler_view))
        resetDimTimer()

        // Set up clock and date
        clockHandler.post(updateClockRunnable)

        // Set up the RecyclerView with the custom CarouselLayoutManager
        contactsRecyclerView = findViewById(R.id.contacts_recycler_view)
        carouselLayoutManager = CarouselLayoutManager(this)
        contactsRecyclerView.layoutManager = carouselLayoutManager
        contactsAdapter = ContactsAdapter(this, emergencyContacts)
        contactsRecyclerView.adapter = contactsAdapter

        // Use a LinearSnapHelper to automatically snap to the center item
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(contactsRecyclerView)

        // Start the recycler view in the middle for infinite scrolling
        contactsRecyclerView.scrollToPosition(Int.MAX_VALUE / 2)

        // Override back button behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to prevent the app from closing
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle physical button presses
        if (keyCode == KeyEvent.KEYCODE_STEM_PRIMARY) {
            startCall(emergencyContacts[carouselLayoutManager.getCenterItemPosition()].phoneNumber)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_SCROLL && event.isFromSource(SOURCE_ROTARY_ENCODER)) {
            // Handle rotary wheel scrolling
            contactsRecyclerView.smoothScrollBy(0, (event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 50).toInt())
            resetDimTimer()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetDimTimer()
    }

    private fun resetDimTimer() {
        handler.removeCallbacks(dimScreenRunnable)
        dimmableViews.forEach { view ->
            view.alpha = 1.0f
        }
        handler.postDelayed(dimScreenRunnable, idleTimeout)
    }

    private fun startCall(phoneNumber: String) {
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (tm.callState == TelephonyManager.CALL_STATE_IDLE) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL)
                callIntent.data = Uri.parse("tel:$phoneNumber")
                startActivity(callIntent)
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission to make calls is required.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class ContactInfo(val name: String, val phoneNumber: String)
}

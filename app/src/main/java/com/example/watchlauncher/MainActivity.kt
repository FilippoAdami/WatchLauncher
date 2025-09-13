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
import android.telephony.TelephonyManager
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
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val emergencyContacts = mutableListOf(
        ContactInfo("Son", "3333333333"),
        ContactInfo("Daughter", "3333333333"),
        ContactInfo("Uncle", "3333333333"),
        ContactInfo("112", "112"),
    )

    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var carouselLayoutManager: CarouselLayoutManager
    private val dimmableViews: MutableList<View> = mutableListOf()
    private val handler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val idleTimeout: Long = 10000 // 10 seconds
    private val CALL_PHONE_PERMISSION_REQUEST_CODE = 101

    private var lastScrollTime: Long = 0

    private val dimScreenRunnable = Runnable {
        dimmableViews.forEach { view ->
            view.alpha = 0.5f
        }
    }

    private lateinit var batteryIndicatorView: View

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            updateBatteryIndicator(level)
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

        // Hide the status bar for an immersive, full-screen experience
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

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

        batteryIndicatorView = findViewById(R.id.battery_indicator)

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
        contactsRecyclerView.post {
            contactsRecyclerView.smoothScrollBy(0, 10)
        }

        // Override back button behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to prevent the app from closing
            }
        })

        // Register the battery receiver
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            this.registerReceiver(batteryReceiver, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        updateBatteryIndicator(level)
    }

    // The onGenericMotionEvent is handled at the Activity level
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_SCROLL) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScrollTime > 400) {
                val scrollValue = event.getAxisValue(MotionEvent.AXIS_SCROLL).toInt()
                // Dispatch the scroll command to the custom function
                smoothScrollVerticallyBy(scrollValue)
                lastScrollTime = currentTime
            }
            resetDimTimer()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun smoothScrollVerticallyBy(direction: Int) {
        // Find the height of a single item to know how far to scroll
        val childView = contactsRecyclerView.getChildAt(0)
        val itemHeight = childView?.height ?: 0

        // Use 150% of the item's height to ensure the snap helper is triggered
        val scrollDistance = (direction * itemHeight*1.2).toInt()

        contactsRecyclerView.smoothScrollBy(0, scrollDistance)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 131) {
            val selectedPosition = carouselLayoutManager.getCenterItemPosition()
            val contact = emergencyContacts[selectedPosition % emergencyContacts.size]
            startCall(contact.phoneNumber)
            resetDimTimer()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetDimTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        clockHandler.removeCallbacks(updateClockRunnable)
    }

    private fun resetDimTimer() {
        handler.removeCallbacks(dimScreenRunnable)
        dimmableViews.forEach { view ->
            view.alpha = 1.0f
        }
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

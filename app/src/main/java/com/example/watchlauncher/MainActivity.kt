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
import android.telephony.PhoneStateListener
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
import android.media.MediaRecorder
import java.io.IOException

private const val emergency_number = "890"

class MainActivity : AppCompatActivity() {

    private val emergencyContacts = mutableListOf(
        ContactInfo("Filippo", "3711421801"),
        ContactInfo("Casa Andrea", "042381677"),
        ContactInfo("Dionisia", "3497227733"),
        ContactInfo("Andrea", "3392132313"),
        ContactInfo("Emergency", emergency_number)
    )

    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var carouselLayoutManager: CarouselLayoutManager
    private val dimmableViews: MutableList<View> = mutableListOf()
    private val handler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val idleTimeout: Long = 10000 // 10 seconds
    private val PERMISSION_REQUEST_CODE = 101

    private var lastScrollTime: Long = 0
    private var isSequentialCalling = false

    private val dimScreenRunnable: Runnable = object : Runnable {
        override fun run() {
            dimmableViews.forEach { view ->
                view.alpha = 0.5f
            }
        }
    }

    private lateinit var batteryIndicatorView: View

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            updateBatteryIndicator(level)
        }
    }

    private val updateClockRunnable: Runnable = object : Runnable {
        override fun run() {
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val currentDate = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
            findViewById<TextView>(R.id.clock_text).text = currentTime
            findViewById<TextView>(R.id.date_text).text = currentDate
            clockHandler.postDelayed(this, 1000)
        }
    }

    private lateinit var telephonyManager: TelephonyManager
    private var isCalling = false
    private var currentCallingPosition = -1
    private var isUserSpeaking = false // New variable to track if the user is speaking

    private val callTimeoutHandler = Handler(Looper.getMainLooper())
    private val callTimeoutRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isCalling) return

            // If the timeout is reached, it means the call wasn't answered by a person.
            // We stop listening to the current state and try the next contact.
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            callNextContactInSequence()
        }
    }

    private val micCheckHandler = Handler(Looper.getMainLooper())
    private val micCheckRunnable: Runnable = object : Runnable {
        override fun run() {
            // If the mic check timer expires, it means no speaking was detected.
            stopMicrophoneCheck()
            // We can now move on to the next contact.
            callNextContactInSequence()
        }
    }

    private var mediaRecorder: MediaRecorder? = null

    private fun startMicrophoneCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission is required for this feature.", Toast.LENGTH_SHORT).show()
            return
        }

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile("/dev/null")
            try {
                prepare()
                start()
                isUserSpeaking = false
                micCheckHandler.postDelayed(micCheckRunnable, 5000) // 5 second timer
                // Start a background check for amplitude
                Thread {
                    while (isCalling) {
                        if (getAmplitude() > 1000) { // A threshold to detect sound
                            isUserSpeaking = true
                            stopMicrophoneCheck()
                            break
                        }
                        Thread.sleep(100)
                    }
                }.start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopMicrophoneCheck() {
        micCheckHandler.removeCallbacks(micCheckRunnable)
        if (mediaRecorder != null) {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun getAmplitude(): Int {
        return mediaRecorder?.maxAmplitude ?: 0
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    // Start the 15-second timer
                    callTimeoutHandler.postDelayed(callTimeoutRunnable, 15000)
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    // Call is answered (either human or voicemail), cancel the 15-second timer
                    callTimeoutHandler.removeCallbacks(callTimeoutRunnable)
                    // Start the microphone check
                    startMicrophoneCheck()
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    // Call has ended.
                    if (isSequentialCalling) {
                        stopMicrophoneCheck()
                        // If the user was speaking, we stop the sequence.
                        if (isUserSpeaking) {
                            isSequentialCalling = false
                        } else {
                            // If no speaking was detected, the micCheckRunnable will continue to the next call.
                            callNextContactInSequence()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the status bar for an immersive, full-screen experience
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_main)

        // Request all permissions at once
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
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

        // This makes sure the list is perfectly centered on startup
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

        // Initialize TelephonyManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.CALL_PHONE -> {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Call permission granted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Call permission denied.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Manifest.permission.READ_PHONE_STATE -> {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Phone state permission granted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Phone state permission denied.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Manifest.permission.RECORD_AUDIO -> {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Audio permission granted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Audio permission denied.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // The onGenericMotionEvent is handled at the Activity level
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_SCROLL) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScrollTime > 350) {
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
        val scrollDistance = (direction * itemHeight)

        contactsRecyclerView.smoothScrollBy(0, scrollDistance)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 131) {
            if (isSequentialCalling) {
                isSequentialCalling = false
                Toast.makeText(this, "Calling sequence stopped.", Toast.LENGTH_SHORT).show()
            } else {
                currentCallingPosition = carouselLayoutManager.getCenterItemPosition() % emergencyContacts.size
                isSequentialCalling = true
                startSequentialCall()
            }
            resetDimTimer()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startSequentialCall() {
        if (!isSequentialCalling) return
        val currentContact = emergencyContacts[currentCallingPosition]
        startCall(currentContact.phoneNumber)
    }

    private fun callNextContactInSequence() {
        if (!isSequentialCalling) return

        // Scroll to the next position
        smoothScrollVerticallyBy(1)

        // Wait a small delay for the scroll to complete before getting the new position
        Handler(Looper.getMainLooper()).postDelayed({
            currentCallingPosition = carouselLayoutManager.getCenterItemPosition() % emergencyContacts.size
            val nextContact = emergencyContacts[currentCallingPosition]

            // If the next contact is the emergency number, we scroll again to skip it.
            if (nextContact.phoneNumber == emergency_number) {
                contactsRecyclerView.smoothScrollBy(0, 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    currentCallingPosition = carouselLayoutManager.getCenterItemPosition() % emergencyContacts.size
                    // Now we are at the final position, call this number.
                    val finalContact = emergencyContacts[currentCallingPosition]
                    startCall(finalContact.phoneNumber)
                }, 500)
            } else {
                // It's a regular contact, so we call it.
                startCall(nextContact.phoneNumber)
            }
        }, 500)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetDimTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        clockHandler.removeCallbacks(updateClockRunnable)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
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
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        callTimeoutHandler.postDelayed(callTimeoutRunnable, 15000)

        try {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$phoneNumber")
            startActivity(callIntent)
        } catch (e: SecurityException) {
            isCalling = false
        }
    }

    data class ContactInfo(val name: String, val phoneNumber: String)
}

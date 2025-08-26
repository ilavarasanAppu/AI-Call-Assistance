package com.example.aicallassistance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d("CallStateReceiver", "State: $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d("CallStateReceiver", "Incoming call ringing")
                // Handle incoming call ringing
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d("CallStateReceiver", "Call is active (off-hook)")
                val serviceIntent = Intent(context, InCallService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallStateReceiver", "Call is idle")
                val serviceIntent = Intent(context, InCallService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}

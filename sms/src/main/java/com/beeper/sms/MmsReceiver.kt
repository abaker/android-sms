package com.beeper.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MmsReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "Received MMS")
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}

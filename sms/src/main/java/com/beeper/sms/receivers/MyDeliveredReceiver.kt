package com.beeper.sms.receivers

import android.content.Context
import android.content.Intent
import com.beeper.sms.Log
import com.beeper.sms.extensions.printExtras
import com.klinker.android.send_message.DeliveredReceiver

class MyDeliveredReceiver : DeliveredReceiver() {
    override fun onMessageStatusUpdated(context: Context?, intent: Intent?, resultCode: Int) {
        Log.d(TAG, intent.printExtras())
    }

    companion object {
        private const val TAG = "MyDeliveredReceiver"
    }
}
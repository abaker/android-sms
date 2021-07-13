package com.beeper.sms.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.beeper.sms.Bridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.SmsProvider
import com.klinker.android.send_message.SentReceiver
import com.klinker.android.send_message.Transaction.SENT_SMS_BUNDLE

class MySentReceiver : SentReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, intent.printExtras())
        val uri = intent?.getStringExtra("message_uri")?.toUri()
        val commandId = (intent?.getParcelableExtra(SENT_SMS_BUNDLE) as? Command)?.id
        val message = uri?.let { SmsProvider(context).getMessage(it) }
        if (uri == null || commandId == null || message == null) {
            Log.e(TAG, "Unable to respond (command=$commandId uri=$uri)")
            return
        }
        Bridge.INSTANCE.send(
            Command(
                "response",
                SendMessage.Response(message.guid, message.timestamp),
                commandId,
            )
        )
    }

    companion object {
        private const val TAG = "MySentReceiver"
    }
}

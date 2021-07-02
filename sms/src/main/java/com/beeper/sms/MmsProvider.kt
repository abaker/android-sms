package com.beeper.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms.*
import android.provider.Telephony.MmsSms
import android.provider.Telephony.ThreadsColumns
import android.util.Log
import androidx.core.net.toUri
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.PartProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MmsProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val partProvider: PartProvider,
) {
    private val cr = context.contentResolver

    fun getMessage(uri: Uri): Message? = getMms(where = "_id = ${uri.lastPathSegment}").firstOrNull()

    private fun getMms(where: String? = null): List<Message> =
        cr.map(CONTENT_URI, where) {
            val id = it.getLong(_ID)
            val recipients =
                getAddresses(it.getLong(THREAD_ID))
                    ?.mapNotNull { addr -> getPhoneNumber(addr) }
            val attachments = partProvider.getAttachment(id)
            val sender = getSender(id)
            if (recipients?.size == 1) {
                Message(
                    guid = it.getInt(_ID).toString(),
                    timestamp = it.getLong(DATE),
                    subject = it.getString(SUBJECT) ?: "",
                    text = attachments.mapNotNull { a -> a.text }.joinToString(""),
                    chat_guid = "SMS;-;$sender",
                    sender_guid = "SMS;-;$sender",
                    attachments = attachments.mapNotNull { a -> a.attachment },
                )
            } else {
                val address = recipients?.joinToString(" ")
                Log.e(TAG, "group chats not supported yet: $address")
                null
            }
        }

    private fun getAddresses(thread: Long): List<String>? =
        cr.firstOrNull(URI_THREADS, "$_ID = $thread") {
            it.getString(ThreadsColumns.RECIPIENT_IDS)?.split(" ")
        }

    private fun getPhoneNumber(recipient: String): String? =
        cr.firstOrNull(URI_ADDRESSES, "$_ID = $recipient") {
            it.getString(Addr.ADDRESS)
        }

    private fun getSender(message: Long): String? =
        cr.firstOrNull("$CONTENT_URI/$message/addr".toUri()) {
            it.getString(Addr.ADDRESS)
        }

    companion object {
        private const val TAG = "MmsProvider"
        private val URI_THREADS = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
        private val URI_ADDRESSES = "${MmsSms.CONTENT_URI}/canonical-addresses".toUri()

        val Uri.isMms: Boolean
            get() = toString().startsWith("$CONTENT_URI")
    }
}
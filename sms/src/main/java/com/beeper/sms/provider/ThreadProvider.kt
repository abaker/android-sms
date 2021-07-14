package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony.*
import androidx.core.net.toUri
import com.beeper.sms.extensions.*

class ThreadProvider constructor(context: Context) {
    private val cr = context.contentResolver

    fun getRecentConversations(minTimestamp: Long): List<String> =
        cr.map(URI_THREADS, "${Sms.Conversations.DATE} > $minTimestamp") {
            getChatGuid(it.getLong(ThreadsColumns._ID))
        }

    fun getMessagesAfter(thread: Long, timestampSeconds: Long): List<Pair<Long, Boolean>> =
        getMessagesAfter(thread, timestampSeconds * 1000, false)
            .plus(getMessagesAfter(thread, timestampSeconds, true))
            .sortedBy { it.first }
            .map { Pair(it.second, it.third) }

    fun getRecentMessages(thread: Long, limit: Int): List<Pair<Long, Boolean>> =
        getRecentMessages(thread, limit, false)
            .plus(getRecentMessages(thread, limit, true))
            .sortedBy { it.first }
            .takeLast(limit)
            .map { Pair(it.second, it.third) }

    fun getChatGuid(thread: Long): String? =
        getAddresses(thread)
            ?.mapNotNull { addr -> getPhoneNumber(addr) }
            ?.takeIf { it.isNotEmpty() }
            ?.chatGuid

    private fun getRecentMessages(thread: Long, limit: Int, mms: Boolean) =
        getMessages(thread, mms.isMms, "LIMIT $limit")

    private fun getMessagesAfter(thread: Long, timestamp: Long, mms: Boolean) =
        getMessages(thread, "${mms.isMms} AND ${Sms.Conversations.DATE} > $timestamp")

    private fun getMessages(thread: Long, where: String, limit: String = "") =
        cr.map(
            "${MmsSms.CONTENT_CONVERSATIONS_URI}/$thread".toUri(),
            where = where,
            projection = PROJECTION,
            order = "${Sms.Conversations.DATE} DESC $limit"
        ) {
            val mms = it.getString(MmsSms.TYPE_DISCRIMINATOR_COLUMN) == "mms"
            Triple(
                it.getLong(Sms.Conversations.DATE) * if (mms) 1000 else 1,
                it.getLong(Sms.Conversations._ID),
                mms
            )
        }

    private fun getAddresses(thread: Long): List<String>? =
        cr.firstOrNull(URI_THREADS, "${Mms._ID} = $thread") {
            it.getString(ThreadsColumns.RECIPIENT_IDS)?.split(" ")
        }

    private fun getPhoneNumber(recipient: String): String? =
        cr.firstOrNull(URI_ADDRESSES, "${Mms._ID} = $recipient") {
            it.getString(Mms.Addr.ADDRESS)
        }

    companion object {
        val URI_THREADS = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
        private val URI_ADDRESSES = "${MmsSms.CONTENT_URI}/canonical-addresses".toUri()
        private val PROJECTION = arrayOf(
            Sms.Conversations._ID,
            Sms.Conversations.DATE,
            MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        )

        val Boolean.isMms: String
            get() = "${MmsSms.TYPE_DISCRIMINATOR_COLUMN} ${if (this) "=" else "!="} 'mms'"

        val List<String>.chatGuid: String
            get() = "SMS;${if (size == 1) "-" else "+"};${joinToString(" ")}"
    }
}
package com.beeper.sms

import android.content.Context
import android.os.Build
import android.util.Log
import com.beeper.sms.BridgeService.Companion.startBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.Error
import com.beeper.sms.extensions.cacheDir
import com.beeper.sms.extensions.env
import com.beeper.sms.extensions.isDefaultSmsApp
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.util.concurrent.Executors.newSingleThreadExecutor

class Bridge private constructor() {
    private lateinit var nativeLibDir: String
    private lateinit var cacheDir: String
    private lateinit var channelId: String
    private var configPathProvider: (suspend () -> String?)? = null
    private var configPath: String? = null
    private var channelIcon: Int? = null
    private var process: Process? = null
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val gson =
        GsonBuilder().registerTypeAdapter(DOUBLE_SERIALIZER_TYPE, DOUBLE_SERIALIZER).create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(
        context: Context,
        channelId: String = DEFAULT_CHANNEL_ID,
        channelIcon: Int? = null,
        configPathProvider: suspend () -> String?,
    ) {
        this.configPathProvider = configPathProvider
        this.channelId = channelId
        this.channelIcon = channelIcon
        nativeLibDir = context.applicationInfo.nativeLibraryDir
        cacheDir = context.cacheDir("mautrix")
        start(context)
    }

    fun start(context: Context?) = scope.launch {
        try {
            if (context?.isDefaultSmsApp == true &&
                getConfig().exists() &&
                getProcess()?.running == true
            ) {
                context.startBridge(channelId, channelIcon)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Error")
        }
    }

    @Synchronized
    private fun getProcess(): Process? {
        if (process?.running != true) {
            Log.d(TAG, "Starting mautrix-imessage")
            process = ProcessBuilder()
                .env(
                    "LD_LIBRARY_PATH" to nativeLibDir,
                    "TMPDIR" to cacheDir,
                )
                .directory(File(nativeLibDir))
                .command("./libmautrix.so", "-c", configPath)
                .start()
        }
        return process
    }

    private suspend fun getConfig(): String? =
        configPath ?: configPathProvider?.invoke()?.takeIf { it.exists() }?.also { configPath = it }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process?.destroyForcibly()
        } else {
            process?.destroy()
        }
        process = null
    }

    fun forEachError(action: (String) -> Unit) =
        getProcess()?.errorStream?.forEach(action) ?: Log.e(TAG, "forEachError failed")

    fun forEachCommand(action: (String) -> Unit) =
        getProcess()?.inputStream?.forEach(action) ?: Log.e(TAG, "forEachCommand failed")

    fun send(error: Error) = send(error as Any)

    fun send(command: Command) = send(command as Any)

    private fun send(any: Any) = scope.launch(outgoing) {
        getProcess()
            ?.outputStream
            ?.writer()
            ?.apply {
                Log.d(TAG, "send: $any")
                append("${gson.toJson(any)}\n")
                flush()
            }
            ?: Log.e(TAG, "failed to send: $any")
    }

    companion object {
        private const val TAG = "Bridge"
        private const val DEFAULT_CHANNEL_ID = "sms_bridge_service"
        private val DOUBLE_SERIALIZER_TYPE = object : TypeToken<Double>() {}.type
        private val DOUBLE_SERIALIZER =
            JsonSerializer<Double> { src, _, _ -> JsonPrimitive(BigDecimal.valueOf(src)) }
        val INSTANCE = Bridge()

        val Process.running: Boolean
            get() = try {
                exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }

        fun InputStream.forEach(action: (String) -> Unit) {
            Log.d(TAG, "$this forEach")
            reader().forEachLine(action)
            Log.d(TAG, "$this closed")
        }

        private fun String?.exists(): Boolean = this?.let { File(it) }?.exists() == true
    }
}
package com.e621.client.data.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single log entry
 */
data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val message: String,
    val tag: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    
    override fun toString(): String {
        return "[$formattedTime] ${type.name}: $message" + (tag?.let { " (Tag: $it)" } ?: "")
    }
    
    fun toLogLine(): String {
        return "$timestamp|${type.name}|$message|${tag ?: ""}"
    }
    
    companion object {
        fun fromLogLine(line: String): LogEntry? {
            return try {
                val parts = line.split("|", limit = 4)
                if (parts.size >= 3) {
                    LogEntry(
                        timestamp = parts[0].toLongOrNull() ?: 0L,
                        type = LogType.valueOf(parts[1]),
                        message = parts[2],
                        tag = if (parts.size > 3 && parts[3].isNotEmpty()) parts[3] else null
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class LogType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    CHECK,
    NOTIFICATION
}

/**
 * Simple file-based app logger for following activity
 */
object AppLog {
    private const val LOG_FILE = "app_log.txt"
    private const val MAX_ENTRIES = 500
    
    /**
     * Add a log entry
     */
    fun log(context: Context, type: LogType, message: String, tag: String? = null) {
        try {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                type = type,
                message = message,
                tag = tag
            )
            
            val file = File(context.filesDir, LOG_FILE)
            val existingLogs = if (file.exists()) {
                file.readLines().takeLast(MAX_ENTRIES - 1)
            } else {
                emptyList()
            }
            
            file.writeText((existingLogs + entry.toLogLine()).joinToString("\n"))
        } catch (e: Exception) {
            // Ignore logging errors
        }
    }
    
    /**
     * Get all log entries
     */
    fun getLogs(context: Context): List<LogEntry> {
        return try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists()) {
                file.readLines()
                    .mapNotNull { LogEntry.fromLogLine(it) }
                    .sortedByDescending { it.timestamp }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear all logs
     */
    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    // Convenience methods
    fun info(context: Context, message: String, tag: String? = null) = 
        log(context, LogType.INFO, message, tag)
    
    fun success(context: Context, message: String, tag: String? = null) = 
        log(context, LogType.SUCCESS, message, tag)
    
    fun warning(context: Context, message: String, tag: String? = null) = 
        log(context, LogType.WARNING, message, tag)
    
    fun error(context: Context, message: String, tag: String? = null) = 
        log(context, LogType.ERROR, message, tag)
    
    fun check(context: Context, message: String, tag: String? = null) = 
        log(context, LogType.CHECK, message, tag)
    
    fun notification(context: Context, message: String, tag: String? = null) = 
        log(context, LogType.NOTIFICATION, message, tag)
}

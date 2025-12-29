package com.e621.client.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log entry data class
 */
data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val message: String,
    val tag: String? = null
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getShortTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * Log type enum with corresponding icons
 */
enum class LogType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    CHECK,
    NOTIFICATION
}

/**
 * File-based logging system for the Following feature
 */
object AppLog {
    private const val LOG_FILE = "following_log.txt"
    private const val MAX_ENTRIES = 500
    private const val TAG = "AppLog"
    
    /**
     * Log a message
     */
    @Synchronized
    fun log(context: Context, type: LogType, message: String, tag: String? = null) {
        try {
            val file = getLogFile(context)
            val entry = "${System.currentTimeMillis()}|${type.name}|${message.replace("|", "\\|")}|${tag ?: ""}"
            
            // Append to file
            BufferedWriter(FileWriter(file, true)).use { writer ->
                writer.write(entry)
                writer.newLine()
            }
            
            // Also log to Android logcat
            Log.d(TAG, "[$type] $message" + if (tag != null) " (tag: $tag)" else "")
            
            // Trim file if too large
            trimLogFile(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log", e)
        }
    }
    
    /**
     * Get all log entries
     */
    @Synchronized
    fun getLogs(context: Context): List<LogEntry> {
        val logs = mutableListOf<LogEntry>()
        try {
            val file = getLogFile(context)
            if (!file.exists()) return logs
            
            BufferedReader(FileReader(file)).use { reader ->
                reader.forEachLine { line ->
                    try {
                        val parts = line.split("|", limit = 4)
                        if (parts.size >= 3) {
                            val timestamp = parts[0].toLongOrNull() ?: return@forEachLine
                            val type = try { LogType.valueOf(parts[1]) } catch (e: Exception) { LogType.INFO }
                            val message = parts[2].replace("\\|", "|")
                            val tag = if (parts.size > 3 && parts[3].isNotEmpty()) parts[3] else null
                            logs.add(LogEntry(timestamp, type, message, tag))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing log line: $line", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading logs", e)
        }
        return logs.sortedByDescending { it.timestamp }
    }
    
    /**
     * Clear all logs
     */
    @Synchronized
    fun clearLogs(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing logs", e)
        }
    }
    
    /**
     * Get logs as formatted string for sharing
     */
    fun getLogsAsText(context: Context): String {
        val logs = getLogs(context)
        if (logs.isEmpty()) return "No logs"
        
        val sb = StringBuilder()
        sb.appendLine("E621 Client - Following Log")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("================================")
        sb.appendLine()
        
        for (log in logs) {
            sb.append("[${log.getFormattedTime()}] ")
            sb.append("[${log.type}] ")
            sb.append(log.message)
            if (log.tag != null) {
                sb.append(" (tag: ${log.tag})")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE)
    }
    
    private fun trimLogFile(context: Context) {
        try {
            val logs = getLogs(context)
            if (logs.size > MAX_ENTRIES) {
                val toKeep = logs.take(MAX_ENTRIES)
                clearLogs(context)
                
                val file = getLogFile(context)
                BufferedWriter(FileWriter(file, true)).use { writer ->
                    // Write in reverse order so oldest is first
                    for (log in toKeep.reversed()) {
                        val entry = "${log.timestamp}|${log.type.name}|${log.message.replace("|", "\\|")}|${log.tag ?: ""}"
                        writer.write(entry)
                        writer.newLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming log file", e)
        }
    }
    
    // Convenience methods
    fun info(context: Context, message: String, tag: String? = null) = log(context, LogType.INFO, message, tag)
    fun success(context: Context, message: String, tag: String? = null) = log(context, LogType.SUCCESS, message, tag)
    fun warning(context: Context, message: String, tag: String? = null) = log(context, LogType.WARNING, message, tag)
    fun error(context: Context, message: String, tag: String? = null) = log(context, LogType.ERROR, message, tag)
    fun check(context: Context, message: String, tag: String? = null) = log(context, LogType.CHECK, message, tag)
    fun notification(context: Context, message: String, tag: String? = null) = log(context, LogType.NOTIFICATION, message, tag)
}

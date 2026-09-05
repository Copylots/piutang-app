package com.example.filemanager

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.Locale

data class LocalFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val isDirectory: Boolean
)

object FileManagerHelper {

    fun getDefaultScanDirectory(): File {
        // Return standard public downloads folder as a great working default directory
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun scanDirectory(
        directory: File,
        searchQuery: String = "",
        category: String = "ALL", // ALL, MEDIA, DOCUMENTS
        specificExtensions: List<String> = emptyList(), // e.g. ["pdf", "csv"]
        sortBy: String = "DATE_DESC" // DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, NAME_ASC, NAME_DESC
    ): List<LocalFileItem> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val files = directory.listFiles() ?: return emptyList()
        val resultList = mutableListOf<LocalFileItem>()

        for (file in files) {
            val isDir = file.isDirectory
            val name = file.name
            val ext = file.extension.lowercase()

            // Filter search query
            if (searchQuery.isNotEmpty() && !name.contains(searchQuery, ignoreCase = true)) {
                continue
            }

            // Filter category
            if (!isDir) {
                if (category == "MEDIA" && !isMediaFile(ext)) {
                    continue
                }
                if (category == "DOCUMENTS" && !isDocumentFile(ext)) {
                    continue
                }

                // Filter specific extensions
                if (specificExtensions.isNotEmpty() && !specificExtensions.contains(ext)) {
                    continue
                }
            } else if (category != "ALL") {
                // If filtering by categories, skip directories
                continue
            }

            resultList.add(
                LocalFileItem(
                    name = name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    extension = ext,
                    isDirectory = isDir
                )
            )
        }

        // Apply Sorting
        return when (sortBy) {
            "DATE_DESC" -> resultList.sortedByDescending { it.lastModified }
            "DATE_ASC" -> resultList.sortedBy { it.lastModified }
            "SIZE_DESC" -> resultList.sortedByDescending { it.size }
            "SIZE_ASC" -> resultList.sortedBy { it.size }
            "NAME_ASC" -> resultList.sortedBy { it.name.lowercase() }
            "NAME_DESC" -> resultList.sortedByDescending { it.name.lowercase() }
            else -> resultList
        }
    }

    fun isMediaFile(ext: String): Boolean {
        val mediaExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "mp4", "mkv", "mp3", "wav", "flac")
        return mediaExtensions.contains(ext)
    }

    fun isDocumentFile(ext: String): Boolean {
        val docExtensions = setOf("pdf", "csv", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf")
        return docExtensions.contains(ext)
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && !file.isDirectory) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun moveFile(path: String, destinationDir: File): File? {
        return try {
            val srcFile = File(path)
            if (srcFile.exists() && !srcFile.isDirectory) {
                if (!destinationDir.exists()) {
                    destinationDir.mkdirs()
                }
                val destFile = File(destinationDir, srcFile.name)
                srcFile.renameTo(destFile)
                destFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

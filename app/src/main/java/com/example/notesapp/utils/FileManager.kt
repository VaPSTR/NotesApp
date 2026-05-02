package com.example.notesapp.utils

import android.content.Context
import java.io.File

object FileManager {
    private val INVALID_CHARS = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")
    
    fun sanitizeFileName(name: String): String {
        return name.replace(INVALID_CHARS, "_").take(255)
    }
    
    fun generateFileName(tag: String, content: String): String {
        val cleanTag = sanitizeFileName(tag.take(16))
        val preview = if (content.length > 20) content.take(20) else content
        val cleanPreview = sanitizeFileName(preview)
        return "${cleanTag}_${cleanPreview}.txt"
    }
    
    suspend fun saveNote(
        context: Context,
        folderName: String,
        fileName: String,
        content: String
    ): Result<File> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val folder = File(context.getExternalFilesDir(null), folderName)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            
            val file = File(folder, fileName)
            file.writeText(content)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getFreeSpaceOnDevice(): Long {
        return File("/data").freeSpace
    }
    
    suspend fun getAllNotesFromFolder(folderPath: String): List<File> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val folder = File(folderPath)
        if (folder.exists() && folder.isDirectory) {
            folder.listFiles { file -> file.extension == "txt" }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
}

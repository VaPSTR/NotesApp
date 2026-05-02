package com.example.notesapp.network

import android.util.Log
import com.jcraft.jsch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class SSHClient {
    private var session: Session? = null
    private var channelSftp: ChannelSftp? = null
    
    suspend fun connect(
        host: String,
        username: String,
        password: String,
        port: Int = 22
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            session = jsch.getSession(username, host, port)
            session?.setPassword(password)
            
            val config = java.util.Properties()
            config.put("StrictHostKeyChecking", "no")
            session?.setConfig(config)
            
            session?.connect(30000)
            
            val channel = session?.openChannel("sftp")
            channel?.connect()
            channelSftp = channel as ChannelSftp
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SSH", "Connection failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun uploadFile(
        localFile: File,
        remotePath: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureDirectoryExists(remotePath.substringBeforeLast("/"))
            channelSftp?.put(FileInputStream(localFile), remotePath.substringAfterLast("/"))
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SSH", "Upload failed", e)
            Result.failure(e)
        }
    }
    
    private suspend fun ensureDirectoryExists(remoteDir: String) {
        try {
            channelSftp?.cd(remoteDir)
        } catch (e: SftpException) {
            val parts = remoteDir.split("/")
            var currentPath = ""
            for (part in parts) {
                if (part.isNotEmpty()) {
                    currentPath += "/$part"
                    try {
                        channelSftp?.cd(currentPath)
                    } catch (e: SftpException) {
                        channelSftp?.mkdir(currentPath)
                        channelSftp?.cd(currentPath)
                    }
                }
            }
        }
    }
    
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        channelSftp?.exit()
        session?.disconnect()
    }
}

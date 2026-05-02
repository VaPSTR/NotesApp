package com.example.notesapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 1,
    val userName: String = "",
    val pinCode: String = "",
    val storageFolder: String = "",
    val serverIp: String = "",
    val serverLogin: String = "",
    val serverPassword: String = "",
    val serverFolder: String = "",
    val isFirstLaunch: Boolean = true
)

package com.example.notesapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val tag: String,
    val content: String,
    val isCopiedToServer: Boolean = false,
    val savedAt: Date,
    val copiedAt: Date? = null
)

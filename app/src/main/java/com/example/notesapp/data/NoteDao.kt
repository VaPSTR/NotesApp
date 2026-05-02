package com.example.notesapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY savedAt DESC")
    fun getAllNotes(): Flow<List<Note>>
    
    @Insert
    suspend fun insertNote(note: Note)
    
    @Update
    suspend fun updateNote(note: Note)
    
    @Delete
    suspend fun deleteNote(note: Note)
    
    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
    
    @Query("SELECT * FROM notes WHERE fileName = :fileName")
    suspend fun getNoteByFileName(fileName: String): Note?
}

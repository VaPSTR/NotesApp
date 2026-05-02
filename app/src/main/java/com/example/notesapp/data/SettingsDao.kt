package com.example.notesapp.data

import androidx.room.*

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): Settings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: Settings)
    
    @Query("DELETE FROM settings WHERE id = 1")
    suspend fun deleteSettings()
}

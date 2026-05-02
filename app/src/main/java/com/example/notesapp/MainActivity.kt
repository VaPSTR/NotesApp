package com.example.notesapp

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesapp.data.*
import com.example.notesapp.network.SSHClient
import com.example.notesapp.utils.FileManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var sshClient: SSHClient
    private var currentPinAttempts = 0
    private var currentTag = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        db = AppDatabase.getInstance(this)
        sshClient = SSHClient()
        
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettings()
            if (settings?.isFirstLaunch == true || settings == null) {
                showFirstLaunchDialog()
            } else {
                showPinCodeDialog()
            }
        }
    }
    
    private fun showFirstLaunchDialog() {
        val inputLayout = LinearLayout(this)
        inputLayout.orientation = LinearLayout.VERTICAL
        inputLayout.setPadding(50, 20, 50, 20)
        
        val etName = EditText(this)
        etName.hint = "Ваше имя"
        inputLayout.addView(etName)
        
        val etPin = EditText(this)
        etPin.hint = "Пин-код (4 цифры)"
        etPin.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputLayout.addView(etPin)
        
        AlertDialog.Builder(this)
            .setTitle("Первый запуск")
            .setView(inputLayout)
            .setPositiveButton("Далее") { _, _ ->
                val name = etName.text.toString()
                val pin = etPin.text.toString()
                
                if (name.isBlank()) {
                    Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
                    showFirstLaunchDialog()
                    return@setPositiveButton
                }
                
                if (pin.length != 4 || !pin.all { it.isDigit() }) {
                    Toast.makeText(this, "Пин-код должен быть 4 цифры", Toast.LENGTH_SHORT).show()
                    showFirstLaunchDialog()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    val settings = Settings(
                        userName = name,
                        pinCode = pin,
                        isFirstLaunch = false
                    )
                    db.settingsDao().saveSettings(settings)
                    showSettingsScreen()
                }
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showPinCodeDialog() {
        val etPin = EditText(this)
        etPin.hint = "Введите пин-код"
        etPin.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        
        AlertDialog.Builder(this)
            .setTitle("Введите пин-код")
            .setView(etPin)
            .setPositiveButton("Войти") { _, _ ->
                val pin = etPin.text.toString()
                lifecycleScope.launch {
                    val settings = db.settingsDao().getSettings()
                    if (settings?.pinCode == pin) {
                        currentPinAttempts = 0
                        showMainMenu()
                    } else {
                        currentPinAttempts++
                        if (currentPinAttempts >= 5) {
                            finishAffinity()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Неверный пин-код. Осталось попыток: ${5 - currentPinAttempts}",
                                Toast.LENGTH_SHORT
                            ).show()
                            showPinCodeDialog()
                        }
                    }
                }
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showMainMenu() {
        setContentView(R.layout.activity_main)
        
        findViewById<Button>(R.id.btnCreateNote).setOnClickListener {
            showCreateNoteScreen()
        }
        
        findViewById<Button>(R.id.btnSavedNotes).setOnClickListener {
            showSavedNotesScreen()
        }
        
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettingsScreen()
        }
        
        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }
    }
    
    private fun showCreateNoteScreen() {
        currentTag = ""
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_note, null)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val btnTag = dialogView.findViewById<Button>(R.id.btnTag)
        val tvTag = dialogView.findViewById<TextView>(R.id.tvTag)
        
        btnTag.setOnClickListener {
            showTagInputDialog { tag ->
                currentTag = tag
                tvTag.text = "Tag: $tag"
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Создать заметку")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                saveNote(etNote.text.toString(), currentTag)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun saveNote(content: String, tag: String) {
        if (content.isBlank()) {
            Toast.makeText(this, "Заметка не может быть пустой", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (content.toByteArray().size > 1048576) {
            Toast.makeText(this, "Заметка не может быть больше 1 МБ", Toast.LENGTH_SHORT).show()
            return
        }
        
        val freeSpace = FileManager.getFreeSpaceOnDevice()
        if (content.toByteArray().size > freeSpace) {
            Toast.makeText(this, "Недостаточно места для сохранения заметки", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettings()
            val folderName = "Documents/${settings?.storageFolder ?: "Notes"}"
            val finalTag = if (tag.isBlank()) "БезТега" else tag
            val fileName = FileManager.generateFileName(finalTag, content)
            
            val fileResult = FileManager.saveNote(this@MainActivity, folderName, fileName, content)
            
            if (fileResult.isSuccess) {
                val now = Date()
                val note = Note(
                    fileName = fileName,
                    tag = finalTag,
                    content = content,
                    isCopiedToServer = false,
                    savedAt = now
                )
                db.noteDao().insertNote(note)
                
                if (!settings?.serverIp.isNullOrBlank() &&
                    !settings?.serverLogin.isNullOrBlank() &&
                    !settings?.serverPassword.isNullOrBlank()
                ) {
                    copyToServer(fileName, content, settings!!, note)
                } else {
                    Toast.makeText(this@MainActivity, "Заметка сохранена в Смартфоне", Toast.LENGTH_SHORT).show()
                    showMainMenu()
                }
            } else {
                Toast.makeText(this@MainActivity, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun copyToServer(fileName: String, content: String, settings: Settings, note: Note) {
        val connection = sshClient.connect(
            settings.serverIp,
            settings.serverLogin,
            settings.serverPassword
        )
        
        if (connection.isSuccess) {
            val remotePath = "${settings.serverFolder}/$fileName"
            val localFile = FileManager.saveNote(this, "Documents/${settings.storageFolder}", fileName, content).getOrNull()
            
            if (localFile != null) {
                val upload = sshClient.uploadFile(localFile, remotePath)
                if (upload.isSuccess) {
                    note.isCopiedToServer = true
                    note.copiedAt = Date()
                    db.noteDao().updateNote(note)
                    Toast.makeText(this, "Заметка сохранена и скопирована на сервер", Toast.LENGTH_LONG).show()
                }
            }
            sshClient.disconnect()
        }
        showMainMenu()
    }
    
    private fun showSavedNotesScreen() {
        setContentView(R.layout.activity_saved_notes)
        
        val rvNotes = findViewById<RecyclerView>(R.id.rvNotes)
        rvNotes.layoutManager = LinearLayoutManager(this)
        
        val notesList = mutableListOf<Note>()
        val adapter = NoteAdapter(notesList) { note ->
            lifecycleScope.launch {
                note.isCopiedToServer = false
                db.noteDao().updateNote(note)
                showCreateNoteScreen()
            }
        }
        rvNotes.adapter = adapter
        
        lifecycleScope.launch {
            db.noteDao().getAllNotes().collect { notes ->
                notesList.clear()
                notesList.addAll(notes)
                adapter.notifyDataSetChanged()
            }
        }
        
        findViewById<Button>(R.id.btnCopyToServer).setOnClickListener {
            startCopyToServer()
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            showMainMenu()
        }
    }
    
    private fun startCopyToServer() {
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettings()
            if (settings?.serverIp.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "Настройки сервера не заданы", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val notes = db.noteDao().getAllNotes()
            val notesToCopy = notes.value.filter { !it.isCopiedToServer }
            
            if (notesToCopy.isEmpty()) {
                Toast.makeText(this@MainActivity, "Нет заметок для копирования", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val connection = sshClient.connect(
                settings.serverIp,
                settings.serverLogin,
                settings.serverPassword
            )
            
            if (connection.isSuccess) {
                var successCount = 0
                for (note in notesToCopy) {
                    val localFile = FileManager.saveNote(
                        this@MainActivity,
                        "Documents/${settings.storageFolder}",
                        note.fileName,
                        note.content
                    ).getOrNull()
                    
                    if (localFile != null) {
                        val remotePath = "${settings.serverFolder}/${note.fileName}"
                        val upload = sshClient.uploadFile(localFile, remotePath)
                        if (upload.isSuccess) {
                            note.isCopiedToServer = true
                            note.copiedAt = Date()
                            db.noteDao().updateNote(note)
                            successCount++
                        }
                    }
                }
                sshClient.disconnect()
                Toast.makeText(
                    this@MainActivity,
                    "Скопировано $successCount из ${notesToCopy.size} заметок",
                    Toast.LENGTH_LONG
                ).show()
            }
            showSavedNotesScreen()
        }
    }
    
    private fun showSettingsScreen() {
        setContentView(R.layout.activity_settings)
        
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettings()
            if (settings != null) {
                findViewById<EditText>(R.id.etUserName).setText(settings.userName)
                findViewById<EditText>(R.id.etPinCode).setText(settings.pinCode)
                findViewById<EditText>(R.id.etStorageFolder).setText(settings.storageFolder)
                findViewById<EditText>(R.id.etServerIp).setText(settings.serverIp)
                findViewById<EditText>(R.id.etServerLogin).setText(settings.serverLogin)
                findViewById<EditText>(R.id.etServerPassword).setText(settings.serverPassword)
                findViewById<EditText>(R.id.etServerFolder).setText(settings.serverFolder)
            }
        }
        
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSettings()
        }
        
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            showDeleteScreen()
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            showMainMenu()
        }
    }
    
    private fun saveSettings() {
        val userName = findViewById<EditText>(R.id.etUserName).text.toString()
        val pinCode = findViewById<EditText>(R.id.etPinCode).text.toString()
        val storageFolder = findViewById<EditText>(R.id.etStorageFolder).text.toString()
        
        if (userName.isBlank()) {
            Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (pinCode.length != 4 || !pinCode.all { it.isDigit() }) {
            Toast.makeText(this, "Введите Пин-код, 4 цифры", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (storageFolder.isBlank()) {
            Toast.makeText(this, "Нужно имя папки для хранения на Смартфоне", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val settings = Settings(
                userName = userName,
                pinCode = pinCode,
                storageFolder = storageFolder,
                serverIp = findViewById<EditText>(R.id.etServerIp).text.toString(),
                serverLogin = findViewById<EditText>(R.id.etServerLogin).text.toString(),
                serverPassword = findViewById<EditText>(R.id.etServerPassword).text.toString(),
                serverFolder = findViewById<EditText>(R.id.etServerFolder).text.toString(),
                isFirstLaunch = false
            )
            db.settingsDao().saveSettings(settings)
            Toast.makeText(this@MainActivity, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            showMainMenu()
        }
    }
    
    private fun showDeleteScreen() {
        val options = arrayOf("Удалить настройки", "Удалить заметки", "Удалить всё")
        AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmDelete("После подтверждения все Настройки будут удалены") {
                        lifecycleScope.launch {
                            val settings = db.settingsDao().getSettings()
                            if (settings != null) {
                                val settingsToSave = Settings(
                                    storageFolder = settings.storageFolder,
                                    isFirstLaunch = true
                                )
                                db.settingsDao().saveSettings(settingsToSave)
                            }
                            showMainMenu()
                        }
                    }
                    1 -> confirmDelete("После подтверждения все Заметки на смартфоне будут удалены") {
                        lifecycleScope.launch {
                            db.noteDao().deleteAllNotes()
                            showMainMenu()
                        }
                    }
                    2 -> confirmDelete("После подтверждения все Настройки и Заметки будут удалены") {
                        lifecycleScope.launch {
                            db.noteDao().deleteAllNotes()
                            db.settingsDao().deleteSettings()
                            showFirstLaunchDialog()
                        }
                    }
                }
            }
            .show()
    }
    
    private fun showTagInputDialog(onTagEntered: (String) -> Unit) {
        val etTag = EditText(this)
        etTag.hint = "Только буквы и цифры (макс 16)"
        
        AlertDialog.Builder(this)
            .setTitle("Введите Tag")
            .setView(etTag)
            .setPositiveButton("OK") { _, _ ->
                val tag = etTag.text.toString()
                if (tag.length <= 16 && tag.matches(Regex("[A-Za-z0-9]*"))) {
                    onTagEntered(tag)
                } else {
                    Toast.makeText(this, "Tag должен содержать только буквы/цифры и до 16 символов", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun confirmDelete(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Подтверждение")
            .setMessage(message)
            .setPositiveButton("Подтвердить") { _, _ -> onConfirm() }
            .setNegativeButton("ОТМЕНИТЬ", null)
            .show()
    }
    
    inner class NoteAdapter(
        private val notes: List<Note>,
        private val onItemClick: (Note) -> Unit
    ) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {
        
        inner class NoteViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(android.R.id.text1)
            val tvDate: TextView = itemView.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NoteViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return NoteViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = notes[position]
            holder.tvTitle.text = note.fileName.replace(".txt", "")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.tvDate.text = sdf.format(note.savedAt)
            holder.itemView.setOnClickListener { onItemClick(note) }
        }
        
        override fun getItemCount() = notes.size
    }
}

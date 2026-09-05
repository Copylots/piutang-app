package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.ActivityLogEntity
import com.example.database.PiutangEntity
import com.example.export.ExportHelper
import com.example.filemanager.FileManagerHelper
import com.example.filemanager.LocalFileItem
import com.example.repository.SinarMasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SinarMasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SinarMasRepository(application)

    // Piutang State
    private val _piutangSearchQuery = MutableStateFlow("")
    val piutangSearchQuery = _piutangSearchQuery.asStateFlow()

    val filteredPiutangList: StateFlow<List<PiutangEntity>> = repository.allPiutang
        .combine(_piutangSearchQuery) { list, query ->
            if (query.isEmpty()) {
                list
            } else {
                list.filter { it.nama.contains(query, ignoreCase = true) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Activity Logs State
    val allLogs: StateFlow<List<ActivityLogEntity>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var showEncryptedLogs by mutableStateOf(false)

    // File Manager State
    var currentDirectory by mutableStateOf(FileManagerHelper.getDefaultScanDirectory())
        private set

    var fileList by mutableStateOf<List<LocalFileItem>>(emptyList())
        private set

    var fileSearchQuery by mutableStateOf("")
        private set

    var fileCategory by mutableStateOf("ALL") // ALL, MEDIA, DOCUMENTS
        private set

    var specificExtensions by mutableStateOf<List<String>>(emptyList())
        private set

    var fileSortBy by mutableStateOf("DATE_DESC") // DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, NAME_ASC, NAME_DESC
        private set

    var isLoadingFiles by mutableStateOf(false)
        private set

    var selectedLogoPath by mutableStateOf("")

    // Security/2FA State
    var is2FAAuthenticated by mutableStateOf(false)
    var securityPin by mutableStateOf("1234") // Default PIN
    var enteredPin by mutableStateOf("")
    var pinError by mutableStateOf(false)

    // Export Status Toast State
    var exportStatusMessage by mutableStateOf<String?>(null)

    init {
        // Load default PIN from SharedPreferences
        val prefs = application.getSharedPreferences("sinar_mas_prefs", Context.MODE_PRIVATE)
        securityPin = prefs.getString("security_pin", "1234") ?: "1234"
        selectedLogoPath = prefs.getString("selected_logo_path", "") ?: ""
        
        // Scan initial files
        scanCurrentDirectory()
    }

    // Piutang operations
    fun setPiutangSearchQuery(query: String) {
        _piutangSearchQuery.value = query
    }

    fun addPiutang(nama: String, m0: Int, m1: Int, m2: Int, m3: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addPiutang(nama, m0, m1, m2, m3)
        }
    }

    fun updatePiutang(id: Int, nama: String, m0: Int, m1: Int, m2: Int, m3: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePiutang(id, nama, m0, m1, m2, m3)
        }
    }

    fun deletePiutang(piutang: PiutangEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePiutang(piutang)
        }
    }

    // Security Operations
    fun verifyPin(pin: String): Boolean {
        return if (pin == securityPin) {
            is2FAAuthenticated = true
            pinError = false
            enteredPin = ""
            true
        } else {
            pinError = true
            enteredPin = ""
            false
        }
    }

    fun updatePin(newPin: String) {
        if (newPin.length >= 4) {
            securityPin = newPin
            val prefs = getApplication<Application>().getSharedPreferences("sinar_mas_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("security_pin", newPin).apply()
            
            viewModelScope.launch {
                repository.logCustomAction("UBAH KEAMANAN", "Berhasil memperbarui PIN Otentikasi Dua Faktor (2FA).")
            }
        }
    }

    fun logout() {
        is2FAAuthenticated = false
    }

    // File Manager operations
    fun setScanDirectory(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            currentDirectory = dir
            scanCurrentDirectory()
        }
    }

    fun updateFileFilters(
        search: String = fileSearchQuery,
        category: String = fileCategory,
        extensions: List<String> = specificExtensions,
        sortBy: String = fileSortBy
    ) {
        fileSearchQuery = search
        fileCategory = category
        specificExtensions = extensions
        fileSortBy = sortBy
        scanCurrentDirectory()
    }

    fun scanCurrentDirectory() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingFiles = true
            // A tiny delayed loading to let visual indicator spinner shine beautifully
            delay(400)
            fileList = FileManagerHelper.scanDirectory(
                directory = currentDirectory,
                searchQuery = fileSearchQuery,
                category = fileCategory,
                specificExtensions = specificExtensions,
                sortBy = fileSortBy
            )
            isLoadingFiles = false
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = FileManagerHelper.deleteFile(path)
            if (success) {
                repository.logCustomAction("HAPUS FILE", "Menghapus file secara permanen di lokasi: $path")
                scanCurrentDirectory()
            }
        }
    }

    fun moveFile(path: String, destDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val movedFile = FileManagerHelper.moveFile(path, destDir)
            if (movedFile != null) {
                repository.logCustomAction("PINDAH FILE", "Memindahkan file ke: ${movedFile.absolutePath}")
                scanCurrentDirectory()
            }
        }
    }

    fun selectLogo(path: String) {
        selectedLogoPath = path
        val prefs = getApplication<Application>().getSharedPreferences("sinar_mas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_logo_path", path).apply()
        
        viewModelScope.launch {
            repository.logCustomAction("PILIH LOGO", "Mengonfigurasi logo cetak PDF kustom: ${File(path).name}")
        }
    }

    fun clearLogo() {
        selectedLogoPath = ""
        val prefs = getApplication<Application>().getSharedPreferences("sinar_mas_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("selected_logo_path").apply()
        
        viewModelScope.launch {
            repository.logCustomAction("PILIH LOGO", "Menghapus logo kustom. Cetak PDF akan menggunakan tajuk default.")
        }
    }

    // Export operations
    fun triggerExportCsv(context: Context, data: List<PiutangEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = ExportHelper.exportToCsv(context, data)
            if (file != null) {
                repository.logCustomAction("EKSPOR CSV", "Mengekspor laporan format CSV sukses. File disimpan di Downloads: ${file.name}")
                exportStatusMessage = "CSV berhasil disimpan di Downloads!"
            } else {
                exportStatusMessage = "Gagal mengekspor CSV."
            }
        }
    }

    fun triggerExportPdf(context: Context, data: List<PiutangEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = ExportHelper.exportToPdf(context, data, selectedLogoPath)
            if (file != null) {
                repository.logCustomAction("EKSPOR PDF", "Mengekspor laporan format PDF sukses. File disimpan di Downloads: ${file.name}")
                exportStatusMessage = "PDF berhasil disimpan di Downloads!"
            } else {
                exportStatusMessage = "Gagal mengekspor PDF."
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
            repository.logCustomAction("HAPUS LOG", "Seluruh riwayat aktivitas sistem dibersihkan secara aman.")
        }
    }
}

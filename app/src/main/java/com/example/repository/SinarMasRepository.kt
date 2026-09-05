package com.example.repository

import android.content.Context
import com.example.database.ActivityLogEntity
import com.example.database.AppDatabase
import com.example.database.PiutangEntity
import com.example.notification.NotificationHelper
import com.example.security.CryptoHelper
import kotlinx.coroutines.flow.Flow

class SinarMasRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val piutangDao = database.piutangDao()
    private val logDao = database.activityLogDao()

    val allPiutang: Flow<List<PiutangEntity>> = piutangDao.getAll()
    val allLogs: Flow<List<ActivityLogEntity>> = logDao.getAll()

    suspend fun addPiutang(nama: String, m0: Int, m1: Int, m2: Int, m3: Int) {
        val total = m0 + m1 + m2 + m3
        val piutang = PiutangEntity(nama = nama, m0 = m0, m1 = m1, m2 = m2, m3 = m3, total = total)
        piutangDao.insert(piutang)

        // Log action securely
        logAction(
            "TAMBAH DATA",
            "Menambahkan piutang meubel '$nama' dengan rincian Rp ${formatNumber(m0)} (0 bln), Rp ${formatNumber(m1)} (1 bln), Rp ${formatNumber(m2)} (2 bln), Rp ${formatNumber(m3)} (3 bln). Total: Rp ${formatNumber(total)}."
        )

        // Notify
        NotificationHelper.sendNotification(
            context,
            "Sinar Mas: Data Ditambahkan",
            "Piutang meubel '$nama' berhasil ditambahkan."
        )
    }

    suspend fun updatePiutang(id: Int, nama: String, m0: Int, m1: Int, m2: Int, m3: Int) {
        val total = m0 + m1 + m2 + m3
        val piutang = PiutangEntity(id = id, nama = nama, m0 = m0, m1 = m1, m2 = m2, m3 = m3, total = total)
        piutangDao.update(piutang)

        // Log action securely
        logAction(
            "UBAH DATA",
            "Mengubah piutang meubel '$nama' (ID: $id) menjadi Rp ${formatNumber(m0)} (0 bln), Rp ${formatNumber(m1)} (1 bln), Rp ${formatNumber(m2)} (2 bln), Rp ${formatNumber(m3)} (3 bln). Total baru: Rp ${formatNumber(total)}."
        )

        // Notify
        NotificationHelper.sendNotification(
            context,
            "Sinar Mas: Data Diperbarui",
            "Piutang meubel '$nama' berhasil diperbarui."
        )
    }

    suspend fun deletePiutang(piutang: PiutangEntity) {
        piutangDao.delete(piutang)

        // Log action securely
        logAction(
            "HAPUS DATA",
            "Menghapus piutang meubel '${piutang.nama}' dengan total piutang Rp ${formatNumber(piutang.total)}."
        )

        // Notify
        NotificationHelper.sendNotification(
            context,
            "Sinar Mas: Data Dihapus",
            "Piutang meubel '${piutang.nama}' berhasil dihapus."
        )
    }

    suspend fun deletePiutangById(id: Int, nama: String) {
        piutangDao.deleteById(id)

        // Log action securely
        logAction(
            "HAPUS DATA",
            "Menghapus piutang meubel '$nama' (ID: $id)."
        )

        // Notify
        NotificationHelper.sendNotification(
            context,
            "Sinar Mas: Data Dihapus",
            "Piutang meubel '$nama' berhasil dihapus."
        )
    }

    suspend fun logCustomAction(action: String, details: String) {
        logAction(action, details)
    }

    suspend fun clearLogs() {
        logDao.clearAll()
    }

    private suspend fun logAction(action: String, details: String) {
        val encryptedAction = CryptoHelper.encrypt(action)
        val encryptedDetails = CryptoHelper.encrypt(details)
        val log = ActivityLogEntity(action = encryptedAction, details = encryptedDetails)
        logDao.insert(log)
    }

    private fun formatNumber(num: Int): String {
        return "%,d".format(num).replace(",", ".")
    }
}

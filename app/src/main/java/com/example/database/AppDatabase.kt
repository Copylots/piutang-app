package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "piutang")
data class PiutangEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nama: String,
    val m0: Int,
    val m1: Int,
    val m2: Int,
    val m3: Int,
    val total: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val action: String, // Encrypted
    val timestamp: Long = System.currentTimeMillis(),
    val details: String // Encrypted
)

@Dao
interface PiutangDao {
    @Query("SELECT * FROM piutang ORDER BY nama ASC")
    fun getAll(): Flow<List<PiutangEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(piutang: PiutangEntity): Long

    @Update
    suspend fun update(piutang: PiutangEntity)

    @Delete
    suspend fun delete(piutang: PiutangEntity)

    @Query("DELETE FROM piutang WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM piutang")
    suspend fun deleteAll()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()
}

@Database(entities = [PiutangEntity::class, ActivityLogEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun piutangDao(): PiutangDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sinar_mas_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

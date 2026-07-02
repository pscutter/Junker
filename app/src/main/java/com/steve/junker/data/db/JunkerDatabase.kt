package com.steve.junker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handicap: Int
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val holeHandicaps: String, // Comma-separated integers
    val holePars: String = "4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4"
)

class Converters {
    @TypeConverter
    fun fromString(value: String): List<Int> {
        return if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
    }

    @TypeConverter
    fun fromList(list: List<Int>): String {
        return list.joinToString(",")
    }
}

@androidx.room.Dao
interface PlayerDao {
    @Query("SELECT * FROM players")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Delete
    suspend fun deletePlayer(player: PlayerEntity)
}

@androidx.room.Dao
interface CourseDao {
    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)
}

// FIX: Registered GameRoundEntity inside the entities index compilation array and bumped version to 2
@Database(
    entities = [
        PlayerEntity::class, 
        CourseEntity::class, 
        GameRoundEntity::class
    ], 
    version = 2, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JunkerDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun courseDao(): CourseDao
    abstract fun gameRoundDao(): GameRoundDao

    companion object {
        @Volatile
        private var INSTANCE: JunkerDatabase? = null

        fun getDatabase(context: Context): JunkerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JunkerDatabase::class.java,
                    "junker_database"
                )
                .fallbackToDestructiveMigration() // Automatically refreshes local phone storage safely on re-link
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
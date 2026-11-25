package com.steve.junker.data.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class AirtableRecord<T>(
    val id: String? = null,
    val fields: T
)

data class AirtableResponse<T>(
    val records: List<AirtableRecord<T>>,
    val offset: String? = null
)

data class AirtableUpsertRequest<T>(
    val performUpsert: AirtablePerformUpsert,
    val records: List<AirtableRecord<T>>
)

data class AirtablePerformUpsert(
    val fieldsToMergeOn: List<String>
)

data class PlayerFields(
    val id: String? = null,
    val name: String? = null,
    val handicap: Int? = null
)

data class CourseFields(
    val id: String? = null,
    val name: String? = null,
    val holeHandicaps: String? = null,
    val holePars: String? = null
)

data class GameRoundFields(
    val id: String? = null,
    val dateLong: Long? = null,
    val courseName: String? = null,
    val playersNamesWithDots: String? = null,
    val scoreSummary: String? = null,
    val rawScoresJson: String? = null
)

interface AirtableApiService {

    // --- PLAYERS ---
    @GET("players")
    suspend fun getPlayers(
        @Header("Authorization") auth: String,
        @Query("filterByFormula") filter: String? = null,
        @Query("offset") offset: String? = null
    ): AirtableResponse<PlayerFields>

    @PATCH("players")
    suspend fun upsertPlayers(
        @Header("Authorization") auth: String,
        @Body body: AirtableUpsertRequest<PlayerFields>
    ): AirtableResponse<PlayerFields>

    @DELETE("players/{recordId}")
    suspend fun deletePlayer(
        @Header("Authorization") auth: String,
        @Path("recordId") recordId: String
    ): AirtableRecord<PlayerFields>

    // --- COURSES ---
    @GET("courses")
    suspend fun getCourses(
        @Header("Authorization") auth: String,
        @Query("filterByFormula") filter: String? = null,
        @Query("offset") offset: String? = null
    ): AirtableResponse<CourseFields>

    @PATCH("courses")
    suspend fun upsertCourses(
        @Header("Authorization") auth: String,
        @Body body: AirtableUpsertRequest<CourseFields>
    ): AirtableResponse<CourseFields>

    @DELETE("courses/{recordId}")
    suspend fun deleteCourse(
        @Header("Authorization") auth: String,
        @Path("recordId") recordId: String
    ): AirtableRecord<CourseFields>

    // --- GAME ROUNDS ---
    @GET("game_rounds")
    suspend fun getRounds(
        @Header("Authorization") auth: String,
        @Query("filterByFormula") filter: String? = null,
        @Query("offset") offset: String? = null
    ): AirtableResponse<GameRoundFields>

    @PATCH("game_rounds")
    suspend fun upsertRounds(
        @Header("Authorization") auth: String,
        @Body body: AirtableUpsertRequest<GameRoundFields>
    ): AirtableResponse<GameRoundFields>

    @DELETE("game_rounds/{recordId}")
    suspend fun deleteRound(
        @Header("Authorization") auth: String,
        @Path("recordId") recordId: String
    ): AirtableRecord<GameRoundFields>

    companion object {
        fun create(): AirtableApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val baseUrl = "https://api.airtable.com/v0/${AirtableConfig.BASE_ID}/"
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AirtableApiService::class.java)
        }
    }
}

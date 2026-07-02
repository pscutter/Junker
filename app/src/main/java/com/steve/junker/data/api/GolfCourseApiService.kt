package com.steve.junker.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// --- Enclosed Envelope Data Mappings ---
data class ApiSearchEnvelope(
    val courses: List<ApiSearchResponse>?
)

data class ApiSearchResponse(
    val id: Int, 
    val club_name: String?, 
    val course_name: String?
)

data class ApiCourseDetailResponse(
    val course: ApiCourseWrapper? // Detail endpoint returns a wrapper around the individual course metrics
)

data class ApiCourseWrapper(
    val id: Int,
    val course_name: String?,
    val tees: ApiTeesWrapper?
)

data class ApiTeesWrapper(
    val male: List<ApiTeeBox>?,
    val female: List<ApiTeeBox>?
)

data class ApiTeeBox(
    val tee_name: String?,
    val holes: List<ApiHoleDetail>?
)

data class ApiHoleDetail(
    val par: Int,
    val yardage: Int,
    val handicap: Int
)

// --- Retrofit Service Endpoint Rules ---
interface GolfCourseApiService {
    
    // FIX: Updated return layout constraint tracking matching the root envelope shape
    @GET("v1/search")
    suspend fun searchCourses(
        @Query("search_query") query: String,
        @Header("Authorization") apiKey: String = "Key 2YYINVXX4IJLTOGUB3JGYEUDHU" // FIX: Adjusted to use custom "Key " prefix
    ): ApiSearchEnvelope

    @GET("v1/courses/{id}")
    suspend fun getCourseDetails(
        @Path("id") courseId: Int,
        @Header("Authorization") apiKey: String = "Key 2YYINVXX4IJLTOGUB3JGYEUDHU"
    ): ApiCourseDetailResponse

    companion object {
        private const val BASE_URL = "https://api.golfcourseapi.com/"

        fun create(): GolfCourseApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GolfCourseApiService::class.java)
        }
    }
}
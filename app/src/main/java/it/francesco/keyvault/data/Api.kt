package it.francesco.keyvault.data

import android.content.Context
import it.francesco.keyvault.security.TokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

const val DEFAULT_BACKEND_URL = "https://backend-passwordmanager.onrender.com/"

data class AuthRequest(val email: String, val password: String)
data class AuthResponse(val token: String)
data class MeResponse(val email: String)
data class CredentialRequest(
    val nomeSito: String,
    val urlSito: String,
    val username: String,
    val password: String
)
data class Credential(
    val id: Long,
    val nomeSito: String,
    val urlSito: String,
    val username: String,
    val password: String
)

interface PasswordApi {
    @POST("api/utente/register")
    suspend fun register(@Body body: AuthRequest): retrofit2.Response<okhttp3.ResponseBody>

    @POST("api/utente/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    @GET("api/utente/me")
    suspend fun me(): MeResponse

    @GET("api/credenziali")
    suspend fun credentials(): List<Credential>

    @POST("api/credenziali")
    suspend fun createCredential(@Body body: CredentialRequest): retrofit2.Response<okhttp3.ResponseBody>

    @PUT("api/credenziali/{id}")
    suspend fun updateCredential(@Path("id") id: Long, @Body body: CredentialRequest): Credential

    @DELETE("api/credenziali/{id}")
    suspend fun deleteCredential(@Path("id") id: Long): retrofit2.Response<okhttp3.ResponseBody>
}

object ApiFactory {
    fun create(context: Context, baseUrl: String): PasswordApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val auth = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                TokenStore(context).get()?.let { header("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder().addInterceptor(auth).build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PasswordApi::class.java)
    }
}

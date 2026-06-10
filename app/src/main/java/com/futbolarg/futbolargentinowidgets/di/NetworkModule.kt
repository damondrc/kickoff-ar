package com.futbolarg.futbolargentinowidgets.di

import com.futbolarg.futbolargentinowidgets.BuildConfig
import com.futbolarg.futbolargentinowidgets.data.remote.api.EspnApiService
import com.futbolarg.futbolargentinowidgets.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// ============================================================
// NetworkModule.kt
// ============================================================
// Recetas de Hilt para los objetos de red.
//
// CAMBIO vs. versión anterior: la API de ESPN es pública, así
// que desapareció el interceptor de autenticación con API key.
// Solo queda logging (en debug) y timeouts.
// ============================================================

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ========================================================
    // OKHTTP CLIENT
    // ========================================================
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {

        // Logging: muestra requests/responses en Logcat (solo debug)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ========================================================
    // RETROFIT
    // ========================================================
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.ESPN_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ========================================================
    // API SERVICE
    // ========================================================
    @Provides
    @Singleton
    fun provideEspnApiService(
        retrofit: Retrofit
    ): EspnApiService {
        return retrofit.create(EspnApiService::class.java)
    }
}

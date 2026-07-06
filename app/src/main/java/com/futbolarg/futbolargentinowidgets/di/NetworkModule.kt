package com.futbolarg.futbolargentinowidgets.di

import com.futbolarg.futbolargentinowidgets.BuildConfig
import com.futbolarg.futbolargentinowidgets.data.remote.api.EspnApiService
import com.futbolarg.futbolargentinowidgets.data.remote.api.KickoffApiService
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
    // API SERVICE — ESPN (fuente directa)
    // ========================================================
    @Provides
    @Singleton
    fun provideEspnApiService(
        retrofit: Retrofit
    ): EspnApiService {
        return retrofit.create(EspnApiService::class.java)
    }

    // ========================================================
    // API SERVICE — Proxy (nuestro Cloudflare Worker)
    // ========================================================
    // Segunda instancia de Retrofit porque la base URL es otra,
    // pero REUTILIZA el mismo OkHttpClient (timeouts, logging y
    // pool de conexiones compartidos). Se crea siempre, pero si
    // USE_PROXY es false nunca se usa — crear el objeto es
    // barato, lo costoso serían las llamadas.
    // ========================================================
    @Provides
    @Singleton
    fun provideKickoffApiService(
        okHttpClient: OkHttpClient
    ): KickoffApiService {
        return Retrofit.Builder()
            .baseUrl(Constants.PROXY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KickoffApiService::class.java)
    }
}

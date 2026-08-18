package com.tvproxy.app.core.di

import com.squareup.moshi.Moshi
import com.tvproxy.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Shared network plumbing (architecture.md §2): one OkHttpClient (connection pool,
 * timeouts) and one Moshi (codegen adapters only — no kotlin-reflect in the APK).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_S = 15L
    private const val READ_TIMEOUT_S = 60L // XMLTV feeds of 100k+ programmes can be slow

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor { chain ->
                    val request = chain.request()
                    Timber.d("HTTP %s %s", request.method, request.url)
                    chain.proceed(request)
                }
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()
}

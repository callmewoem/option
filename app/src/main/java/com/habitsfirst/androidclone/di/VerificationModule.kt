package com.habitsfirst.androidclone.di

import com.habitsfirst.androidclone.data.verification.AnthropicImageVerificationClient
import com.habitsfirst.androidclone.data.verification.ImageVerificationClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VerificationModule {

    @Binds
    abstract fun bindImageVerificationClient(impl: AnthropicImageVerificationClient): ImageVerificationClient
}

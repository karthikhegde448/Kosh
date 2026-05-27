package com.rejowan.pdfreaderpro.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.rejowan.pdfreaderpro.util.TesseractManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    single {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(get())
            }
            engine {
                connectTimeout = 15_000
                socketTimeout = 15_000
            }
        }
    }

    single { TesseractManager(context = androidContext()) }
}

package com.rejowan.pdfreaderpro.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_cache",
    indices = [Index(value = ["word"], unique = true)]
)
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val definitionsJson: String,   // JSON blob from API stored as-is
    val savedOffline: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

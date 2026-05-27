package com.rejowan.pdfreaderpro.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rejowan.pdfreaderpro.data.local.database.entity.DictionaryEntity

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_cache WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): DictionaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryEntity)

    @Query("UPDATE dictionary_cache SET savedOffline = 1 WHERE word = :word")
    suspend fun markOffline(word: String)

    @Query("SELECT * FROM dictionary_cache WHERE savedOffline = 1 ORDER BY word ASC")
    suspend fun getOfflineWords(): List<DictionaryEntity>

    @Query("DELETE FROM dictionary_cache WHERE savedOffline = 0 AND cachedAt < :before")
    suspend fun pruneOldCache(before: Long)
}

package com.finapp.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/** Converte LocalDate <-> Long (dias desde a época) para o SQLite. */
class Converters {

    @TypeConverter
    fun deEpochDay(valor: Long?): LocalDate? = valor?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun paraEpochDay(data: LocalDate?): Long? = data?.toEpochDay()
}

package com.jax.automation.database

import androidx.room.TypeConverter

/**
 * Room type converters. List<String> is stored as unit-separator-delimited
 * text (no JSON dependency needed in the persistence layer).
 */
class Converters {
    private val sep = ""

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        (value ?: emptyList()).joinToString(sep)

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(sep)
}

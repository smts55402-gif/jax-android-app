package com.jax.automation.database

import androidx.room.TypeConverter
import com.jax.automation.models.QaStatus
import com.jax.automation.models.ReferenceCategory
import com.jax.automation.models.TaskStatus

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

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromReferenceCategory(value: ReferenceCategory): String = value.name

    @TypeConverter
    fun toReferenceCategory(value: String): ReferenceCategory =
        ReferenceCategory.valueOf(value)

    @TypeConverter
    fun fromQaStatus(value: QaStatus): String = value.name

    @TypeConverter
    fun toQaStatus(value: String): QaStatus = QaStatus.valueOf(value)
}

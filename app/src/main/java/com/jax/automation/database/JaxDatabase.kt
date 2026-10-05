package com.jax.automation.database

import android.content.Context
import androidx.room.Room

/** Process-wide singleton for the JAX Room database. */
object JaxDatabase {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "jax.db"
            ).build().also { instance = it }
        }
}

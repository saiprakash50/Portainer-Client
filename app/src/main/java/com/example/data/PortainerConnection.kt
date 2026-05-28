package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portainer_connections")
data class PortainerConnection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM portainer_connections ORDER BY createdAt DESC")
    fun getAllConnections(): Flow<List<PortainerConnection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: PortainerConnection)

    @Update
    suspend fun updateConnection(connection: PortainerConnection)

    @Delete
    suspend fun deleteConnection(connection: PortainerConnection)

    @Query("SELECT * FROM portainer_connections WHERE id = :id LIMIT 1")
    suspend fun getConnectionById(id: Int): PortainerConnection?
}

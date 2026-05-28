package com.example.data

import kotlinx.coroutines.flow.Flow

class ConnectionRepository(private val connectionDao: ConnectionDao) {
    val allConnections: Flow<List<PortainerConnection>> = connectionDao.getAllConnections()

    suspend fun insert(connection: PortainerConnection) {
        connectionDao.insertConnection(connection)
    }

    suspend fun update(connection: PortainerConnection) {
        connectionDao.updateConnection(connection)
    }

    suspend fun delete(connection: PortainerConnection) {
        connectionDao.deleteConnection(connection)
    }

    suspend fun getById(id: Int): PortainerConnection? {
        return connectionDao.getConnectionById(id)
    }
}

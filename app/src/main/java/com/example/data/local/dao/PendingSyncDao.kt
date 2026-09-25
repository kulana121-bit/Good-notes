package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.PendingSyncOperation
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PendingSyncDao {

    @Query("SELECT * FROM pending_sync_operations WHERE userId = :userId ORDER BY createdAt ASC")
    fun getAllPendingOperations(userId: String): Flow<List<PendingSyncOperation>>

    @Query("SELECT * FROM pending_sync_operations WHERE userId = :userId ORDER BY createdAt ASC")
    suspend fun getAllPendingOperationsDirect(userId: String): List<PendingSyncOperation>

    @Query("SELECT * FROM pending_sync_operations WHERE userId = :userId AND entityId = :entityId")
    suspend fun getPendingOperationsForEntity(userId: String, entityId: String): List<PendingSyncOperation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: PendingSyncOperation)

    @Query("DELETE FROM pending_sync_operations WHERE operationId = :operationId")
    suspend fun deleteOperation(operationId: String)

    @Query("DELETE FROM pending_sync_operations WHERE userId = :userId AND entityId = :entityId")
    suspend fun deleteOperationsForEntity(userId: String, entityId: String)

    @Query("DELETE FROM pending_sync_operations WHERE userId = :userId")
    suspend fun clearOperationsForUser(userId: String)

    @Query("DELETE FROM pending_sync_operations")
    suspend fun clearAllOperations()

    @Query("SELECT COUNT(*) FROM pending_sync_operations WHERE userId = :userId")
    fun getPendingCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_operations")
    fun getGlobalPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_operations WHERE userId = :userId")
    suspend fun getPendingCountDirect(userId: String): Int

    @Query("UPDATE pending_sync_operations SET retryCount = :retryCount, lastError = :lastError WHERE operationId = :operationId")
    suspend fun updateRetry(operationId: String, retryCount: Int, lastError: String?)

    /**
     * Enqueues an operation with intelligent offline operation coalescing:
     * - CREATE + UPDATE -> CREATE (with updated timestamp/payload)
     * - CREATE + DELETE -> REMOVE (never sent to cloud, safe to discard)
     * - UPDATE + UPDATE -> UPDATE (latest state)
     * - UPDATE + DELETE -> DELETE
     * - DELETE + CREATE -> UPDATE
     */
    @Transaction
    open suspend fun enqueueCoalesced(
        userId: String = "",
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String? = null
    ) {
        val existingOps = getPendingOperationsForEntity(userId, entityId)
        if (existingOps.isEmpty()) {
            insertOperation(
                PendingSyncOperation(
                    operationId = "op_${UUID.randomUUID().toString().take(12)}",
                    userId = userId,
                    entityType = entityType,
                    entityId = entityId,
                    operationType = operationType,
                    createdAt = System.currentTimeMillis(),
                    payloadJson = payloadJson
                )
            )
            return
        }

        val primaryOp = existingOps.first()
        when (primaryOp.operationType) {
            "CREATE" -> {
                when (operationType) {
                    "UPDATE" -> {
                        // Coalesce into existing CREATE with updated payload & timestamp
                        insertOperation(
                            primaryOp.copy(
                                createdAt = System.currentTimeMillis(),
                                payloadJson = payloadJson ?: primaryOp.payloadJson
                            )
                        )
                    }
                    "DELETE" -> {
                        // Transition to DELETE tombstone operation so cloud records the deletion
                        insertOperation(
                            primaryOp.copy(
                                operationType = "DELETE",
                                createdAt = System.currentTimeMillis(),
                                payloadJson = payloadJson ?: primaryOp.payloadJson
                            )
                        )
                    }
                }
            }
            "UPDATE" -> {
                when (operationType) {
                    "UPDATE" -> {
                        // Coalesce: latest UPDATE
                        insertOperation(
                            primaryOp.copy(
                                createdAt = System.currentTimeMillis(),
                                payloadJson = payloadJson ?: primaryOp.payloadJson
                            )
                        )
                    }
                    "DELETE" -> {
                        // Transition to DELETE
                        insertOperation(
                            primaryOp.copy(
                                operationType = "DELETE",
                                createdAt = System.currentTimeMillis(),
                                payloadJson = payloadJson
                            )
                        )
                    }
                }
            }
            "DELETE" -> {
                when (operationType) {
                    "CREATE", "UPDATE" -> {
                        // Resurrected or re-created
                        insertOperation(
                            primaryOp.copy(
                                operationType = "UPDATE",
                                createdAt = System.currentTimeMillis(),
                                payloadJson = payloadJson
                            )
                        )
                    }
                }
            }
        }
    }
}

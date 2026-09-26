package com.example.core.dtn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DtnBundleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DtnBundleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBundle(bundle: DtnBundleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBundle(bundle: DtnBundleEntity)

    @Update
    suspend fun updateBundle(bundle: DtnBundleEntity)

    @Query("SELECT * FROM dtn_bundles WHERE bundleId = :bundleId LIMIT 1")
    suspend fun getBundleById(bundleId: String): DtnBundleEntity?

    @Query("SELECT * FROM dtn_bundles ORDER BY createdAtEpochMs DESC")
    fun observeAllBundles(): Flow<List<DtnBundleEntity>>

    @Query("SELECT * FROM dtn_bundles WHERE status = 'PENDING_CARRIED' AND expiresAtEpochMs > :currentTime ORDER BY priority DESC, createdAtEpochMs ASC")
    suspend fun getPendingCarriedBundles(currentTime: Long): List<DtnBundleEntity>

    @Query("SELECT bundleId FROM dtn_bundles WHERE status != 'EXPIRED' AND expiresAtEpochMs > :currentTime")
    suspend fun getActiveBundleIds(currentTime: Long): List<String>

    @Query("UPDATE dtn_bundles SET status = :status WHERE bundleId = :bundleId")
    suspend fun updateBundleStatus(bundleId: String, status: BundleStatus)

    @Query("DELETE FROM dtn_bundles WHERE bundleId = :bundleId")
    suspend fun deleteBundle(bundleId: String)

    /**
     * Evicts expired bundles based on TTL decay.
     */
    @Query("UPDATE dtn_bundles SET status = 'EXPIRED' WHERE expiresAtEpochMs <= :currentTime AND status != 'EXPIRED'")
    suspend fun markExpiredBundles(currentTime: Long): Int

    @Query("DELETE FROM dtn_bundles WHERE expiresAtEpochMs <= :currentTime")
    suspend fun purgeExpiredBundles(currentTime: Long): Int

    /**
     * Priority & buffer eviction query:
     * Returns bundles ordered from lowest priority and oldest creation date first.
     */
    @Query("SELECT * FROM dtn_bundles WHERE status = 'PENDING_CARRIED' ORDER BY priority ASC, createdAtEpochMs ASC LIMIT :count")
    suspend fun getCandidateBundlesForEviction(count: Int): List<DtnBundleEntity>

    @Query("SELECT COUNT(*) FROM dtn_bundles WHERE status = 'PENDING_CARRIED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT SUM(payloadSizeBytes) FROM dtn_bundles WHERE status = 'PENDING_CARRIED'")
    fun observeTotalCarriedBytes(): Flow<Long?>
}

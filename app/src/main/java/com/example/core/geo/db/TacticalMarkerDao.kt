package com.example.core.geo.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.core.geo.TacticalMarker
import com.example.core.geo.TacticalMarkerType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tactical_markers")
data class TacticalMarkerEntity(
    @PrimaryKey
    val markerId: String,
    val type: String,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val creatorNodeId: String,
    val creatorAlias: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val isResolved: Boolean
) {
    fun toDomain(): TacticalMarker {
        val markerType = try {
            TacticalMarkerType.valueOf(type)
        } catch (e: Exception) {
            TacticalMarkerType.HAZARD
        }
        return TacticalMarker(
            markerId = markerId,
            type = markerType,
            title = title,
            description = description,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            accuracyMeters = accuracyMeters,
            creatorNodeId = creatorNodeId,
            creatorAlias = creatorAlias,
            createdAtMs = createdAtMs,
            expiresAtMs = expiresAtMs,
            isResolved = isResolved
        )
    }

    companion object {
        fun fromDomain(domain: TacticalMarker): TacticalMarkerEntity {
            return TacticalMarkerEntity(
                markerId = domain.markerId,
                type = domain.type.name,
                title = domain.title,
                description = domain.description,
                latitude = domain.latitude,
                longitude = domain.longitude,
                altitudeMeters = domain.altitudeMeters,
                accuracyMeters = domain.accuracyMeters,
                creatorNodeId = domain.creatorNodeId,
                creatorAlias = domain.creatorAlias,
                createdAtMs = domain.createdAtMs,
                expiresAtMs = domain.expiresAtMs,
                isResolved = domain.isResolved
            )
        }
    }
}

@Entity(tableName = "breadcrumb_tracks")
data class BreadcrumbEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val nodeId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedMps: Float,
    val bearingDegrees: Float,
    val timestampMs: Long
)

@Dao
interface TacticalMarkerDao {
    @Query("SELECT * FROM tactical_markers WHERE expiresAtMs > :nowMs ORDER BY createdAtMs DESC")
    fun getActiveMarkersFlow(nowMs: Long): Flow<List<TacticalMarkerEntity>>

    @Query("SELECT * FROM tactical_markers ORDER BY createdAtMs DESC")
    suspend fun getAllMarkers(): List<TacticalMarkerEntity>

    @Query("SELECT * FROM tactical_markers WHERE markerId = :markerId")
    suspend fun getMarkerById(markerId: String): TacticalMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(marker: TacticalMarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(markers: List<TacticalMarkerEntity>)

    @Query("UPDATE tactical_markers SET isResolved = :resolved WHERE markerId = :markerId")
    suspend fun setResolved(markerId: String, resolved: Boolean)

    @Query("DELETE FROM tactical_markers WHERE markerId = :markerId")
    suspend fun deleteById(markerId: String)

    @Query("DELETE FROM tactical_markers WHERE expiresAtMs <= :nowMs")
    suspend fun purgeExpired(nowMs: Long)

    @Query("DELETE FROM tactical_markers")
    suspend fun clearAll()
}

@Dao
interface BreadcrumbDao {
    @Query("SELECT * FROM breadcrumb_tracks WHERE nodeId = :nodeId ORDER BY timestampMs ASC LIMIT :maxPoints")
    fun getTrackPointsFlow(nodeId: String, maxPoints: Int = 200): Flow<List<BreadcrumbEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: BreadcrumbEntity)

    @Query("DELETE FROM breadcrumb_tracks WHERE nodeId = :nodeId")
    suspend fun clearTrackForNode(nodeId: String)

    @Query("DELETE FROM breadcrumb_tracks")
    suspend fun clearAll()
}

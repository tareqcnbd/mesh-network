package com.example.core.dtn.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.core.crypto.DatabasePassphraseManager
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.DeliveryAckEntity
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.geo.db.BreadcrumbDao
import com.example.core.geo.db.BreadcrumbEntity
import com.example.core.geo.db.TacticalMarkerDao
import com.example.core.geo.db.TacticalMarkerEntity
import com.example.core.media.db.MediaChunkEntity
import com.example.core.media.db.MediaTransferDao
import com.example.core.media.db.MediaTransferEntity
import com.example.core.reputation.db.PeerReputationDao
import com.example.core.reputation.db.PeerReputationEntity
import com.example.core.reputation.db.ReputationAuditLogEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        DtnBundleEntity::class,
        DeliveryAckEntity::class,
        MeshPeerEntity::class,
        ChatMessageEntity::class,
        TacticalMarkerEntity::class,
        BreadcrumbEntity::class,
        MediaTransferEntity::class,
        MediaChunkEntity::class,
        PeerReputationEntity::class,
        ReputationAuditLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(DtnTypeConverters::class)
abstract class MeshDatabase : RoomDatabase() {

    abstract fun dtnBundleDao(): DtnBundleDao
    abstract fun deliveryAckDao(): DeliveryAckDao
    abstract fun meshPeerDao(): MeshPeerDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun tacticalMarkerDao(): TacticalMarkerDao
    abstract fun breadcrumbDao(): BreadcrumbDao
    abstract fun mediaTransferDao(): MediaTransferDao
    abstract fun peerReputationDao(): PeerReputationDao

    companion object {
        private const val DB_NAME = "mesh_network_encrypted.db"

        @Volatile
        private var INSTANCE: MeshDatabase? = null

        fun getInstance(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildEncryptedDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private fun buildEncryptedDatabase(context: Context): MeshDatabase {
            val passphrase = DatabasePassphraseManager.getOrCreatePassphrase(context)
            val openHelperFactory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context,
                MeshDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(openHelperFactory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

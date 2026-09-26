package com.example.core.dtn.db

import androidx.room.TypeConverter
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus

class DtnTypeConverters {

    @TypeConverter
    fun fromPriority(priority: BundlePriority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): BundlePriority = try {
        BundlePriority.valueOf(value)
    } catch (_: Exception) {
        BundlePriority.NORMAL
    }

    @TypeConverter
    fun fromStatus(status: BundleStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): BundleStatus = try {
        BundleStatus.valueOf(value)
    } catch (_: Exception) {
        BundleStatus.PENDING_CARRIED
    }
}

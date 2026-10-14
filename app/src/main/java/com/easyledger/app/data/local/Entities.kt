package com.easyledger.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultMode: String,
    val defaultUnit: String,
    val defaultPrice: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "''") val note: String = "",
)

@Entity(tableName = "work_records", foreignKeys = [ForeignKey(
    entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.RESTRICT
)], indices = [Index(value = ["projectId", "date", "deletedAt"])])
data class WorkRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val date: String,
    val mode: String,
    val unit: String,
    val quantity: String,
    val unitPrice: String,
    val amount: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val actualMinutes: Long? = null,
    val quantitySource: String = "MANUAL",
    val calculationPolicyVersion: Int = 1,
)

@Entity(tableName = "preferences")
data class PreferenceEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "work_segments", foreignKeys = [ForeignKey(
    entity = WorkRecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.RESTRICT
)], indices = [Index(value = ["recordId", "position"], unique = true)])
data class WorkSegmentEntity(
    @PrimaryKey val id: String, val recordId: String, val position: Int,
    val startDateTime: String, val endDateTime: String,
)

data class RecordWithProject(
    @androidx.room.Embedded val record: WorkRecordEntity,
    val projectName: String,
    val projectDeletedAt: Long?,
)

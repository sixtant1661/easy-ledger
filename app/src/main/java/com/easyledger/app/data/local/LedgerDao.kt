package com.easyledger.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("""SELECT * FROM projects WHERE deletedAt IS NULL AND
        (:keyword = '' OR instr(lower(name), lower(:keyword)) > 0 OR instr(lower(note), lower(:keyword)) > 0)
        ORDER BY createdAt DESC, id""")
    fun projects(keyword: String): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects ORDER BY createdAt DESC, id")
    fun allProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun project(id: String): ProjectEntity?
    @Insert suspend fun insertProject(project: ProjectEntity)
    @Update suspend fun updateProject(project: ProjectEntity): Int
    @Query("UPDATE projects SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun deleteProject(id: String, now: Long): Int
    @Query("""SELECT r.*, p.name AS projectName, p.deletedAt AS projectDeletedAt FROM work_records r
        JOIN projects p ON r.projectId = p.id WHERE r.deletedAt IS NULL AND
        (:keyword = '' OR instr(lower(p.name), lower(:keyword)) > 0 OR instr(lower(r.note), lower(:keyword)) > 0)
        ORDER BY r.date DESC, r.createdAt DESC, r.id""")
    fun records(keyword: String): Flow<List<RecordWithProject>>
    @Query("SELECT * FROM work_records WHERE id = :id") suspend fun record(id: String): WorkRecordEntity?
    @Insert suspend fun insertRecord(record: WorkRecordEntity)
    @Update suspend fun updateRecord(record: WorkRecordEntity): Int
    @Query("UPDATE work_records SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun deleteRecord(id: String, now: Long): Int
    @Query("SELECT value FROM preferences WHERE `key` = :key") fun preference(key: String): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun preference(value: PreferenceEntity)
}

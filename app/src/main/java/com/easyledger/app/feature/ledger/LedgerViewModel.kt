package com.easyledger.app.feature.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easyledger.app.LedgerApplication
import com.easyledger.app.domain.model.BillingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LedgerApplication).repository
    val projectSearch = MutableStateFlow("")
    val recordSearch = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val allProjects = repository.allProjects.catch { notices.emit("项目读取失败，请重试；原数据未被清除") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projects = projectSearch.flatMapLatest(repository::projects).catch { notices.emit("项目读取失败，请重试") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records = recordSearch.flatMapLatest(repository::records).catch { notices.emit("记录读取失败，请重试；原数据未被清除") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allRecords = repository.records("").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val onboardingComplete = repository.onboarding.map { it == "true" }
        .catch { notices.emit("引导状态读取失败，请重试") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun write(success: String, onDone: () -> Unit, operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { operation(); notices.emit(success); onDone() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notices.emit(e.message?.takeIf { e is IllegalArgumentException } ?: "保存失败，请重试；输入内容已保留") }
            finally { busy.value = false }
        }
    }
    fun saveProject(id: String?, name: String, note: String, mode: BillingMode, unit: String, price: String, done: () -> Unit) =
        write("项目已保存", done) { repository.saveProject(id, name, note, mode, unit, price) }
    fun deleteProject(id: String, done: () -> Unit) = write("项目已删除，历史工作记录仍保留", done) { repository.deleteProject(id) }
    fun saveRecord(id: String?, projectId: String, date: String, mode: BillingMode, unit: String, quantity: String, price: String, note: String, done: () -> Unit) =
        write("工作记录已保存", done) { repository.saveRecord(id, projectId, date, mode, unit, quantity, price, note) }
    fun deleteRecord(id: String, done: () -> Unit) = write("记录已删除", done) { repository.deleteRecord(id) }
    fun finishOnboarding(done: () -> Unit) = write("可以开始记账了", done) { repository.finishOnboarding() }
}

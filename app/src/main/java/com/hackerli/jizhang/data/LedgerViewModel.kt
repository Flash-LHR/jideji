package com.hackerli.jizhang.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LedgerDatabase(application)
    private val locationProvider = ForegroundLocationProvider(application)
    private val updateManager = UpdateManager(application)
    private var locationJob: Job? = null
    private val tagOrderMutex = Mutex()

    private val _tags = MutableStateFlow<List<QuickTag>>(emptyList())
    val tags = _tags.asStateFlow()

    private val _allTags = MutableStateFlow<List<QuickTag>>(emptyList())
    val allTags = _allTags.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses = _expenses.asStateFlow()

    private val _locationState = MutableStateFlow<LocationState>(LocationState.PermissionRequired)
    val locationState = _locationState.asStateFlow()

    private val _operationInFlight = MutableStateFlow(false)
    val operationInFlight = _operationInFlight.asStateFlow()

    val updateState = updateManager.state

    private val eventChannel = Channel<LedgerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        refreshAll(cleanOrphanPhotos = true)
        checkForUpdate()
    }

    fun refreshLocation() {
        if (locationJob?.isActive == true) return
        _locationState.value = LocationState.Loading
        locationJob = viewModelScope.launch {
            val result = locationProvider.locate()
            _locationState.value = result.fold(
                onSuccess = { LocationState.Ready(it) },
                onFailure = { LocationState.Error(it.message ?: "定位失败，请重试") },
            )
        }
    }

    fun requireLocationPermission() {
        locationJob?.cancel()
        _locationState.value = LocationState.PermissionRequired
    }

    fun checkForUpdate() {
        viewModelScope.launch { updateManager.check() }
    }

    fun updateInstallIntent() = updateManager.requestInstall()

    fun record(
        amountYuan: Long,
        tag: QuickTag,
        note: String,
        photoPaths: List<String>,
        location: LocationSnapshot,
    ) {
        if (amountYuan !in 1L..MAX_AMOUNT_YUAN) {
            sendError("请输入有效金额")
            return
        }
        launchMutation(LedgerEvent.ExpenseSaved, "保存失败，请重试") {
            withContext(Dispatchers.IO) {
                requirePhotosExist(photoPaths)
                database.insertExpense(
                    amountCents = amountYuan * 100L,
                    tagId = tag.id,
                    occurredAt = System.currentTimeMillis(),
                    note = note.trim(),
                    location = location,
                    photoPaths = photoPaths,
                )
            }
            refreshExpenses()
        }
    }

    fun updateExpense(
        expense: Expense,
        amountYuan: Long,
        tag: QuickTag,
        note: String,
        photoPaths: List<String>,
    ) {
        if (amountYuan !in 1L..MAX_AMOUNT_YUAN) {
            sendError("请输入有效金额")
            return
        }
        if (amountYuan * 100L < expense.refundedAmountCents) {
            sendError("原消费金额不能低于累计退款")
            return
        }
        val removedPaths = expense.photos.map { it.path } - photoPaths.toSet()
        launchMutation(LedgerEvent.ExpenseUpdated, "修改失败，请重试") {
            withContext(Dispatchers.IO) {
                requirePhotosExist(photoPaths)
                database.updateExpense(
                    expenseId = expense.id,
                    amountCents = amountYuan * 100L,
                    tagId = tag.id,
                    note = note.trim(),
                    photoPaths = photoPaths,
                )
                PhotoStorage.deleteAll(removedPaths)
            }
            refreshExpenses()
        }
    }

    fun deleteExpense(id: Long) {
        val photoPaths = _expenses.value.firstOrNull { it.id == id }?.photos?.map { it.path }.orEmpty()
        launchMutation(LedgerEvent.ExpenseDeleted, "删除失败，请重试") {
            withContext(Dispatchers.IO) {
                database.deleteExpense(id)
                PhotoStorage.deleteAll(photoPaths)
            }
            refreshExpenses()
        }
    }

    fun addRefund(expenseId: Long, amountYuan: Long) {
        val expense = _expenses.value.firstOrNull { it.id == expenseId }
        if (expense == null) {
            sendError("账单不存在")
            return
        }
        val remainingCents = expense.amountCents - expense.refundedAmountCents
        val amountCents = amountYuan * 100L
        if (amountYuan !in 1L..MAX_AMOUNT_YUAN || amountCents > remainingCents) {
            sendError("退款总额不能超过原消费金额")
            return
        }
        launchMutation(LedgerEvent.RefundSaved, "退款保存失败，请重试") {
            withContext(Dispatchers.IO) {
                database.insertRefund(expenseId, amountCents, System.currentTimeMillis())
            }
            refreshExpenses()
        }
    }

    fun refundRemaining(expenseId: Long) {
        val expense = _expenses.value.firstOrNull { it.id == expenseId }
        if (expense == null) {
            sendError("账单不存在")
            return
        }
        addRefund(expenseId, (expense.amountCents - expense.refundedAmountCents) / 100L)
    }

    fun deleteRefund(refundId: Long) {
        launchMutation(LedgerEvent.RefundDeleted, "删除退款失败，请重试") {
            withContext(Dispatchers.IO) { database.deleteRefund(refundId) }
            refreshExpenses()
        }
    }

    fun addTag(name: String, emoji: String, colorArgb: Int): Boolean {
        val cleanName = name.trim()
        if (!validateTagName(cleanName)) return false
        val cleanEmoji = emoji.trim().ifEmpty { "●" }
        val sortOrder = (_allTags.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.insertTag(cleanName, cleanEmoji, colorArgb, sortOrder)
                }
                loadAll()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                eventChannel.send(LedgerEvent.Error("新建标签失败"))
            }
        }
        return true
    }

    fun updateTag(tag: QuickTag): Boolean {
        val cleanName = tag.name.trim()
        if (!validateTagName(cleanName, tag.id)) return false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { database.updateTag(tag.copy(name = cleanName)) }
                loadAll()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                eventChannel.send(LedgerEvent.Error("修改标签失败"))
            }
        }
        return true
    }

    fun setTagArchived(tag: QuickTag, archived: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { database.setTagArchived(tag.id, archived) }
                loadAll()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                eventChannel.send(
                    LedgerEvent.Error(error.userMessageOr("修改标签失败")),
                )
            }
        }
    }

    fun moveTag(tag: QuickTag, offset: Int) {
        val reordered = _tags.value.toMutableList()
        val from = reordered.indexOfFirst { it.id == tag.id }
        if (from < 0) return
        val to = (from + offset).coerceIn(0, reordered.lastIndex)
        if (from == to) return
        reordered.add(to, reordered.removeAt(from))
        val normalized = reordered.mapIndexed { index, item -> item.copy(sortOrder = index) }
        _tags.value = normalized
        val byId = normalized.associateBy { it.id }
        _allTags.value = _allTags.value.map { byId[it.id] ?: it }
        viewModelScope.launch {
            try {
                tagOrderMutex.withLock {
                    withContext(Dispatchers.IO) { database.updateTagOrder(normalized) }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                eventChannel.send(LedgerEvent.Error("标签排序失败"))
                loadAll()
            }
        }
    }

    private fun validateTagName(name: String, ownId: Long? = null): Boolean {
        val error = when {
            name.isBlank() -> "标签名称不能为空"
            name.length > 8 -> "标签名称最多 8 个字"
            _allTags.value.any { it.id != ownId && it.name.equals(name, ignoreCase = true) } ->
                "标签名称不能重复"
            else -> null
        }
        if (error != null) sendError(error)
        return error == null
    }

    private fun launchMutation(
        successEvent: LedgerEvent,
        errorMessage: String,
        block: suspend () -> Unit,
    ) {
        if (!_operationInFlight.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                block()
                eventChannel.send(successEvent)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                eventChannel.send(LedgerEvent.Error(error.userMessageOr(errorMessage)))
            } finally {
                _operationInFlight.value = false
            }
        }
    }

    private fun sendError(message: String) {
        viewModelScope.launch { eventChannel.send(LedgerEvent.Error(message)) }
    }

    private fun requirePhotosExist(paths: List<String>) {
        require(paths.all { File(it).let { file -> file.isFile && file.canRead() && file.length() > 0L } }) {
            "有照片文件已丢失，请重新选择"
        }
    }

    private fun refreshAll(cleanOrphanPhotos: Boolean = false) {
        viewModelScope.launch { loadAll(cleanOrphanPhotos) }
    }

    private suspend fun loadAll(cleanOrphanPhotos: Boolean = false) {
        val (allTags, expenses) = withContext(Dispatchers.IO) {
            database.getTags(includeArchived = true) to database.getExpenses()
        }
        if (cleanOrphanPhotos) {
            withContext(Dispatchers.IO) {
                PhotoStorage.cleanupOrphans(
                    getApplication(),
                    expenses.flatMap { expense -> expense.photos.map { it.path } }.toSet(),
                )
            }
        }
        _allTags.value = allTags
        _tags.value = allTags.filterNot { it.isArchived }.sortedBy { it.sortOrder }
        _expenses.value = expenses
    }

    private suspend fun refreshExpenses() {
        _expenses.value = withContext(Dispatchers.IO) { database.getExpenses() }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }

    companion object {
        const val MAX_AMOUNT_YUAN = 999_999L
    }
}

sealed interface LedgerEvent {
    data object ExpenseSaved : LedgerEvent
    data object ExpenseUpdated : LedgerEvent
    data object RefundSaved : LedgerEvent
    data object RefundDeleted : LedgerEvent
    data object ExpenseDeleted : LedgerEvent
    data class Error(val message: String) : LedgerEvent
}

private fun Throwable.userMessageOr(fallback: String): String =
    if (this is IllegalArgumentException) message?.takeIf { it.isNotBlank() } ?: fallback else fallback

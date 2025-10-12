package com.example.safyscooter


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object ViolationsRepository {

    data class Violation(
        val path: String,
        var status: String
    )

    // Внутренний изменяемый список
    private val _violations = MutableLiveData<List<Violation>>(emptyList())
    val violations: LiveData<List<Violation>> = _violations

    private val internalList = mutableListOf<Violation>()

    @Synchronized
    fun addViolation(path: String, status: String) {
        internalList.add(0, Violation(path, status)) // добавлять сверху
        _violations.postValue(internalList.toList())
    }

    @Synchronized
    fun updateStatus(path: String, newStatus: String) {
        val idx = internalList.indexOfFirst { it.path == path }
        if (idx != -1) {
            internalList[idx].status = newStatus
            _violations.postValue(internalList.toList())
        }
    }

    fun getViolations(): List<Violation> = internalList.toList()
}

package com.example.safyscooter.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safyscooter.data.ViolationRepository
import com.example.safyscooter.data.ViolationStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ViolationDetailsUi(
    val title: String,
    val dateTime: String,
    val status: String
)

class ViolationDetailsViewModel(
    private val violationId: Long
) : ViewModel() {

    private val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // статус как Flow из репозитория
    val status: StateFlow<String> =
        ViolationRepository.statusFlow(violationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "статус в разработке")

    // весь UI-стейт
    val ui: StateFlow<ViolationDetailsUi> =
        ViolationRepository.statusFlow(violationId).map { st ->
            val v = ViolationStore.getById(violationId)
            ViolationDetailsUi(
                title = v?.title ?: "нарушение",
                dateTime = v?.timestamp?.let { sdf.format(Date(it)) } ?: "",
                status = st
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            ViolationDetailsUi("нарушение", "", "статус в разработке")
        )

    fun refresh() {
        viewModelScope.launch { ViolationRepository.refreshStatus(violationId) }
    }
}

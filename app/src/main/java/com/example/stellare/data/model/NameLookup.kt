package com.example.stellare.data.model

sealed interface NameLookup {
    data object Loading : NameLookup
    data class Known(val name: String) : NameLookup
    data object Absent : NameLookup
    data object Unavailable : NameLookup

    val isSettled: Boolean
        get() = this is Known || this is Absent
}

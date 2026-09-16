package com.example.stellare.ui.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _filters = MutableStateFlow(FeedFilters())
    val filters: StateFlow<FeedFilters> = _filters.asStateFlow()

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val wszyscyPsycholodzy = combine(
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST),
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT)
    ) { zawodowi, studenci -> zawodowi + studenci }

    val psychologists: StateFlow<List<UserModel>> =
        combine(wszyscyPsycholodzy, _filters) { lista, filtry ->
            lista.filter(filtry::matches).sortedByDescending { it.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patients: StateFlow<List<UserModel>> = combine(
        userRepository.getUsersOfRoleFlow(UserRole.PATIENT),
        _filters
    ) { lista, filtry ->
        lista.filter { user ->
            filtry.query.isEmpty() ||
                "${user.firstName} ${user.lastName}".contains(filtry.query, ignoreCase = true) ||
                user.bio.contains(filtry.query, ignoreCase = true)
        }.sortedByDescending { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptySet())
            else userRepository.getFavoritesForUserFlow(user.id)
                .map { lista -> lista.map { it.psychologistId }.toSet() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoritePsychologists: StateFlow<List<UserModel>> = combine(
        currentUser,
        wszyscyPsycholodzy,
        userRepository.getUsersOfRoleFlow(UserRole.PATIENT),
        favoriteIds
    ) { user, allPsychs, allPatients, favIds ->
        val isPsych = user?.role == UserRole.PSYCHOLOGIST || user?.role == UserRole.PSYCHOLOGY_STUDENT
        val targetList = if (isPsych) allPatients else allPsychs
        targetList.filter { targetUser -> favIds.contains(targetUser.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFilters(filters: FeedFilters) {
        _filters.value = filters
    }

    fun updateQuery(query: String) {
        _filters.value = _filters.value.copy(query = query)
    }

    fun resetFilters() {
        _filters.value = FeedFilters()
    }

    fun toggleFavorite(psychologistId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser() ?: return@launch
                val aktualne = userRepository.getFavoritesForUserFlow(user.id).first()
                    .map { it.psychologistId }.toSet()
                if (aktualne.contains(psychologistId)) {
                    userRepository.deleteFavorite(user.id, psychologistId)
                } else {
                    userRepository.insertFavorite(user.id, psychologistId)
                }
            } catch (e: Exception) {
                Log.e("Favorites", "Nie udalo sie zmienic ulubionych", e)
            }
        }
    }
}

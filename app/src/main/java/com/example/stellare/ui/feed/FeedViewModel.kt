package com.example.stellare.ui.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.StaleData
import com.example.stellare.ui.lastKnownUser
import com.example.stellare.ui.dashboard.ratingBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

internal const val RATINGS_STALE_MESSAGE =
    "Nie udało się odświeżyć ocen. Pokazane oceny mogą być nieaktualne."

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _filters = MutableStateFlow(FeedFilters())
    val filters: StateFlow<FeedFilters> = _filters.asStateFlow()

    private val currentUser = userRepository.getCurrentUserFlow()
        .lastKnownUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun usersOfRole(role: UserRole): Flow<List<UserModel>> = userRepository.isSignedInFlow()
        .flatMapLatest { signedIn ->
            if (signedIn) userRepository.getUsersOfRoleFlow(role) else flowOf(emptyList())
        }

    private val ratingsStale = StaleData(RATINGS_STALE_MESSAGE)
    val ratingsStaleMessage: StateFlow<String?> = ratingsStale.message

    fun retryRatings() {
        ratingsStale.retry()
    }

    private val oceny: StateFlow<Map<String, RatingModel>> = ratingsStale
        .cover("Ratings", "ocen") {
            userRepository.isSignedInFlow().flatMapLatest { signedIn ->
                if (signedIn) userRepository.getRatingsFlow() else flowOf(emptyMap())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val wszyscyPsycholodzy = combine(
        usersOfRole(UserRole.PSYCHOLOGIST),
        usersOfRole(UserRole.PSYCHOLOGY_STUDENT)
    ) { zawodowi, studenci -> zawodowi + studenci }

    val authorRatingBadges: StateFlow<Map<String, String>> = oceny
        .map(::ratingBadges)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val patients: StateFlow<List<UserModel>> = combine(
        usersOfRole(UserRole.PATIENT),
        _filters
    ) { lista, filtry ->
        lista.filter { user ->
            filtry.query.isEmpty() ||
                "${user.firstName} ${user.lastName}".contains(filtry.query, ignoreCase = true) ||
                user.bio.contains(filtry.query, ignoreCase = true)
        }.sortedByDescending { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = userRepository
        .signedInUserIdFlow()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptySet())
            else userRepository.getFavoritesForUserFlow(uid)
                .map { lista -> lista.map { it.psychologistId }.toSet() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoritePsychologists: StateFlow<List<UserModel>> = combine(
        currentUser,
        wszyscyPsycholodzy,
        usersOfRole(UserRole.PATIENT),
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Favorites", "Nie udalo sie zmienic ulubionych", e)
            }
        }
    }
}

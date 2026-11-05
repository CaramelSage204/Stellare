package com.example.stellare.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.CurrentUserReads
import com.example.stellare.ui.UncertainWriteMessages
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.lastKnownUser
import com.example.stellare.ui.retryWithBackoff
import com.example.stellare.ui.psychologistdetail.ProfileLoad
import com.example.stellare.ui.psychologistdetail.ReviewsLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

private const val PROFILE_SAVE_FAILED_MESSAGE = "Nie udało się zapisać profilu. Spróbuj ponownie."
private const val PROFILE_POSTS_SYNC_FAILED_MESSAGE = "Profil został zapisany, ale nie udało się zaktualizować Twoich ogłoszeń. Spróbuj zapisać ponownie."
private const val NOT_SIGNED_IN_MESSAGE = "Brak zalogowanego użytkownika."

private const val REVIEWS_LOAD_FAILED_MESSAGE = "Nie udało się wczytać opinii. Spróbuj ponownie później."
private const val PROFILE_LOAD_FAILED_MESSAGE = "Nie udało się wczytać profilu. Spróbuj ponownie."

/**
 * Zapis do Firestore bez sieci nie kończy się ani sukcesem, ani błędem: SDK przyjmuje go lokalnie
 * i czeka na połączenie (zmierzone na urządzeniu: 120 s napisu "Zapisywanie..." i nic więcej).
 * Ta sama liczba, co jedyny inny limit w projekcie: krok usuwania konta w AccountDeleter.
 */
internal const val WRITE_TIMEOUT_MS = 20_000L

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository,
    private val postRepository: PostRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val profileReads: Flow<CurrentUserRead> = userRepository.getCurrentUserFlow()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val currentUser: StateFlow<UserModel?> = profileReads
        .lastKnownUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Osobny kanał od [currentUser], bo ekran musi powiedzieć o awarii odczytu także wtedy, gdy
     * ostatni znany profil zostaje na ekranie — i odwrotnie: gdy nie ma żadnego profilu do
     * pokazania, to ten komunikat, nie wskaźnik postępu, jest prawdą o stanie tu i teraz.
     */
    val profileReadFailure: StateFlow<String?> = profileReads
        .map(CurrentUserReads::failureMessage)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun retryProfile() {
        userRepository.retryCurrentUser()
    }

    val privateProfile: StateFlow<PrivateProfile?> = userRepository.currentPrivateProfileFlow()
        .retryWithBackoff("Profile", "danych prywatnych")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Totalna, bo wołają ją nieosłonięte `LaunchedEffect` i `produceState` w kompozycji, gdzie nad
     * wyjątkiem nie ma żadnej osłony. [ProfileLoad.Absent] i [ProfileLoad.Failed] są osobne: zwrócenie
     * pustki za nieudany odczyt udawałoby, że takiego psychologa nie ma.
     */
    suspend fun userById(userId: String): ProfileLoad = try {
        userRepository.getUserById(userId)?.let(ProfileLoad::Loaded) ?: ProfileLoad.Absent
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Profile", "Nie udalo sie wczytac profilu $userId", e)
        ProfileLoad.Failed(UserFacingError.message(e, PROFILE_LOAD_FAILED_MESSAGE))
    }

    private val reviewsRetry = MutableStateFlow(0)

    fun retryReviews() {
        reviewsRetry.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reviewsLoadFor(psychologistId: String): Flow<ReviewsLoad> =
        reviewsRetry.flatMapLatest {
            userRepository.whileSignedIn(ReviewsLoad.Loaded(emptyList())) {
                reviewRepository.getReviewsForPsychologistFlow(psychologistId)
                    .map<List<ReviewModel>, ReviewsLoad> { ReviewsLoad.Loaded(it) }
                    .catch { error ->
                        Log.e("Reviews", "Nie udalo sie wczytac opinii", error)
                        emit(ReviewsLoad.Failed(UserFacingError.message(error, REVIEWS_LOAD_FAILED_MESSAGE)))
                    }
            }
        }

    fun ratingFor(psychologistId: String): Flow<RatingModel> =
        userRepository.whileSignedIn(RatingModel()) { userRepository.getRatingFlow(psychologistId) }
            .retryWithBackoff("Ratings", "oceny")

    fun reviewsFor(psychologistId: String): Flow<List<ReviewModel>> =
        reviewsLoadFor(psychologistId)
            .filterIsInstance<ReviewsLoad.Loaded>()
            .map { it.reviews }

    private val _saveState = MutableStateFlow<ProfileSaveState>(ProfileSaveState.Idle)
    val saveState: StateFlow<ProfileSaveState> = _saveState.asStateFlow()

    fun resetSaveState() {
        if (_saveState.value !is ProfileSaveState.InProgress) {
            _saveState.value = ProfileSaveState.Idle
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        bio: String,
        specializations: String,
        pricePerSession: Double,
        customPrices: String = "",
        profileImageUri: String? = null,
        qualifications: String? = null
    ) {
        if (_saveState.value is ProfileSaveState.InProgress) return
        _saveState.value = ProfileSaveState.InProgress
        viewModelScope.launch {
            val current = try {
                userRepository.getCurrentUser()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Profile", "Nie udalo sie odczytac profilu przed zapisem", e)
                _saveState.value = ProfileSaveState.Error(UserFacingError.message(e, PROFILE_SAVE_FAILED_MESSAGE))
                return@launch
            }
            if (current == null) {
                _saveState.value = ProfileSaveState.Error(NOT_SIGNED_IN_MESSAGE)
                return@launch
            }
            val updated = current.copy(
                firstName = firstName,
                lastName = lastName,
                age = age,
                gender = gender,
                bio = bio,
                specializations = specializations,
                pricePerSession = pricePerSession,
                customPrices = customPrices,
                profileImageUri = profileImageUri ?: current.profileImageUri,
                qualifications = qualifications ?: current.qualifications
            )
            try {
                val landed = withTimeoutOrNull(WRITE_TIMEOUT_MS) { userRepository.updateUser(updated) }
                if (landed == null) {
                    Log.e("Profile", "Zapis profilu nie odpowiedzial w $WRITE_TIMEOUT_MS ms")
                    _saveState.value = ProfileSaveState.Uncertain(UncertainWriteMessages.PROFILE_SAVE)
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Profile", "Nie udalo sie zapisac profilu", e)
                _saveState.value = ProfileSaveState.Error(UserFacingError.message(e, PROFILE_SAVE_FAILED_MESSAGE))
                return@launch
            }
            try {
                val synced = withTimeoutOrNull(WRITE_TIMEOUT_MS) { postRepository.syncOwnPostsWithAuthor(updated) }
                if (synced == null) {
                    Log.e("Profile", "Synchronizacja ogloszen nie odpowiedziala w $WRITE_TIMEOUT_MS ms")
                    _saveState.value = ProfileSaveState.Uncertain(UncertainWriteMessages.PROFILE_POSTS_SYNC)
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Profile", "Nie udalo sie zsynchronizowac ogloszen po zapisie profilu", e)
                _saveState.value = ProfileSaveState.Error(PROFILE_POSTS_SYNC_FAILED_MESSAGE)
                return@launch
            }
            _saveState.value = ProfileSaveState.Success
        }
    }

    suspend fun hasCompletedSessionWith(psychologistId: String): Boolean = try {
        val current = userRepository.getCurrentUser()
        current != null && reviewRepository.hasCompletedSession(psychologistId, current.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Reviews", "Nie udalo sie sprawdzic odbytej wizyty", e)
        false
    }

    private val _reviewState = MutableStateFlow<ReviewSubmitState>(ReviewSubmitState.Idle)
    val reviewState: StateFlow<ReviewSubmitState> = _reviewState.asStateFlow()

    fun resetReviewState() {
        _reviewState.value = ReviewSubmitState.Idle
    }

    fun addReview(psychologistId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            _reviewState.value = ReviewSubmitState.InProgress
            try {
                val current = userRepository.getCurrentUser() ?: run {
                    _reviewState.value = ReviewSubmitState.Error("Brak zalogowanego użytkownika.")
                    return@launch
                }
                userRepository.getUserById(psychologistId) ?: run {
                    _reviewState.value = ReviewSubmitState.Error("Nie znaleziono psychologa.")
                    return@launch
                }
                val reviewId = "${psychologistId}_${current.id}"
                val review = ReviewModel(
                    reviewId = reviewId,
                    psychologistId = psychologistId,
                    reviewerId = current.id,
                    reviewerName = "${current.firstName} ${current.lastName}",
                    rating = rating,
                    comment = comment,
                    timestamp = clock()
                )
                reviewRepository.saveReviewWithRating(review, psychologistId)
                _reviewState.value = ReviewSubmitState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Reviews", "Nie udalo sie dodac recenzji", e)
                _reviewState.value = ReviewSubmitState.Error(
                    UserFacingError.message(
                        e,
                        "Nie udało się dodać opinii. Opinię można wystawić tylko raz, po odbytej wizycie.",
                        contention = UserFacingError.RATING_TRY_AGAIN
                    )
                )
            }
        }
    }
}

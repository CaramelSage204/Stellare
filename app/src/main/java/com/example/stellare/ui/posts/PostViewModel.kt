package com.example.stellare.ui.posts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.AuthorSnapshot
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.StaleData
import com.example.stellare.ui.lastKnownUser
import com.example.stellare.ui.UserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_TITLE_LENGTH = 120
private const val MAX_BODY_LENGTH = 2000
private const val MAX_TAGS_COUNT = 5

internal const val POSTS_STALE_MESSAGE =
    "Nie udało się odświeżyć ogłoszeń. Pokazana lista może być nieaktualna."
internal const val PUBLISH_FAILED_MESSAGE =
    "Nie udało się opublikować ogłoszenia. Spróbuj ponownie."
internal const val DEACTIVATE_FAILED_MESSAGE =
    "Nie udało się ukryć ogłoszenia. Nadal jest widoczne dla innych. Spróbuj ponownie."

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModel(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .lastKnownUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            currentUser.filterNotNull().collect { author ->
                // Osłona jest WEWNĄTRZ collect, bo decyduje o ponowieniu: osłona wokół
                // całego collect też powstrzymałaby awarię, ale zakończyłaby przepływ na
                // pierwszej odmowie i do końca procesu nie uzgodniłaby już żadnej kolejnej
                // zmiany profilu. Sama osłona (gdziekolwiek) jest konieczna, bo tego
                // wyjątku nie ma tu kto przechwycić — zadanie 11 (R1) zmierzyło na
                // urządzeniu FATAL EXCEPTION przy każdym starcie i konto nie do odratowania.
                try {
                    postRepository.syncOwnPostsWithAuthor(author)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Post", "Nie udalo sie zsynchronizowac wlasnych ogloszen z profilem", e)
                }
            }
        }
    }

    private val postsStale = StaleData(POSTS_STALE_MESSAGE)
    val postsStaleMessage: StateFlow<String?> = postsStale.message

    fun retryPosts() {
        postsStale.retry()
    }

    val seekingPosts: StateFlow<List<PostModel>> = postsStale
        .cover("Post", "ogloszen pacjentow") {
            userRepository.whileSignedIn(emptyList()) {
                currentUser.flatMapLatest { user ->
                    if (user != null && typeForRole(user.role) == PostType.OFFERING_THERAPY) {
                        postRepository.activePosts(PostType.SEEKING_PSYCHOLOGIST)
                    } else {
                        flowOf(emptyList())
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offeringPosts: StateFlow<List<PostModel>> = postsStale
        .cover("Post", "ogloszen psychologow") {
            userRepository.isSignedInFlow().flatMapLatest { signedIn ->
                if (signedIn) postRepository.activePosts(PostType.OFFERING_THERAPY) else flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myPosts: StateFlow<List<PostModel>> = postsStale
        .cover("Post", "wlasnych ogloszen") {
            userRepository.signedInUserIdFlow().flatMapLatest { uid ->
                if (uid != null) postRepository.postsByAuthor(uid) else flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()

    fun clearPublishError() {
        _publishError.value = null
    }

    suspend fun publish(title: String, body: String, tags: List<String>): Boolean {
        _publishError.value = null
        val user = when (val read = userRepository.readCurrentUser()) {
            is CurrentUserRead.Loaded -> read.user
            CurrentUserRead.SignedOut, CurrentUserRead.Absent -> return false
            is CurrentUserRead.Unavailable -> {
                Log.e("Post", "Nie udalo sie odczytac profilu przed publikacja", read.error)
                _publishError.value = UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
                return false
            }
        }

        val validationError = validate(title, body, tags)
        if (validationError != null) {
            _publishError.value = validationError
            return false
        }

        val type = typeForRole(user.role)
        val priceCoins = if (type == PostType.OFFERING_THERAPY) {
            user.pricePerSession.toInt()
        } else {
            0
        }

        val post = PostModel(
            id = postRepository.newPostId(),
            authorId = user.id,
            type = type,
            title = title,
            body = body,
            tags = tags,
            priceCoins = priceCoins,
            createdAt = clock(),
            isActive = true,
            authorSnapshot = AuthorSnapshot(
                firstName = user.firstName,
                lastName = user.lastName,
                profileImageUri = user.profileImageUri,
                age = user.age,
                gender = user.gender,
                isVerified = user.isVerified
            )
        )

        return try {
            postRepository.savePost(post)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Post", "Nie udalo sie opublikowac ogloszenia", e)
            _publishError.value = UserFacingError.message(e, PUBLISH_FAILED_MESSAGE)
            false
        }
    }

    private val _deactivateError = MutableStateFlow<String?>(null)
    val deactivateError: StateFlow<String?> = _deactivateError.asStateFlow()

    fun clearDeactivateError() {
        _deactivateError.value = null
    }

    fun deactivate(postId: String) {
        _deactivateError.value = null
        viewModelScope.launch {
            try {
                postRepository.deactivatePost(postId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Post", "Nie udalo sie dezaktywowac ogloszenia", e)
                _deactivateError.value = UserFacingError.message(e, DEACTIVATE_FAILED_MESSAGE)
            }
        }
    }

    private fun typeForRole(role: UserRole): PostType = when (role) {
        UserRole.PSYCHOLOGIST, UserRole.PSYCHOLOGY_STUDENT -> PostType.OFFERING_THERAPY
        UserRole.PATIENT -> PostType.SEEKING_PSYCHOLOGIST
    }

    private fun validate(title: String, body: String, tags: List<String>): String? = when {
        title.isBlank() -> "Tytuł nie może być pusty."
        title.length > MAX_TITLE_LENGTH -> "Tytuł może mieć najwyżej 120 znaków."
        body.length > MAX_BODY_LENGTH -> "Treść może mieć najwyżej 2000 znaków."
        tags.size > MAX_TAGS_COUNT -> "Można wybrać najwyżej 5 tagów."
        else -> null
    }
}

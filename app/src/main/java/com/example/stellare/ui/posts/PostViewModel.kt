package com.example.stellare.ui.posts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.AuthorSnapshot
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModel(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            currentUser.filterNotNull().collect { author ->
                postRepository.syncOwnPostsWithAuthor(author)
            }
        }
    }

    val seekingPosts: StateFlow<List<PostModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null && typeForRole(user.role) == PostType.OFFERING_THERAPY) {
                postRepository.activePosts(PostType.SEEKING_PSYCHOLOGIST)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offeringPosts: StateFlow<List<PostModel>> = postRepository
        .activePosts(PostType.OFFERING_THERAPY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myPosts: StateFlow<List<PostModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) postRepository.postsByAuthor(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()

    fun clearPublishError() {
        _publishError.value = null
    }

    suspend fun publish(title: String, body: String, tags: List<String>): Boolean {
        _publishError.value = null
        val user = userRepository.getCurrentUser() ?: return false

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
            createdAt = System.currentTimeMillis(),
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
        } catch (e: Exception) {
            Log.e("Post", "Nie udalo sie opublikowac ogloszenia", e)
            false
        }
    }

    fun deactivate(postId: String) {
        viewModelScope.launch {
            try {
                postRepository.deactivatePost(postId)
            } catch (e: Exception) {
                Log.e("Post", "Nie udalo sie dezaktywowac ogloszenia", e)
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

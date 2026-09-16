package com.example.stellare.fakes

import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.remote.PostDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Atrapa źródła ogłoszeń licząca wywołania [savePost], [getPostsByAuthor] i [getActivePosts].
 * [FakePostDataSource][com.example.stellare.fakes.FakePostDataSource] nie liczy niczego,
 * więc nie da się nią odróżnić "zapisano tę samą wartość" od "nie zapisano wcale"
 * (ekonomia zapisów) ani "nie zapytano wcale" od "zapytano i nic nie znaleziono"
 * (uzgodnienie nie ma ruszać, dopóki nikt nie jest zalogowany; seekingPosts nie ma
 * pytać o ogłoszenia poszukujące, gdy zalogowany jest pacjent).
 */
class RecordingPostDataSource(initial: List<PostModel> = emptyList()) : PostDataSource {
    val posts = MutableStateFlow(initial)
    var saveCount = 0
        private set
    var getPostsByAuthorCallCount = 0
        private set
    private val getActivePostsCallCountsByType = mutableMapOf<String, Int>()
    val getActivePostsCallCount: Int
        get() = getActivePostsCallCountsByType.values.sum()
    private var licznik = 0

    fun getActivePostsCallCount(type: PostType): Int =
        getActivePostsCallCountsByType[type.name] ?: 0

    override fun getActivePosts(type: String): Flow<List<PostModel>> {
        getActivePostsCallCountsByType[type] = (getActivePostsCallCountsByType[type] ?: 0) + 1
        return posts.map { list -> list.filter { it.isActive && it.type.name == type } }
    }

    override fun getPostsByAuthor(authorId: String): Flow<List<PostModel>> {
        getPostsByAuthorCallCount++
        return posts.map { list -> list.filter { it.authorId == authorId } }
    }

    override suspend fun savePost(post: PostModel) {
        saveCount++
        posts.value = posts.value.filterNot { it.id == post.id } + post
    }

    override suspend fun deactivatePost(postId: String) {
        posts.value = posts.value.map { if (it.id == postId) it.copy(isActive = false) else it }
    }

    override fun newPostId(): String = "post-${++licznik}"
}

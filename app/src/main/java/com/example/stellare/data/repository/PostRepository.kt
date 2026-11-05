package com.example.stellare.data.repository

import com.example.stellare.data.model.AuthorSnapshot
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.PostDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PostRepository(private val postDataSource: PostDataSource) {

    fun activePosts(type: PostType): Flow<List<PostModel>> =
        postDataSource.getActivePosts(type.name)

    fun postsByAuthor(authorId: String): Flow<List<PostModel>> =
        postDataSource.getPostsByAuthor(authorId)

    suspend fun savePost(post: PostModel) =
        postDataSource.savePost(post)

    suspend fun deactivatePost(postId: String) =
        postDataSource.deactivatePost(postId)

    fun newPostId(): String = postDataSource.newPostId()

    // Reguła Firestore na /posts sprawdza w JEDNYM zapisie zarowno
    // authorSnapshot.isVerified, jak i priceCoins wzgledem aktualnego profilu
    // autora (patrz snapshotPrawdziwy() i cenaZgodnaZProfilem() w firestore.rules).
    // Przepisanie samego snapshotu bez ceny zostaloby odrzucone, gdy cena tez
    // sie rozjechala - dlatego oba pola sa uzgadniane w jednym savePost().
    // Zapisujemy tylko ogloszenia, ktore faktycznie odbiegaja od profilu.
    suspend fun syncOwnPostsWithAuthor(author: UserModel) {
        postDataSource.getPostsByAuthor(author.id).first().forEach { post ->
            reconciledWithAuthor(post, author)?.let { postDataSource.savePost(it) }
        }
    }

    private fun reconciledWithAuthor(post: PostModel, author: UserModel): PostModel? {
        val snapshot = AuthorSnapshot(
            firstName = author.firstName,
            lastName = author.lastName,
            profileImageUri = author.profileImageUri,
            age = author.age,
            gender = author.gender,
            isVerified = author.isVerified
        )
        val priceCoins = if (post.type == PostType.OFFERING_THERAPY) {
            author.pricePerSession.toInt()
        } else {
            0
        }
        return if (post.authorSnapshot == snapshot && post.priceCoins == priceCoins) {
            null
        } else {
            post.copy(authorSnapshot = snapshot, priceCoins = priceCoins)
        }
    }
}

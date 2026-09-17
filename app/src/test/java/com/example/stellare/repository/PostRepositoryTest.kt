package com.example.stellare.repository

import com.example.stellare.data.model.AuthorSnapshot
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.fakes.RecordingPostDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRepositoryTest {

    private fun autorZgodnyZPostem(post: PostModel, role: UserRole = UserRole.PSYCHOLOGIST) = UserModel(
        id = post.authorId,
        role = role,
        firstName = post.authorSnapshot.firstName,
        lastName = post.authorSnapshot.lastName,
        profileImageUri = post.authorSnapshot.profileImageUri,
        age = post.authorSnapshot.age,
        gender = post.authorSnapshot.gender,
        isVerified = post.authorSnapshot.isVerified,
        pricePerSession = post.priceCoins.toDouble()
    )

    @Test
    fun `syncOwnPostsWithAuthor przepisuje snapshot i cene w jednym zapisie gdy oba sie rozjechaly`() = runTest {
        val stary = PostModel(
            id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(firstName = "Jan", isVerified = false), priceCoins = 100
        )
        val dataSource = RecordingPostDataSource(listOf(stary))
        val repo = PostRepository(dataSource)
        val autor = UserModel(
            id = "psych1", role = UserRole.PSYCHOLOGIST,
            firstName = "Jan", isVerified = true, pricePerSession = 200.0
        )

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(1, dataSource.saveCount)
        val zapisany = dataSource.posts.value.single()
        assertTrue(zapisany.authorSnapshot.isVerified)
        assertEquals(200, zapisany.priceCoins)
    }

    @Test
    fun `syncOwnPostsWithAuthor pomija ogloszenie juz zgodne z profilem`() = runTest {
        val zgodny = PostModel(
            id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(firstName = "Jan", isVerified = true), priceCoins = 150
        )
        val dataSource = RecordingPostDataSource(listOf(zgodny))
        val repo = PostRepository(dataSource)
        val autor = autorZgodnyZPostem(zgodny).copy(pricePerSession = 150.0)

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(0, dataSource.saveCount)
    }

    @Test
    fun `syncOwnPostsWithAuthor przepisuje sama cene gdy snapshot juz jest zgodny`() = runTest {
        val post = PostModel(
            id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(firstName = "Jan", isVerified = true), priceCoins = 150
        )
        val dataSource = RecordingPostDataSource(listOf(post))
        val repo = PostRepository(dataSource)
        val autor = autorZgodnyZPostem(post).copy(pricePerSession = 300.0)

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(1, dataSource.saveCount)
        val zapisany = dataSource.posts.value.single()
        assertEquals(300, zapisany.priceCoins)
        assertTrue(zapisany.authorSnapshot.isVerified)
    }

    @Test
    fun `syncOwnPostsWithAuthor przepisuje sam snapshot i mimo to zapisuje aktualna cene`() = runTest {
        val post = PostModel(
            id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(firstName = "Jan", isVerified = false), priceCoins = 150
        )
        val dataSource = RecordingPostDataSource(listOf(post))
        val repo = PostRepository(dataSource)
        val autor = autorZgodnyZPostem(post).copy(isVerified = true)

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(1, dataSource.saveCount)
        val zapisany = dataSource.posts.value.single()
        assertTrue(zapisany.authorSnapshot.isVerified)
        assertEquals(150, zapisany.priceCoins)
    }

    @Test
    fun `syncOwnPostsWithAuthor dla ogloszenia poszukujacego cena zawsze zero`() = runTest {
        val post = PostModel(
            id = "p1", authorId = "pat1", type = PostType.SEEKING_PSYCHOLOGIST,
            authorSnapshot = AuthorSnapshot(firstName = "Jan", isVerified = false), priceCoins = 0
        )
        val dataSource = RecordingPostDataSource(listOf(post))
        val repo = PostRepository(dataSource)
        val autor = UserModel(
            id = "pat1", role = UserRole.PATIENT,
            firstName = "Jan", isVerified = true, pricePerSession = 999.0
        )

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(1, dataSource.saveCount)
        val zapisany = dataSource.posts.value.single()
        assertEquals(0, zapisany.priceCoins)
        assertTrue(zapisany.authorSnapshot.isVerified)
    }

    @Test
    fun `syncOwnPostsWithAuthor nie rusza ogloszen cudzych autorow`() = runTest {
        val moje = PostModel(
            id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(isVerified = true), priceCoins = 150
        )
        val cudze = PostModel(
            id = "p2", authorId = "psych2", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(isVerified = false), priceCoins = 0
        )
        val dataSource = RecordingPostDataSource(listOf(moje, cudze))
        val repo = PostRepository(dataSource)
        val autor = autorZgodnyZPostem(moje).copy(pricePerSession = 150.0)

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(0, dataSource.saveCount)
        val niezmienione = dataSource.posts.value.single { it.id == "p2" }
        assertFalse(niezmienione.authorSnapshot.isVerified)
        assertEquals(0, niezmienione.priceCoins)
    }

    @Test
    fun `syncOwnPostsWithAuthor aktualizuje tylko rozjezdzajace sie ogloszenia z kilku wlasnych`() = runTest {
        val zgodny = PostModel(
            id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(isVerified = true), priceCoins = 150
        )
        val rozjechany = PostModel(
            id = "p2", authorId = "psych1", type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(isVerified = false), priceCoins = 150
        )
        val dataSource = RecordingPostDataSource(listOf(zgodny, rozjechany))
        val repo = PostRepository(dataSource)
        val autor = autorZgodnyZPostem(zgodny).copy(pricePerSession = 150.0)

        repo.syncOwnPostsWithAuthor(autor)

        assertEquals(1, dataSource.saveCount)
        assertTrue(dataSource.posts.value.single { it.id == "p2" }.authorSnapshot.isVerified)
    }
}

package com.example.stellare.repository

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryRegistrationGateTest {

    @Test
    fun `getCurrentUserFlow nie emituje uzytkownika w trakcie rejestracji i emituje go po jej zakonczeniu`() = runTest {
        val auth = FakeAuthDataSource(null)
        val users = FakeUserDataSource()
        val repo = UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))
        val emitted = mutableListOf<String?>()
        backgroundScope.launch { repo.getCurrentUserFlow().collect { emitted.add((it as? CurrentUserRead.Loaded)?.user?.id) } }
        runCurrent()
        assertEquals(listOf<String?>(null), emitted)

        val insideBlock = CompletableDeferred<Unit>()
        val finishBlock = CompletableDeferred<Unit>()
        val registration = launch {
            repo.whileRegistering {
                auth.userId.value = "u1"
                users.saveUser(UserModel(id = "u1"))
                insideBlock.complete(Unit)
                finishBlock.await()
            }
        }
        insideBlock.await()
        runCurrent()
        assertEquals(listOf<String?>(null), emitted)

        finishBlock.complete(Unit)
        registration.join()
        runCurrent()
        assertEquals(listOf<String?>(null, "u1"), emitted)
    }
}

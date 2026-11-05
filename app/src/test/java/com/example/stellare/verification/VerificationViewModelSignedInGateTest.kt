package com.example.stellare.verification

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.VerificationStatus
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeVerificationDataSource
import com.example.stellare.ui.verification.VerificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelSignedInGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zgloszenie = VerificationRequestModel(uid = "k1", status = VerificationStatus.PENDING)

    private fun zbuduj(signedInAs: String?): VerificationViewModel {
        val users = FakeUserDataSource()
        val source = FakeVerificationDataSource(users).also { it.requests.value = listOf(zgloszenie) }
        val userRepository = UserRepository(FakeAuthDataSource(signedInAs), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))
        return VerificationViewModel(VerificationRepository(source), userRepository)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika dostaje swoje zgloszenie`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.myRequest.collect {} }
        runCurrent()

        assertEquals("k1", vm.myRequest.value?.uid)
    }

    @Test
    fun `niezalogowany nie dostaje swojego zgloszenia`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.myRequest.collect {} }
        runCurrent()

        assertEquals(null, vm.myRequest.value)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika dostaje oczekujace zgloszenia`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.pendingRequests.collect {} }
        runCurrent()

        assertEquals(listOf("k1"), vm.pendingRequests.value.map { it.uid })
    }

    @Test
    fun `niezalogowany nie dostaje oczekujacych zgloszen`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.pendingRequests.collect {} }
        runCurrent()

        assertEquals(emptyList<VerificationRequestModel>(), vm.pendingRequests.value)
    }
}

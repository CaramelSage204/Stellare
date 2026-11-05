package com.example.stellare.resilience

import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.feed.FeedViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Odmowa trwała nie może się udać przy żadnej kolejnej próbie, więc ponawianie jej jest pętlą,
 * która pali baterię i transfer, a użytkownikowi pokazuje wieczne wczytywanie zamiast informacji.
 * Lista błędów trwałych jest jedna — ta z `RetryWithBackoff`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RatingsRetryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Setup(val vm: FeedViewModel, val ratings: FakeRatingDataSource)

    private fun zbuduj(): Setup {
        val users = FakeUserDataSource()
        val ratings = FakeRatingDataSource()
        return Setup(
            FeedViewModel(
                UserRepository(
                    FakeAuthDataSource("k1"), users, FakeFavoriteDataSource(), ratings, privateProfilesOf(users)
                )
            ),
            ratings
        )
    }

    private fun odmowa() =
        FirebaseFirestoreException("odmowa regul", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test
    fun `odmowa trwala konczy ponawianie ocen i daje stan widoczny dla uzytkownika`() = runTest(dispatcher) {
        val s = zbuduj()
        s.ratings.readFailure = odmowa()
        backgroundScope.launch { s.vm.authorRatingBadges.collect {} }
        backgroundScope.launch { s.vm.ratingsStaleMessage.collect {} }
        runCurrent()

        val probyPoPierwszejOdmowie = s.ratings.getRatingsSubscriptionCount
        advanceTimeBy(120_000)
        runCurrent()

        assertEquals(
            "odmowa trwala nie moze sie udac przy kolejnej probie, wiec ponawianie jej jest petla " +
                "palaca baterie i transfer; po dwoch minutach nie moze byc ani jednej proby wiecej",
            probyPoPierwszejOdmowie,
            s.ratings.getRatingsSubscriptionCount
        )
        assertTrue("warunek wstepny: pierwsza proba sie odbyla", probyPoPierwszejOdmowie >= 1)
        assertNotNull(
            "po zakonczeniu ponawiania uzytkownik musi dostac informacje, a nie wieczne wczytywanie",
            s.vm.ratingsStaleMessage.value
        )
        assertEquals(
            "Nie udało się odświeżyć ocen. Pokazane oceny mogą być nieaktualne.",
            s.vm.ratingsStaleMessage.value
        )
    }

    @Test
    fun `awaria przejsciowa jest ponawiana i po niej oceny wracaja`() = runTest(dispatcher) {
        val s = zbuduj()
        s.ratings.failuresBeforeSuccess = 1
        backgroundScope.launch { s.vm.authorRatingBadges.collect {} }
        backgroundScope.launch { s.vm.ratingsStaleMessage.collect {} }
        runCurrent()
        assertNotNull("w trakcie awarii przejsciowej dane nie sa aktualne", s.vm.ratingsStaleMessage.value)

        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(
            "awaria przejsciowa MA byc ponawiana, inaczej jeden blip sieci gasi oceny do konca procesu",
            s.ratings.getRatingsSubscriptionCount >= 2
        )
        assertNull("po udanym ponowieniu komunikat ma zgasnac", s.vm.ratingsStaleMessage.value)
    }

    @Test
    fun `ponowienie na zadanie uzytkownika wczytuje oceny po odmowie trwalej`() = runTest(dispatcher) {
        val s = zbuduj()
        s.ratings.readFailure = odmowa()
        backgroundScope.launch { s.vm.authorRatingBadges.collect {} }
        backgroundScope.launch { s.vm.ratingsStaleMessage.collect {} }
        runCurrent()
        assertNotNull("warunek wstepny: komunikat jest ustawiony", s.vm.ratingsStaleMessage.value)

        s.ratings.readFailure = null
        s.vm.retryRatings()
        runCurrent()

        assertNull(
            "po odmowie trwalej jedyna droga powrotu to zadanie uzytkownika; jesli i ona nie dziala, " +
                "ekran zostaje z nieaktualnymi ocenami na zawsze",
            s.vm.ratingsStaleMessage.value
        )
    }
}

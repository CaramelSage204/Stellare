package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentTiming
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.ui.calendar.DepositState
import com.example.stellare.ui.calendar.ReclaimOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReclaimOfferTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val window = EscrowSettlement.ABANDONED_BOOKING_MILLIS
    private val held = DepositState.Known(EscrowModel("t1", "k1", "p1", 80))

    private fun booked(startsAgo: Long, status: String = "BOOKED", price: Int = 80, patientId: String? = "k1") =
        AppointmentModel(
            appointmentId = "t1", psychologistId = "p1", patientId = patientId,
            status = status, priceCoins = price, startsAtMillis = now - startsAgo
        )

    private fun shown(appointment: AppointmentModel, deposit: DepositState = held, userId: String = "k1"): Boolean =
        ReclaimOffer.candidates(listOf(appointment), userId, now).isNotEmpty() &&
            ReclaimOffer.amount(appointment, deposit, userId, now) != null

    @Test
    fun `14 dni i minute po terminie wejscie sie pokazuje i niesie kwote depozytu`() {
        val wizyta = booked(window + minute)
        assertTrue(shown(wizyta))
        assertEquals(80, ReclaimOffer.amount(wizyta, held, "k1", now))
    }

    @Test
    fun `14 dni minus minuta po terminie wejscie sie nie pokazuje`() {
        val wizyta = booked(window - minute)
        assertTrue("lista kandydatow musi byc pusta", ReclaimOffer.candidates(listOf(wizyta), "k1", now).isEmpty())
        assertNull("operacja odmowilaby, wiec nie wolno jej oferowac", ReclaimOffer.amount(wizyta, held, "k1", now))
    }

    @Test
    fun `dokladnie 14 dni po terminie jeszcze sie nie pokazuje, bo regula wymaga ponad 14 dni`() {
        assertTrue(!shown(booked(window)))
        assertTrue(shown(booked(window + 1)))
    }

    @Test
    fun `bez dokumentu depozytu wejscie sie nie pokazuje, bo operacja odmowilaby`() {
        assertTrue(!shown(booked(window + minute), DepositState.Known(null)))
    }

    @Test
    fun `dopoki depozyt sie wczytuje albo jest nieczytelny wejscie sie nie pokazuje`() {
        assertTrue(!shown(booked(window + minute), DepositState.Loading))
        assertTrue(!shown(booked(window + minute), DepositState.Unavailable))
    }

    @Test
    fun `depozyt juz rozliczony - po odzyskaniu wejscie znika`() {
        val rozliczony = DepositState.Known(EscrowModel("t1", "k1", "p1", 80, EscrowModel.REFUNDED))
        assertTrue(!shown(booked(window + minute), rozliczony))
    }

    @Test
    fun `wizyta odbyta, odwolana albo darmowa nie ma wejscia`() {
        assertTrue(!shown(booked(window + minute, status = "COMPLETED")))
        assertTrue(!shown(booked(window + minute, status = "CANCELLED")))
        assertTrue(ReclaimOffer.candidates(listOf(booked(window + minute, price = 0)), "k1", now).isEmpty())
    }

    @Test
    fun `cudza wizyta i psycholog nie maja wejscia`() {
        assertTrue(ReclaimOffer.candidates(listOf(booked(window + minute)), "inny", now).isEmpty())
        assertTrue(ReclaimOffer.candidates(listOf(booked(window + minute)), null, now).isEmpty())
        assertNull(ReclaimOffer.amount(booked(window + minute), held, "p1", now))
    }

    @Test
    fun `zegar zna chwile otwarcia okna - wejscie pojawia sie w pierwszej milisekundzie po oknie`() {
        val wizyta = booked(window - 5 * minute)
        val otwarcie = AppointmentTiming.nextReclaimOpening(listOf(wizyta), now)
        assertEquals(wizyta.startsAtMillis + window + 1, otwarcie)
        assertEquals(otwarcie, AppointmentTiming.nextChange(listOf(wizyta), now))
        assertTrue(ReclaimOffer.amount(wizyta, held, "k1", otwarcie!!) != null)
        assertNull(ReclaimOffer.amount(wizyta, held, "k1", otwarcie - 1))
    }

    @Test
    fun `po otwarciu okna zegar nie planuje juz kolejnego otwarcia`() {
        assertNull(AppointmentTiming.nextReclaimOpening(listOf(booked(window + minute)), now))
        assertNull(AppointmentTiming.nextReclaimOpening(listOf(booked(window - minute, price = 0)), now))
        assertNull(AppointmentTiming.nextReclaimOpening(listOf(booked(window - minute, status = "COMPLETED")), now))
    }

    @Test
    fun `zegar wybiera wczesniejsza z dwoch granic`() {
        val przyszla = booked(-3 * minute)
        val wygasajaca = booked(window - 10 * minute).copy(appointmentId = "t2")
        assertEquals(now + 3 * minute, AppointmentTiming.nextChange(listOf(przyszla, wygasajaca), now))
    }
}

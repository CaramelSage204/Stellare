package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.ui.calendar.ConfirmationTarget.Operation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationTargetTest {

    private fun visit(id: String, status: String, price: Int = 50) =
        AppointmentModel(appointmentId = id, status = status, priceCoins = price)

    @Test
    fun `wizyta istnieje na liscie - dialog jest pokazany`() {
        val list = listOf(visit("a", "BOOKED"), visit("b", "BOOKED"))
        val found = ConfirmationTarget.resolve("a", list, Operation.CANCEL)
        assertNotNull("wizyta a jest na liście i jest zarezerwowana, dialog odwołania ma się pokazać", found)
        assertEquals("a", found?.appointmentId)
    }

    @Test
    fun `brak identyfikatora - brak dialogu`() {
        val list = listOf(visit("a", "BOOKED"))
        assertNull("bez wybranego identyfikatora nie ma dialogu", ConfirmationTarget.resolve(null, list, Operation.CANCEL))
    }

    @Test
    fun `wizyta zniknela z listy - brak dialogu`() {
        val before = listOf(visit("a", "BOOKED"))
        val after = emptyList<AppointmentModel>()
        assertNotNull(ConfirmationTarget.resolve("a", before, Operation.CANCEL))
        assertNull("wizyty a nie ma już na liście, dialog ma zniknąć", ConfirmationTarget.resolve("a", after, Operation.CANCEL))
    }

    @Test
    fun `wizyta zmienila status - dialog widzi nowy status a nie stary`() {
        val before = listOf(visit("a", "BOOKED"))
        val after = listOf(visit("a", "COMPLETED"))
        assertEquals("BOOKED", ConfirmationTarget.current("a", before)?.status)
        val status = ConfirmationTarget.current("a", after)?.status
        assertEquals("po zmianie na liście ma być COMPLETED, a jest $status (stara migawka)", "COMPLETED", status)
    }

    @Test
    fun `dialog widzi zmiane ceny z aktualnej listy`() {
        val after = listOf(visit("a", "BOOKED", price = 80))
        val price = ConfirmationTarget.resolve("a", after, Operation.CANCEL)?.priceCoins
        assertEquals("cena na liście to 80, dialog pokazuje $price", 80, price)
    }

    @Test
    fun `odwolanie zakonczonej wizyty - brak dialogu bo operacja nie ma sensu`() {
        val list = listOf(visit("a", "COMPLETED"))
        assertNull("zakończonej wizyty nie wolno odwołać, dialog ma zniknąć", ConfirmationTarget.resolve("a", list, Operation.CANCEL))
        assertNull("zakończonej wizyty nie wolno zakończyć ponownie", ConfirmationTarget.resolve("a", list, Operation.COMPLETE))
    }

    @Test
    fun `wolny termin zarezerwowany przez kogos innego - brak dialogu usuwania i rezerwacji`() {
        val booked = listOf(visit("a", "BOOKED"))
        assertNull("zajętego terminu nie wolno usunąć", ConfirmationTarget.resolve("a", booked, Operation.DELETE))
        assertNull("zajętego terminu nie wolno zarezerwować", ConfirmationTarget.resolve("a", booked, Operation.BOOK))
    }

    @Test
    fun `wolny termin - dialog usuwania i rezerwacji sa pokazane`() {
        val free = listOf(visit("a", "FREE"))
        assertNotNull(ConfirmationTarget.resolve("a", free, Operation.DELETE))
        assertNotNull(ConfirmationTarget.resolve("a", free, Operation.BOOK))
    }
}

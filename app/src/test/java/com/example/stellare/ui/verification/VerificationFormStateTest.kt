package com.example.stellare.ui.verification

import com.example.stellare.data.model.VerificationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationFormStateTest {

    @Test
    fun `status jeszcze sie wczytuje - tryb ponownego wyslania zostaje`() {
        assertFalse(
            "brak statusu to zgłoszenie wczytujące się po odtworzeniu — nie wolno kasować trybu ponownego wysłania, bo formularz z wybranym zdjęciem zniknie",
            shouldClearResubmitting(null)
        )
    }

    @Test
    fun `zgloszenie odrzucone - tryb ponownego wyslania zostaje`() {
        assertFalse(
            "odrzucone zgłoszenie to powód, dla którego użytkownik wypełnia formularz ponownie",
            shouldClearResubmitting(VerificationStatus.REJECTED)
        )
    }

    @Test
    fun `zgloszenie oczekujace - tryb ponownego wyslania wygasa`() {
        assertTrue(
            "po wysłaniu zgłoszenie jest oczekujące i tryb ponownego wysłania ma się skończyć",
            shouldClearResubmitting(VerificationStatus.PENDING)
        )
    }

    @Test
    fun `zgloszenie zatwierdzone - tryb ponownego wyslania wygasa`() {
        assertTrue(
            "zatwierdzone zgłoszenie kończy tryb ponownego wysłania",
            shouldClearResubmitting(VerificationStatus.APPROVED)
        )
    }
}

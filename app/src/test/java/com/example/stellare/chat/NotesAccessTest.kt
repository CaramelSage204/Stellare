package com.example.stellare.chat

import com.example.stellare.data.model.ChatModel
import com.example.stellare.ui.chat.canSeeNotesTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesAccessTest {

    private val czat = ChatModel(chatId = "c1", psychologistId = "psy", patientId = "pac")

    @Test
    fun `psycholog prowadzacy czat widzi zakladke notatek`() {
        assertTrue(canSeeNotesTab(czat, "psy"))
    }

    @Test
    fun `pacjent nie widzi zakladki notatek`() {
        assertFalse(canSeeNotesTab(czat, "pac"))
    }

    @Test
    fun `obcy uzytkownik nie widzi zakladki notatek`() {
        assertFalse(canSeeNotesTab(czat, "ktos"))
    }

    @Test
    fun `bez czatu albo bez uzytkownika zakladka jest ukryta`() {
        assertFalse(canSeeNotesTab(null, "psy"))
        assertFalse(canSeeNotesTab(czat, null))
        assertFalse(canSeeNotesTab(czat, ""))
    }
}

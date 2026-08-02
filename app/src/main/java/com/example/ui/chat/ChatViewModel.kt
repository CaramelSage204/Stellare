package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.UserEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.example.ui.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val chatRepository = ChatRepository(database.chatDao(), database.messageDao(), database.noteDao())
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeChats: StateFlow<List<ChatEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                chatRepository.getChatsForUserFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatParticipants = MutableStateFlow<Map<Int, UserEntity>>(emptyMap())
    val chatParticipants = _chatParticipants.asStateFlow()

    init {
        viewModelScope.launch {
            loadChatParticipants()
        }
    }

    private suspend fun loadChatParticipants() {
        userRepository.getUsersOfRoleFlow("PSYCHOLOGIST").combine(userRepository.getUsersOfRoleFlow("PATIENT")) { psychs, patients ->
            psychs + patients
        }.collect { allUsers ->
            _chatParticipants.value = allUsers.associateBy { it.id }
        }
    }

    fun getMessagesForChat(chatId: Int): Flow<List<MessageEntity>> = chatRepository.getMessagesForChatFlow(chatId)

    fun getNotesForChat(chatId: Int): Flow<List<NoteEntity>> = chatRepository.getNotesForChatFlow(chatId)

    fun sendChatMessage(chatId: Int, text: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val msg = MessageEntity(chatId = chatId, senderId = user.id, text = text)
            chatRepository.insertMessage(msg)

            val chatList = activeChats.value
            val chat = chatList.find { it.chatId == chatId }
            if (chat != null) {
                chatRepository.updateChat(chat.copy(lastMessage = text, lastMessageTime = System.currentTimeMillis()))
            }
        }
    }

    fun saveClientNote(chatId: Int, title: String, content: String) {
        viewModelScope.launch {
            val note = NoteEntity(chatId = chatId, title = title, content = content)
            chatRepository.insertNote(note)
        }
    }

    fun deleteClientNote(noteId: Int) {
        viewModelScope.launch {
            chatRepository.deleteNote(noteId)
        }
    }

    fun startChatWith(otherUserId: Int, onNavigate: (Screen) -> Unit) {
        viewModelScope.launch {
            val curr = currentUser.value ?: return@launch
            val isCurrentPsych = curr.role == "PSYCHOLOGIST"
            val psychId = if (isCurrentPsych) curr.id else otherUserId
            val patientId = if (isCurrentPsych) otherUserId else curr.id

            val existing = chatRepository.getChatByParticipants(psychId, patientId)
            if (existing != null) {
                onNavigate(Screen.ChatRoom(existing.chatId))
            } else {
                val newChat = ChatEntity(
                    psychologistId = psychId,
                    patientId = patientId,
                    lastMessage = "Rozpoczęto nowy czat.",
                    lastMessageTime = System.currentTimeMillis()
                )
                val newId = chatRepository.createChat(newChat)
                onNavigate(Screen.ChatRoom(newId.toInt()))
            }
        }
    }
}
